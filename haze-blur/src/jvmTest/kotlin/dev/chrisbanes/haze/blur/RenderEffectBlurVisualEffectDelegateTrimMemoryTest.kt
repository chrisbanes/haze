// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(
  androidx.compose.ui.InternalComposeUiApi::class,
  dev.chrisbanes.haze.InternalHazeApi::class,
)

package dev.chrisbanes.haze.blur

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SkiaGraphicsContext
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.TrimMemoryLevel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

class RenderEffectBlurVisualEffectDelegateTrimMemoryTest {

  @Test
  fun renderEffectCache_isOwnedByEachBlurRuntime() {
    val first = HazeBlurFactory.createRenderer() as BlurVisualEffect
    val second = HazeBlurFactory.createRenderer() as BlurVisualEffect

    assertThat(first.renderEffectCache).isNotSameInstanceAs(second.renderEffectCache)
  }

  @Test
  fun onTrimMemory_backgroundKeepsRetainedOutputAvailability() {
    val delegate = RenderEffectBlurVisualEffectDelegate(
      HazeBlurFactory.createRenderer() as BlurVisualEffect,
    )
    delegate.setPrivateField("retainedOutputAvailable", true)
    val context = RecordingLifecycleScope()

    delegate.onTrimMemory(context, TrimMemoryLevel.BACKGROUND)

    assertThat(delegate.getPrivateField<Boolean>("retainedOutputAvailable")).isTrue()
    assertThat(context.invalidateDrawCalls).isEqualTo(0)
  }

  @Test
  fun onTrimMemory_moderateClearsRetainedOutputAvailabilityAndInvalidatesDraw() {
    val delegate = RenderEffectBlurVisualEffectDelegate(
      HazeBlurFactory.createRenderer() as BlurVisualEffect,
    )
    delegate.setPrivateField("retainedOutputAvailable", true)
    delegate.setPrivateField("lastScaledLayerSize", Size(10f, 10f))
    val context = RecordingLifecycleScope()

    delegate.onTrimMemory(context, TrimMemoryLevel.MODERATE)

    assertThat(delegate.getPrivateField<Boolean>("retainedOutputAvailable")).isFalse()
    assertThat(delegate.getPrivateField<Size?>("lastScaledLayerSize")).isEqualTo(null)
    assertThat(context.invalidateDrawCalls).isEqualTo(1)
  }

  @Test
  fun detach_releasesCaptureSoTheNextDrawableFrameRecordsAgain() {
    val delegate = RenderEffectBlurVisualEffectDelegate(
      HazeBlurFactory.createRenderer() as BlurVisualEffect,
    )
    val context = RecordingDrawContext()

    context.render { with(delegate) { draw(context) } }
    context.render { with(delegate) { draw(context) } }
    delegate.detach()
    context.render { with(delegate) { draw(context) } }

    assertThat(context.inputCaptureCount).isEqualTo(2)
  }

  @Test
  fun onTrimMemory_moderateReleasesCaptureSoTheNextDrawableFrameRecordsAgain() {
    val delegate = RenderEffectBlurVisualEffectDelegate(
      HazeBlurFactory.createRenderer() as BlurVisualEffect,
    )
    val context = RecordingDrawContext()

    context.render { with(delegate) { draw(context) } }
    context.render { with(delegate) { draw(context) } }
    delegate.onTrimMemory(RecordingLifecycleScope(), TrimMemoryLevel.MODERATE)
    context.render { with(delegate) { draw(context) } }

    assertThat(context.inputCaptureCount).isEqualTo(2)
  }

  @Test
  fun releasedLayer_releasesCaptureSoTheNextDrawableFrameRecordsAgain() {
    val delegate = RenderEffectBlurVisualEffectDelegate(
      HazeBlurFactory.createRenderer() as BlurVisualEffect,
    )
    val context = RecordingDrawContext()

    context.render { with(delegate) { draw(context) } }
    context.render { with(delegate) { draw(context) } }
    context.releaseLatestLayer()
    context.render { with(delegate) { draw(context) } }

    assertThat(context.inputCaptureCount).isEqualTo(2)
  }

  @Test
  fun releasedLayerWithoutDrawableInput_isNotReusedAndNextDrawableFrameRecordsAgain() {
    val delegate = RenderEffectBlurVisualEffectDelegate(
      HazeBlurFactory.createRenderer() as BlurVisualEffect,
    )
    val context = RecordingDrawContext()

    context.render { with(delegate) { draw(context) } }
    context.render { with(delegate) { draw(context) } }
    context.hasDrawableInput = false
    context.releaseLatestLayer()
    context.render { with(delegate) { draw(context) } }

    assertThat(delegate.canDrawRetainedOutput()).isFalse()
    assertThat(context.inputCaptureCount).isEqualTo(1)

    context.hasDrawableInput = true
    context.render { with(delegate) { draw(context) } }

    assertThat(context.inputCaptureCount).isEqualTo(2)
  }

