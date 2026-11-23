// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.RuntimeShaderRenderEffectException
import dev.chrisbanes.haze.asBrush
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import org.robolectric.annotation.Config

/**
 * Android Blur recovers when a runtime shader cannot be built: the optional combined noise/tint
 * pass falls back to the separate graph, progressive and custom-brush draws redraw through the
 * runtime-shader-free routes, and a failure inside a saved canvas layer leaves the canvas balanced.
 */
// A small window keeps Robolectric's CPU rasterization of every capture cheap.
@Config(qualifiers = "+w160dp-h320dp")
class BlurRuntimeShaderFailureAndroidHostTest : ScreenshotTest() {
  private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

  @Test
  @Config(sdk = [35])
  fun combinedNoiseTint_injectedFailure_usesSeparateNoiseAndTint() = runScreenshotTest {
    val params = RenderEffectParams(
      blurRadius = 8.dp,
      noiseFactor = 0.5f,
      scale = 1f,
      contentSize = Size(64f, 64f),
      contentOffset = Offset.Zero,
      colorEffects = listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.3f))),
      blurTileMode = TileMode.Clamp,
    )
    assertThat(params.combinedNoiseTintColor()).isNotNull()
    var calls = 0
    val candidate = createRenderEffect(context, Density(1f), params) { _, _, _, _ ->
      calls++
      throw RuntimeShaderRenderEffectException(IllegalStateException("injected"))
    }
    val separate = createRenderEffect(context, Density(1f), params) { _, _, _, _ -> null }
    val unstyled = createRenderEffect(
      context,
      Density(1f),
      RenderEffectParams(8.dp, 0f, 1f, Size(64f, 64f), Offset.Zero, blurTileMode = TileMode.Clamp),
    )

    renderEffects(listOf(candidate, separate, unstyled))
    val pixels = captureRootPixels()
    assertThat(calls).isEqualTo(1)
    assertThat(boxDifference(pixels, 0, 1), "fallback matches the separate graph").isLessThanOrEqualTo(0.01f)
    assertThat(boxDifference(pixels, 1, 2), "separate graph styles the blur").isGreaterThan(0.05f)
  }

  @Test
  @Config(sdk = [35])
  fun progressive_injectedFailure_drawsLayeredFallbackWithItsMask() = runScreenshotTest {
    val probe = FailureProbe { progressive, _, _, _ -> runtimeFailure.takeIf { progressive != null } }
    setContent { FailureSplitContent(probe, style(progressive = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f))) }
    val pixels = frame()

    assertThat(probe.delegate.runtimeShaderFallback).isTrue()
    assertThat(probe.delegate.bandSlotCount, "layered route").isGreaterThan(0)
    // Intensity 0 at the top keeps the source; intensity ~1 at the bottom blurs the split.
    assertThat(splitDifference(pixels, y = 1), "unblurred top").isLessThanOrEqualTo(0.02f)
    assertThat(splitDifference(pixels, y = pixels.height - 4), "blurred bottom").isGreaterThan(0.1f)
  }

  // Like API 31–32, the masked route does not retain the input where the radial intensity is 0, so
  // it matches a full blur under the same alpha mask.
  @Test
  @Config(sdk = [35])
  fun radialProgressive_injectedFailure_drawsMaskedFallback() {
    val radial = HazeProgressive.RadialGradient(centerIntensity = 1f, radiusIntensity = 0f)
    assertMatchesReference(style(progressive = radial), style(mask = radial.asBrush()), logicalRoute = false) { progressive, _, _, _ ->
      runtimeFailure.takeIf { progressive != null }
    }
  }

  @Test
  @Config(sdk = [35])
  fun customMask_injectedFailure_drawsLogicalPathWithItsMask() =
    assertMatchesReference(style(mask = fadingBrush()), style(mask = fadingBrush())) { _, mask, _, _ ->
      runtimeFailure.takeIf { mask?.isCustomShaderBrush() == true }
    }

  // As on API 31–32, the logical route draws a progressive brush as a full blur under that brush.
  @Test
  @Config(sdk = [35])
  fun customProgressiveBrush_injectedFailure_drawsLogicalPath() =
    assertMatchesReference(style(progressive = HazeProgressive.Brush(fadingBrush())), style(mask = fadingBrush())) { progressive, _, _, _ ->
      runtimeFailure.takeIf { progressive != null }
    }

  @Test
  @Config(sdk = [35])
  fun fallback_resetsOnDetach() = runScreenshotTest {
    val probe = FailureProbe { _, mask, _, _ -> runtimeFailure.takeIf { mask != null } }
    setContent { FailureSplitContent(probe, style(mask = fadingBrush())) }
    frame()
    assertThat(probe.delegate.runtimeShaderFallback).isTrue()

    composeTestRule.runOnIdle { probe.delegate.detach() }
    assertThat(probe.delegate.runtimeShaderFallback).isFalse()
  }

  @Test
  @Config(sdk = [32])
  fun logicalPath_failureInsideAlphaLayer_restoresCanvas() = assertRestoresCanvas(
    style = { brush -> style(mask = brush, alpha = 0.8f) },
    fail = { lookups, _ -> IllegalStateException("injected").takeIf { lookups >= 1 } },
  )

  @Test
  @Config(sdk = [32])
  fun logicalPath_failureInsideColorFilterLayers_restoresCanvas() = assertRestoresCanvas(
    style = { brush ->
      style(colorEffects = listOf(HazeColorEffect.tint(brush), HazeColorEffect.colorFilter(ColorFilter.tint(Color.Green.copy(alpha = 0.5f)))))
    },
    // The second shader is the replay inside both color-filter layers.
    fail = { _, shaders -> IllegalStateException("injected").takeIf { shaders >= 2 } },
  )

  @Test
  @Config(sdk = [32])
  fun logicalBands_failureInsideBandLayer_restoresCanvas() = assertRestoresCanvas(
    style = { brush ->
      style(
        progressive = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f),
        colorEffects = listOf(HazeColorEffect.tint(brush)),
      )
    },
    // The second band styles inside its own layer and the alpha layer.
    fail = { _, shaders -> IllegalStateException("injected").takeIf { shaders >= 2 } },
  )

  @Test
  @Config(sdk = [32])
  fun layeredProgressive_failureInsideCompositeLayer_restoresCanvas() = assertRestoresCanvas(
    style = {
      style(
        progressive = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f),
        mask = Brush.verticalGradient(listOf(Color.Black, Color.Transparent)),
        alpha = 0.8f,
      )
    },
    fail = { lookups, _ -> IllegalStateException("injected").takeIf { lookups >= 2 } },
  )

  @Test
  @Config(sdk = [35])
  fun fallback_repeatedRuntimeShaderFailure_propagatesWithBalancedCanvas() = runScreenshotTest {
    var lookups = 0
    val harness = FailureDelegateHarness(style(mask = fadingBrush())) {
      lookups++
      runtimeFailure
    }
    setContent { harness.Content() }
    frame()

    assertThat(harness.failure).isNotNull().isInstanceOf<RuntimeShaderRenderEffectException>()
    assertThat(harness.delegate.runtimeShaderFallback, "one retry").isTrue()
    assertThat(lookups, "first attempt and one retry").isEqualTo(2)
    assertThat(harness.saveCountAfter).isEqualTo(harness.saveCountBefore)
    harness.delegate.detach()
  }

  /**
   * The armed frame throws inside a canvas layer and must leave the save count unchanged; the next
   * frame must draw as a delegate that never failed.
   */
  private fun assertRestoresCanvas(
    style: (Brush) -> HazeBlurStyle,
    fail: (lookups: Int, shaders: Int) -> Throwable?,
  ) = runScreenshotTest {
    var armed by mutableStateOf(true)
    var shaders = 0
    val brush = object : ShaderBrush() {
      override fun createShader(size: Size): Shader {
        if (armed) shaders++
        fail(0, shaders)?.takeIf { armed }?.let { throw it }
        return fadingShader(size)
      }
    }
    var lookups = 0
    val failing = FailureDelegateHarness(style(brush)) {
      if (armed) lookups++
      fail(lookups, 0)?.takeIf { armed }
    }
    val fresh = FailureDelegateHarness(style(brush)) { null }
    var current by mutableStateOf(failing)
    setContent { key(current) { current.Content() } }
    frame()

    assertThat(failing.failure, "armed frame").isNotNull().isInstanceOf<IllegalStateException>()
    assertThat(failing.saveCountAfter, "save count after the failure").isEqualTo(failing.saveCountBefore)

    composeTestRule.runOnIdle {
      armed = false
      failing.redraw()
    }
    val recovered = frame()
    assertThat(failing.failure, "disarmed frame").isNull()

    current = fresh
    val reference = frame()
    assertThat(fresh.failure).isNull()
    assertThat(maxDifference(recovered, reference)).isLessThan(0.01f)
    failing.delegate.detach()
    fresh.delegate.detach()
  }

  /**
   * Draws [style] through an injected-failure probe, then [reference] through a probe that never
   * fails, and checks that they match and that the reference's mask shapes the output.
   */
  private fun assertMatchesReference(
    style: HazeBlurStyle,
    reference: HazeBlurStyle,
    logicalRoute: Boolean = true,
    failure: FailureRule,
  ) = runScreenshotTest {
    val failing = FailureProbe(failure)
    val control = FailureProbe { _, _, _, _ -> null }
    var current by mutableStateOf(failing to style)
    setContent { key(current) { FailureSplitContent(current.first, current.second) } }
    val recovered = frame()
    assertThat(failing.delegate.runtimeShaderFallback).isTrue()
    assertThat(failing.delegate.logicalPathActive, "logical route").isEqualTo(logicalRoute)
    assertThat(failing.delegate.bandSlotCount, "no layered bands").isEqualTo(0)

    current = control to reference
    val expected = frame()
    assertThat(control.delegate.runtimeShaderFallback).isFalse()
    assertThat(maxDifference(recovered, expected)).isLessThan(0.02f)

    current = control to style(mask = null)
    assertThat(maxDifference(expected, frame()), "the mask changes the output").isGreaterThan(0.1f)
  }

  private fun ScreenshotUiTest.renderEffects(effects: List<PlatformRenderEffect>) {
    setContent {
      Row(Modifier.background(Color.White)) {
        effects.forEachIndexed { index, effect ->
          Box(
            Modifier.size(32.dp).testTag("effect$index").graphicsLayer {
              clip = true
              renderEffect = effect.asComposeRenderEffect()
            }.drawBehind {
              val stripe = size.width / 4
              for (x in 0..3) drawRect(if (x % 2 == 0) Color(0xFF2050C0) else Color(0xFFE0A030), Offset(x * stripe, 0f), Size(stripe, size.height))
            },
          )
        }
      }
    }
    waitForIdle()
  }

  private fun ScreenshotUiTest.boxDifference(pixels: PixelMap, first: Int, second: Int): Float {
    fun box(index: Int): List<Color> {
      val bounds = onNodeWithTag("effect$index").fetchSemanticsNode().boundsInRoot
      val left = bounds.left.roundToInt() + 1
      val top = bounds.top.roundToInt() + 1
      val size = bounds.width.roundToInt() - 2
      return (0 until size step 2).flatMap { y -> (0 until size step 2).map { x -> pixels[left + x, top + y] } }
    }
    return box(first).zip(box(second)).maxOf { (a, b) -> a.difference(b) }
  }
}