  @Test
  fun drawableInputReturningAtOriginalSize_restoresRetainedOutputAfterCacheReuse() {
    val delegate = RenderEffectBlurVisualEffectDelegate(
      HazeBlurFactory.createRenderer() as BlurVisualEffect,
    )
    val context = RecordingDrawContext()

    context.render { with(delegate) { draw(context) } }
    context.hasDrawableInput = false
    context.layerSize = Size(11f, 10f)
    context.render { with(delegate) { draw(context) } }

    context.hasDrawableInput = true
    context.layerSize = context.modifierSize
    context.render { with(delegate) { draw(context) } }

    assertThat(context.inputCaptureCount).isEqualTo(1)
    assertThat(delegate.canDrawRetainedOutput()).isTrue()

    context.hasDrawableInput = false
    context.render { with(delegate) { draw(context) } }

    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun captureKeyChanges_recordInputAgain() {
    val effect = BlurVisualEffect()
    val delegate = RenderEffectBlurVisualEffectDelegate(effect)
    val context = RecordingDrawContext()

    context.render { with(delegate) { draw(context) } }
    context.inputSnapshot = DifferentCaptureSnapshot
    context.render { with(delegate) { draw(context) } }
    effect.update(
      BlurLifecycleScope,
      BlurConfiguration(HazeBlurStyle, HazePerformanceMode.Performance),
      HazeSampling.Fixed(0.8f),
    )
    context.render { with(delegate) { draw(context) } }
    context.layerSize = Size(11f, 10f)
    context.render { with(delegate) { draw(context) } }
    context.layerOffset = Offset(1f, 0f)
    context.render { with(delegate) { draw(context) } }

    effect.update(
      BlurLifecycleScope,
      BlurConfiguration(
        HazeBlurStyle { backgroundColor(Color.Blue) },
        HazePerformanceMode.Performance,
      ),
      HazeSampling.Fixed(0.8f),
    )
    context.render { with(delegate) { draw(context) } }

    assertThat(context.inputCaptureCount).isEqualTo(6)
  }

  @Test
  fun styleOnlyChange_drawsWithoutRecapturingInput() {
    val effect = BlurVisualEffect()
    val delegate = RenderEffectBlurVisualEffectDelegate(effect)
    val context = RecordingDrawContext()

    context.render { with(delegate) { draw(context) } }
    effect.update(
      BlurLifecycleScope,
      BlurConfiguration(HazeBlurStyle { alpha(0.5f) }, HazePerformanceMode.Quality),
      HazeSampling.FullResolution,
    )
    context.render { with(delegate) { draw(context) } }

    assertThat(context.inputCaptureCount).isEqualTo(1)
  }

  @Test
  fun clearRetainedOutput_releasesLayerMetadataAndAvailability() {
    val delegate = RenderEffectBlurVisualEffectDelegate(
      HazeBlurFactory.createRenderer() as BlurVisualEffect,
    )
    delegate.setPrivateField("retainedOutputAvailable", true)
    delegate.setPrivateField("lastScaledLayerSize", Size(10f, 10f))

    delegate.clearRetainedOutput()

    assertThat(delegate.getPrivateField<Boolean>("retainedOutputAvailable")).isFalse()
    assertThat(delegate.getPrivateField<Size?>("lastScaledLayerSize")).isEqualTo(null)
    assertThat(delegate.getPrivateField<Any?>("scaledContentLayer")).isEqualTo(null)
    assertThat(delegate.getPrivateField<Any?>("graphicsContext")).isEqualTo(null)
  }

  @Test
  fun clearRetainedOutput_releasesCaptureSoTheNextDrawableFrameRecordsAgain() {
    val delegate = RenderEffectBlurVisualEffectDelegate(
      HazeBlurFactory.createRenderer() as BlurVisualEffect,
    )
    val context = RecordingDrawContext()

    context.render { with(delegate) { draw(context) } }
    context.render { with(delegate) { draw(context) } }
    delegate.clearRetainedOutput()
    context.render { with(delegate) { draw(context) } }

    assertThat(context.inputCaptureCount).isEqualTo(2)
  }

  @Test
  fun backdropEffect_forwardsProgressiveStyleToTheRootRenderEffect() {
    val effect = BlurVisualEffect()
    val context = RecordingDrawContext(recordedSize = Size(100f, 100f))

    fun render(style: HazeBlurStyle): Pair<Float, Float> {
      val configuration = BlurConfiguration(style, HazePerformanceMode.Quality)
      effect.update(BlurLifecycleScope, configuration, HazeSampling.FullResolution)
      context.render { with(effect) { with(context) { prepareDraw(configuration) } } }
      var backdrop: dev.chrisbanes.haze.HazeEffectBackdrop? = null
      context.render {
        backdrop = with(effect) { with(context) { backdropEffect(configuration) } }
      }

      val graphicsContext = SkiaGraphicsContext()
      val inputLayer = graphicsContext.createGraphicsLayer()
      inputLayer.record(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        size = IntSize(100, 100),
      ) {
        drawRect(Color.Black, size = Size(50f, 100f))
        drawRect(Color.White, topLeft = Offset(50f, 0f), size = Size(50f, 100f))
      }
      inputLayer.renderEffect = checkNotNull(backdrop).getPlatformEffect().asComposeRenderEffect()
      val output = ImageBitmap(100, 100)
      CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(output),
        size = Size(100f, 100f),
      ) {
        drawLayer(inputLayer)
      }
      graphicsContext.releaseGraphicsLayer(inputLayer)

      val pixels = output.toPixelMap()
      fun edgeSoftening(y: Int): Float = (51..60).map { pixels[it, y].red }.average().toFloat()
      return edgeSoftening(20) to edgeSoftening(80)
    }

    val forward = render(
      HazeBlurStyle {
        blurRadius(14.dp)
        noiseFactor(0f)
        colorEffects(emptyList())
        progressive(HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f))
      },
    )
    val reversed = render(
      HazeBlurStyle {
        blurRadius(14.dp)
        noiseFactor(0f)
        colorEffects(emptyList())
        progressive(HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f))
      },
    )

    assertThat(forward.second - forward.first)
      .isGreaterThan(0.03f)
    assertThat(reversed.first - reversed.second)
      .isGreaterThan(0.03f)
  }
}