private typealias FailureRule = (progressive: HazeProgressive?, mask: Brush?, progressiveMask: Brush?, colorEffects: List<HazeColorEffect>) -> Throwable?

private val runtimeFailure get() = RuntimeShaderRenderEffectException(IllegalStateException("injected"))

/** Throws the rule's throwable from [lookup], which every draw-time effect resolution consults first. */
private class FailingBlurRenderEffectSlot(private val shouldFail: FailureRule) : BlurRenderEffectSlot() {
  override fun lookup(
    generation: Int,
    densityScale: Float,
    blurRadius: Dp,
    noiseFactor: Float,
    scale: Float,
    captureScale: ScaleFactor,
    captureSize: Size,
    colorEffects: List<HazeColorEffect>,
    colorEffectsAlphaModulate: Float,
    backgroundColor: Color,
    contentSize: Size,
    contentOffset: Offset,
    mask: Brush?,
    progressiveMask: Brush?,
    retainInputWhenMasked: Boolean,
    progressive: HazeProgressive?,
    blurTileMode: TileMode,
  ): PlatformRenderEffect? {
    shouldFail(progressive, mask, progressiveMask, colorEffects)?.let { throw it }
    return super.lookup(
      generation, densityScale, blurRadius, noiseFactor, scale, captureScale, captureSize, colorEffects,
      colorEffectsAlphaModulate, backgroundColor, contentSize, contentOffset, mask,
      progressiveMask, retainInputWhenMasked, progressive, blurTileMode,
    )
  }
}

private class FailureProbe(private val rule: FailureRule) : HazeEffectFactory<BlurConfiguration> {
  lateinit var effect: BlurVisualEffect
    private set

  val delegate: RenderEffectBlurVisualEffectDelegate
    get() = effect.delegate as RenderEffectBlurVisualEffectDelegate

  override fun createRenderer(): HazeEffectRenderer<BlurConfiguration> =
    BlurVisualEffect(renderEffectSlotFactory = { FailingBlurRenderEffectSlot(rule) }).also { effect = it }
}

/** A black left half and a white right half, so blur is measurable across the vertical split. */
@Composable
private fun FailureSplitContent(probe: FailureProbe, style: HazeBlurStyle) {
  Box(Modifier.fillMaxSize().background(Color.Black)) {
    Row(
      Modifier
        .fillMaxSize()
        .hazeEffect(factory = probe, input = HazeInput.Content, style = BlurConfiguration(style, HazePerformanceMode.Quality)),
    ) {
      Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black))
      Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
    }
  }
}

/** How far the pixels either side of the split, at row [y], are from the unblurred source. */
private fun splitDifference(pixels: PixelMap, y: Int, inset: Int = 6): Float {
  val center = pixels.width / 2
  return maxOf(pixels[center - inset, y].difference(Color.Black), pixels[center + inset, y].difference(Color.White))
}