private fun Any.setPrivateField(name: String, value: Any?) {
  javaClass.getDeclaredField(name).apply {
    isAccessible = true
    set(this@setPrivateField, value)
  }
}

private inline fun <reified T> Any.getPrivateField(name: String): T {
  return javaClass.getDeclaredField(name).run {
    isAccessible = true
    get(this@getPrivateField) as T
  }
}

private class RecordingLifecycleScope : HazeEffectLifecycleScope {
  var invalidateDrawCalls = 0
    private set

  override val modifierSize: Size = Size.Zero
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun requirePlatformContext(): PlatformContext = error("Unused in trim-memory tests")
  override fun requireDensity(): Density = Density(1f)
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = error("Unused in trim-memory tests")
  override fun requireGraphicsContext(): GraphicsContext = error("Unused in trim-memory tests")

  override fun invalidateDraw() {
    invalidateDrawCalls++
  }

  override fun invalidateLayerBounds() = Unit
}

private data object BlurLifecycleScope : HazeEffectLifecycleScope {
  override val modifierSize: Size = Size.Zero
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun requirePlatformContext(): PlatformContext = PlatformContext.INSTANCE
  override fun requireDensity(): Density = Density(1f)
  override fun <T> currentValueOf(local: CompositionLocal<T>): T {
    @Suppress("UNCHECKED_CAST")
    return HazeBlurStyle as T
  }
  override fun requireGraphicsContext(): GraphicsContext = error("Unused in direct-draw tests")
  override fun invalidateDraw() = Unit
  override fun invalidateLayerBounds() = Unit
}

@OptIn(InternalComposeUiApi::class, InternalHazeApi::class)
private class RecordingDrawContext(
  private val drawScope: CanvasDrawScope = CanvasDrawScope(),
  private val recordedSize: Size = Size(10f, 10f),
) : HazeEffectRuntimeDrawScope, DrawScope by drawScope {
  private val graphicsContext = RecordingGraphicsContext()

  var inputCaptureCount = 0
    private set

  override val modifierSize: Size = recordedSize
  override val modifierBounds: Rect = Rect(Offset.Zero, modifierSize)
  override var sampling: HazeSampling = HazeSampling.FullResolution
  override var layerSize: Size = modifierSize
  override var layerOffset: Offset = Offset.Zero
  override var hasDrawableInput: Boolean = true
  override var inputSnapshot: HazeEffectInputSnapshot? = CaptureSnapshot
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  fun render(block: DrawScope.() -> Unit) {
    drawScope.draw(
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
      canvas = Canvas(ImageBitmap(recordedSize.width.toInt(), recordedSize.height.toInt())),
      size = modifierSize,
      block = block,
    )
  }

  fun releaseLatestLayer() {
    graphicsContext.releaseLatestLayer()
  }

  override fun requirePlatformContext(): PlatformContext = PlatformContext.INSTANCE
  override fun requireGraphicsContext(): GraphicsContext = graphicsContext
  override fun requireDensity(): Density = Density(1f)
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = error("Unused in direct-draw tests")
  override fun invalidateDraw() = Unit
  override fun drawInput() = Unit

  override fun DrawScope.drawInput() {
    this@RecordingDrawContext.inputCaptureCount++
    drawRect(Color.Red)
  }
}

private data object CaptureSnapshot : HazeEffectInputSnapshot
private data object DifferentCaptureSnapshot : HazeEffectInputSnapshot

@OptIn(InternalComposeUiApi::class)
private class RecordingGraphicsContext : GraphicsContext {
  private val delegate = SkiaGraphicsContext()
  private var latestLayer: androidx.compose.ui.graphics.layer.GraphicsLayer? = null

  override fun createGraphicsLayer() = delegate.createGraphicsLayer().also { latestLayer = it }

  override fun releaseGraphicsLayer(layer: androidx.compose.ui.graphics.layer.GraphicsLayer) {
    delegate.releaseGraphicsLayer(layer)
  }

  fun releaseLatestLayer() {
    latestLayer?.let(::releaseGraphicsLayer)
  }
}