private fun Color.difference(other: Color): Float =
  maxOf(abs(red - other.red), abs(green - other.green), abs(blue - other.blue), abs(alpha - other.alpha))

private fun style(
  mask: Brush? = null,
  progressive: HazeProgressive? = null,
  colorEffects: List<HazeColorEffect> = emptyList(),
  alpha: Float = 1f,
) = HazeBlurStyle {
  blurRadius(24.dp)
  noiseFactor(0f)
  colorEffects(colorEffects)
  mask(mask)
  progressive(progressive)
  alpha(alpha)
}

private fun fadingShader(size: Size): Shader = LinearGradientShader(
  from = Offset.Zero,
  to = Offset(size.width, 0f),
  colors = listOf(Color.Black, Color.Transparent),
)

/** An opaque-to-transparent horizontal mask that is not a Compose gradient, so it is a custom brush. */
private fun fadingBrush(): Brush = object : ShaderBrush() {
  override fun createShader(size: Size): Shader = fadingShader(size)
}

/**
 * Draws a [RenderEffectBlurVisualEffectDelegate] directly from `drawBehind`, catching what the draw
 * throws and recording the canvas save count around it.
 */
private class FailureDelegateHarness(style: HazeBlurStyle, failOnLookup: () -> Throwable?) {
  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  val effect = BlurVisualEffect(
    renderEffectSlotFactory = { FailingBlurRenderEffectSlot { _, _, _, _ -> failOnLookup() } },
  ).apply {
    update(HostLifecycleScope(context), BlurConfiguration(style, HazePerformanceMode.Quality), HazeSampling.FullResolution)
  }
  val delegate = RenderEffectBlurVisualEffectDelegate(effect)
  private var frame by mutableIntStateOf(0)
  var failure: Throwable? = null
    private set
  var saveCountBefore = -1
    private set
  var saveCountAfter = -1
    private set

  fun redraw() {
    frame++
  }

  @Composable
  fun Content() {
    CompositionLocalProvider(LocalDensity provides Density(1f)) {
      val graphics = LocalGraphicsContext.current
      Box(
        Modifier.fillMaxWidth().fillMaxHeight().background(Color.Black).drawBehind {
          frame
          val input = drawInput(this, graphics)
          saveCountBefore = drawContext.canvas.nativeCanvas.saveCount
          failure = runCatching { with(delegate) { draw(input) } }.exceptionOrNull()
          saveCountAfter = drawContext.canvas.nativeCanvas.saveCount
        },
      )
    }
  }

  private fun drawInput(drawScope: DrawScope, graphics: GraphicsContext) = object : HazeEffectRuntimeDrawScope, DrawScope by drawScope {
    override val modifierSize = drawScope.size
    override val modifierBounds = Rect(Offset.Zero, drawScope.size)
    override val layerSize = drawScope.size
    override val layerOffset = Offset.Zero
    override val sampling = HazeSampling.FullResolution
    override val hasDrawableInput = true
    override val inputSnapshot: HazeEffectInputSnapshot? = null
    override val coroutineScope = CoroutineScope(EmptyCoroutineContext)
    override fun requirePlatformContext(): PlatformContext = context
    override fun requireGraphicsContext(): GraphicsContext = graphics
    override fun requireDensity(): Density = Density(1f)
    override fun <T> currentValueOf(local: CompositionLocal<T>): T = error("Unused by the delegate")
    override fun invalidateDraw() = Unit
    override fun drawInput() = Unit
    override fun DrawScope.drawInput() {
      drawRect(Color.Black, size = Size(size.width / 2, size.height))
      drawRect(Color.White, topLeft = Offset(size.width / 2, 0f), size = Size(size.width / 2, size.height))
    }
  }
}
