// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.asBrush
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import org.robolectric.annotation.Config

/**
 * An unchanged Blur draw reuses its RenderEffect, Compose wrapper and layer assignment on every
 * Android branch, while a changed input, a cache clear or (on API 31) a re-captured input still
 * gives a fresh effect.
 */
// Robolectric rasterizes the API 33+ progressive blur shader on the CPU, so every capture's cost
// scales with the window area. A small window keeps every branch and band count above two.
@Config(qualifiers = "+w160dp-h320dp")
class BlurRenderEffectReuseAndroidHostTest : ScreenshotTest() {

  @Test
  @Config(sdk = [35])
  fun api35_ordinary_unchangedDrawsReuseTheEffect() = assertUnchangedDrawsReuse(ORDINARY)

  @Test
  @Config(sdk = [35])
  fun api35_linearProgressive_unchangedDrawsReuseTheEffect() = assertUnchangedDrawsReuse(LINEAR)

  @Test
  @Config(sdk = [35])
  fun api35_radialProgressive_unchangedDrawsReuseTheEffect() = assertUnchangedDrawsReuse(RADIAL)

  @Test
  @Config(sdk = [32])
  fun api32_ordinary_unchangedDrawsReuseTheEffect() = assertUnchangedDrawsReuse(ORDINARY)

  @Test
  @Config(sdk = [32])
  fun api32_maskedProgressive_unchangedDrawsReuseTheEffectAndBrush() =
    assertUnchangedDrawsReuse(RADIAL, expectedBrushCalls = 1)

  @Test
  @Config(sdk = [32])
  fun api32_layeredProgressive_unchangedDrawsReuseEveryBand() = assertUnchangedDrawsReuse(LINEAR)

  @Test
  @Config(sdk = [31])
  fun api31_ordinary_everyRecaptureBuildsAFreshEffect() =
    assertUnchangedDrawsReuse(ORDINARY, freshEffectPerRecapture = true)

  @Test
  @Config(sdk = [35])
  fun api35_softwareCanvas_selectsScrimFallbackAndRecoversAfterStyleChange() {
    val context = SoftwareDrawContext()
    val scope = HostLifecycleScope(ApplicationProvider.getApplicationContext())
    val effect = BlurVisualEffect()
    val tinted = BlurConfiguration(
      HazeBlurStyle { fallbackColorEffect(HazeColorEffect.tint(Color.Red)) },
      HazePerformanceMode.Quality,
    )
    val untinted = BlurConfiguration(HazeBlurStyle, HazePerformanceMode.Quality)

    effect.update(scope, tinted, HazeSampling.FullResolution)
    val fallbackOutput = context.draw(effect, tinted).toPixelMap()
    assertThat(context.wasHardwareAccelerated).isFalse()
    assertThat(effect.delegate).isInstanceOf<ScrimBlurVisualEffectDelegate>()
    assertThat(effect.shouldDrawContentBehind()).isTrue()
    assertThat(fallbackOutput[32, 32]).isEqualTo(Color.Red)

    effect.update(scope, untinted, HazeSampling.FullResolution)
    val untintedOutput = context.draw(effect, untinted).toPixelMap()
    assertThat(untintedOutput[32, 32]).isEqualTo(Color.Blue)

    effect.update(scope, tinted, HazeSampling.FullResolution)
    val recoveredOutput = context.draw(effect, tinted).toPixelMap()
    assertThat(recoveredOutput[32, 32]).isEqualTo(fallbackOutput[32, 32])
  }

  @Test
  @Config(sdk = [35])
  fun api35_sourceDescendantLayerChange_matchesFreshEffectAttachment() = runScreenshotTest {
    val state = HazeState()
    val probe = Probe()
    var alpha by mutableStateOf(1f)
    var effectAttachment by mutableStateOf(0)

    setContent {
      Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().hazeSource(state)) {
          Box(Modifier.fillMaxSize().graphicsLayer {}) {
            Column(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
              Box(Modifier.weight(1f).fillMaxSize().background(Color.Red))
              Box(Modifier.weight(1f).fillMaxSize().background(Color.Yellow))
            }
          }
        }

        key(effectAttachment) {
          Box(
            Modifier
              .fillMaxSize()
              .hazeEffect(
                factory = probe,
                input = HazeInput.Sources(state),
                style = BlurConfiguration(blurStyle(null), HazePerformanceMode.Quality),
              ),
          )
        }
      }
    }

    frame()
    assertThat(probe.effect.delegate).isInstanceOf(RenderEffectBlurVisualEffectDelegate::class)
    val initial = captureRootPixels()

    // This updates the descendant graphics layer without changing or explicitly invalidating the
    // parent hazeSource node. The same attached source must nevertheless refresh its capture.
    alpha = 0.4f
    frame()
    val changed = captureRootPixels()
    assertThat(maxDifference(initial, changed)).isGreaterThan(0.01f)

    // Reattaching only the effect establishes the expected output at the same source alpha.
    effectAttachment++
    frame()
    assertThat(maxDifference(changed, captureRootPixels())).isLessThan(0.01f)
  }

  @Test
  @Config(sdk = [31])
  fun api31_layeredProgressive_everyRecaptureBuildsFreshBandEffects() =
    assertUnchangedDrawsReuse(LINEAR, freshEffectPerRecapture = true, assignsPerBand = true)

  @Test
  @Config(sdk = [32])
  fun layered_bandCountFollowsTheNodeSize() = runScreenshotTest {
    val probe = Probe()
    var fillFraction by mutableStateOf(1f)
    var attachment by mutableStateOf(0)
    setContent {
      key(attachment) { BlurContent(probe, LINEAR, Color.White, fillFraction) }
    }
    frame()
    val tallBands = probe.delegate.bandSlotCount
    assertThat(tallBands).isGreaterThan(2)

    fillFraction = 0.5f
    frame()
    val resizedBands = probe.delegate.bandSlotCount
    val resized = captureRootPixels()
    assertThat(resizedBands).isLessThan(tallBands)

    attachment++
    frame()
    assertThat(resizedBands).isEqualTo(probe.delegate.bandSlotCount)
    assertThat(maxDifference(resized, captureRootPixels())).isLessThan(0.01f)
  }

  @Test
  @Config(sdk = [32])
  fun leavingTheLayeredBranch_releasesTheBandSlots() = runScreenshotTest {
    val probe = Probe()
    var style by mutableStateOf(LINEAR)
    setContent { BlurContent(probe, style, Color.White) }
    frame()
    assertThat(probe.delegate.bandSlotCount).isGreaterThan(0)

    style = RADIAL
    frame()
    assertThat(probe.delegate.bandSlotCount, "masked").isEqualTo(0)

    style = LINEAR
    frame()
    assertThat(probe.delegate.bandSlotCount).isGreaterThan(0)

    style = ORDINARY
    frame()
    assertThat(probe.delegate.bandSlotCount, "ordinary").isEqualTo(0)
  }

  @Test
  @Config(sdk = [32])
  fun layered_changedProgressive_matchesAFreshAttachment() = runScreenshotTest {
    val probe = Probe()
    var progressive by mutableStateOf(HazeProgressive.verticalGradient(startIntensity = 0.2f, endIntensity = 0.8f))
    var attachment by mutableStateOf(0)
    setContent {
      key(attachment) { BlurContent(probe, layeredStyle(progressive), Color.White) }
    }
    frame()

    progressive = HazeProgressive.verticalGradient(startIntensity = 0.8f, endIntensity = 0.2f)
    frame()
    val changed = captureRootPixels()

    attachment++
    frame()
    assertThat(maxDifference(changed, captureRootPixels())).isLessThan(0.01f)
  }

  @Test
  @Config(sdk = [35])
  fun trimBackground_buildsAFreshEffectOnTheNextDraw() = assertCacheClear { probe, _ ->
    probe.effect.onTrimMemory(TrimMemoryLevel.BACKGROUND)
  }

  @Test
  @Config(sdk = [35])
  fun trimModerate_releasesTheSlotsAndBuildsAFreshEffect() = assertCacheClear { probe, _ ->
    probe.effect.onTrimMemory(TrimMemoryLevel.MODERATE)
  }

  @Test
  @Config(sdk = [35])
  fun clearRetainedOutput_releasesTheSlotsAndBuildsAFreshEffect() = assertCacheClear { probe, _ ->
    probe.effect.clearRetainedOutput()
  }

  @Test
  @Config(sdk = [32])
  fun layered_unchangedDraws_keepEveryBandLayerAndMask() = runScreenshotTest {
    val probe = Probe()
    var content by mutableStateOf(Color.White)
    setContent { BlurContent(probe, LINEAR, content) }
    frame()
    val layers = probe.bandLayers()
    val masksBuilt = probe.delegate.bandMasks.buildCount
    val cold = probe.counts()
    assertThat(layers.size).isGreaterThan(2)

    listOf(Color.Red, Color.Green, Color.Blue).forEach {
      content = it
      frame()
    }

    val delta = probe.counts() - cold
    assertThat(delta.slotLookups, "slot consulted by the extra draws").isGreaterThan(0)
    probe.bandLayers().zip(layers).forEach { (now, before) ->
      assertThat(now).isSameInstanceAs(before)
      assertThat(now.isReleased).isFalse()
    }
    assertThat(probe.delegate.bandMasks.buildCount).isEqualTo(masksBuilt)
    assertThat(delta.cache, "cache lookups").isEqualTo(0)
    assertThat(delta.wraps, "wrapper creations").isEqualTo(0)
    assertThat(delta.assigns, "layer assignments").isEqualTo(0)
  }

  @Test
  @Config(sdk = [32])
  fun layered_intensityChange_keepsMasks_geometryChange_rebuildsThem() = runScreenshotTest {
    val probe = Probe()
    var progressive by mutableStateOf(HazeProgressive.verticalGradient(startIntensity = 0.2f, endIntensity = 0.8f))
    setContent { BlurContent(probe, layeredStyle(progressive), Color.White) }
    frame()
    val built = probe.delegate.bandMasks.buildCount
    assertThat(built).isGreaterThan(0)

    progressive = HazeProgressive.verticalGradient(startIntensity = 0.6f, endIntensity = 0.1f)
    frame()
    assertThat(probe.delegate.bandMasks.buildCount, "after an intensity change").isEqualTo(built)

    progressive = HazeProgressive.verticalGradient(startY = 40f, startIntensity = 0.6f, endIntensity = 0.1f)
    frame()
    assertThat(probe.delegate.bandMasks.buildCount, "after a geometry change").isGreaterThan(built)
  }

  @Test
  @Config(sdk = [32])
  fun layered_nodeResize_releasesSurplusBandLayersAndKeepsTheRest() = runScreenshotTest {
    val probe = Probe()
    var fillFraction by mutableStateOf(1f)
    setContent { BlurContent(probe, LINEAR, Color.White, fillFraction) }
    frame()
    val tall = probe.bandLayers()

    fillFraction = 0.5f
    frame()
    val short = probe.bandLayers()
    assertThat(short.size).isLessThan(tall.size)
    short.forEachIndexed { index, layer -> assertThat(layer).isSameInstanceAs(tall[index]) }
    tall.drop(short.size).forEach { assertThat(it.isReleased).isTrue() }

    fillFraction = 1f
    frame()
    val regrown = probe.bandLayers()
    assertThat(regrown.size).isEqualTo(tall.size)
    regrown.take(short.size).forEachIndexed { index, layer -> assertThat(layer).isSameInstanceAs(short[index]) }
    regrown.drop(short.size).forEach { assertThat(it.isReleased).isFalse() }
  }

  @Test
  @Config(sdk = [32])
  fun leavingTheLayeredBranch_releasesBandLayersAndMasks() = runScreenshotTest {
    val probe = Probe()
    var style by mutableStateOf(LINEAR)
    setContent { BlurContent(probe, style, Color.White) }
    frame()
    val layers = probe.bandLayers()
    assertThat(layers.size).isGreaterThan(0)
    assertThat(probe.delegate.bandMasks.isEmpty).isFalse()

    style = RADIAL
    frame()

    layers.forEach { assertThat(it.isReleased).isTrue() }
    assertThat(probe.delegate.bandMasks.isEmpty).isTrue()
  }

  @Test
  @Config(sdk = [32])
  fun layered_trimModerate_releasesBandLayersAndTheNextFrameMatches() =
    assertLayeredRelease { probe -> probe.effect.onTrimMemory(TrimMemoryLevel.MODERATE) }

  @Test
  @Config(sdk = [32])
  fun layered_clearRetainedOutput_releasesBandLayersAndTheNextFrameMatches() =
    assertLayeredRelease { probe -> probe.effect.clearRetainedOutput() }

  @Test
  @Config(sdk = [32])
  fun layered_detach_releasesBandLayers() = runScreenshotTest {
    val probe = Probe()
    var attachment by mutableStateOf(0)
    setContent { key(attachment) { BlurContent(probe, LINEAR, Color.White) } }
    frame()
    val layers = probe.bandLayers()
    assertThat(layers.size).isGreaterThan(0)

    attachment++
    waitForIdle()

    layers.forEach { assertThat(it.isReleased).isTrue() }
  }

  @Test
  @Config(sdk = [32])
  fun layered_trimBackground_keepsLayersButAssignsAFreshEffectPerBand() = runScreenshotTest {
    val probe = Probe()
    var content by mutableStateOf(Color.White)
    setContent { BlurContent(probe, LINEAR, content) }
    frame()
    val layers = probe.bandLayers()
    val before = probe.counts()

    probe.effect.onTrimMemory(TrimMemoryLevel.BACKGROUND)
    content = Color.Red
    frame()

    probe.bandLayers().zip(layers).forEach { (now, was) -> assertThat(now).isSameInstanceAs(was) }
    assertThat((probe.counts() - before).assigns).isEqualTo(layers.size)
  }

  @Test
  @Config(sdk = [32])
  fun layered_repeatedDraws_matchAFreshAttachment() = runScreenshotTest {
    val probe = Probe()
    var content by mutableStateOf(Color.White)
    var attachment by mutableStateOf(0)
    setContent { key(attachment) { BlurContent(probe, LINEAR, content) } }
    frame()
    listOf(Color.Red, Color.Green, Color.Blue).forEach {
      content = it
      frame()
    }
    val repeated = captureRootPixels()

    attachment++
    frame()

    assertThat(maxDifference(repeated, captureRootPixels())).isLessThan(0.01f)
  }

  private fun assertUnchangedDrawsReuse(
    style: HazeBlurStyle,
    freshEffectPerRecapture: Boolean = false,
    // Each band layer takes its wrapper through its slot, so a fresh effect assigns once per band;
    // otherwise only the content layer's own assignment is deduplicated.
    assignsPerBand: Boolean = false,
    expectedBrushCalls: Int? = null,
  ) = runScreenshotTest {
    val probe = Probe()
    var content by mutableStateOf(Color.White)
    setContent { BlurContent(probe, style, content) }
    frame()
    val cold = probe.counts()
    assertThat(cold.cache).isGreaterThan(0)
    assertThat(cold.wraps).isEqualTo(cold.cache)

    val recaptures = listOf(Color.Red, Color.Green, Color.Blue)
    recaptures.forEach {
      content = it
      frame()
    }

    val delta = probe.counts() - cold
    assertThat(delta.slotLookups, "slot consulted by the extra draws").isGreaterThan(0)
    if (freshEffectPerRecapture) {
      // API 31 retains blurred pixels unless a re-captured input gets a fresh effect. The cache
      // clear that guarantees it is counted by the generation; Robolectric does not draw a frame
      // for every content change, so the clears, not the changes, set the expectation.
      assertThat(delta.generation, "input changes that cleared the cache").isGreaterThan(0)
      assertThat(delta.cache).isEqualTo(delta.generation * cold.cache)
      assertThat(delta.wraps).isEqualTo(delta.generation * cold.wraps)
      assertThat(delta.assigns).isEqualTo(delta.generation * if (assignsPerBand) cold.cache else 1)
    } else {
      assertThat(delta.generation, "cache clears").isEqualTo(0)
      assertThat(delta.cache, "cache lookups").isEqualTo(0)
      assertThat(delta.wraps, "wrapper creations").isEqualTo(0)
      assertThat(delta.assigns, "layer assignments").isEqualTo(0)
    }
    expectedBrushCalls?.let { assertThat(probe.brushCalls).isEqualTo(it) }
  }

  private fun assertCacheClear(clear: (Probe, ScreenshotUiTest) -> Unit) = runScreenshotTest {
    val probe = Probe()
    var content by mutableStateOf(Color.White)
    setContent { BlurContent(probe, ORDINARY, content) }
    frame()
    val before = probe.counts()
    assertThat(probe.delegate).isInstanceOf(RenderEffectBlurVisualEffectDelegate::class)

    clear(probe, this)
    content = Color.Red
    frame()

    val delta = probe.counts() - before
    assertThat(delta.cache).isEqualTo(1)
    assertThat(delta.wraps).isEqualTo(1)
    assertThat(delta.assigns).isEqualTo(1)
  }

  private fun assertLayeredRelease(release: (Probe) -> Unit) = runScreenshotTest {
    val probe = Probe()
    setContent { BlurContent(probe, LINEAR, Color.White) }
    val before = frame()
    val layers = probe.bandLayers()
    assertThat(layers.size).isGreaterThan(0)

    release(probe)

    layers.forEach { assertThat(it.isReleased).isTrue() }
    val after = frame()
    assertThat(maxDifference(before, after)).isLessThan(0.01f)
    probe.bandLayers().forEach { assertThat(it.isReleased).isFalse() }
  }
}

@Poko
private class Counts(
  val cache: Int,
  val wraps: Int,
  val assigns: Int,
  val slotLookups: Int,
  val generation: Int,
) {
  operator fun minus(other: Counts) = Counts(
    cache - other.cache,
    wraps - other.wraps,
    assigns - other.assigns,
    slotLookups - other.slotLookups,
    generation - other.generation,
  )
}

/** Creates the Blur runtime a node uses, counting its brush builds and every slot's work. */
private class Probe : HazeEffectFactory<BlurConfiguration> {
  private val slots = mutableListOf<CountingBlurRenderEffectSlot>()
  lateinit var effect: BlurVisualEffect
    private set

  var brushCalls = 0
    private set

  val delegate: RenderEffectBlurVisualEffectDelegate
    get() = effect.delegate as RenderEffectBlurVisualEffectDelegate

  override fun createRenderer(): HazeEffectRenderer<BlurConfiguration> = BlurVisualEffect(
    progressiveBrushFactory = {
      brushCalls++
      it.asBrush()
    },
    renderEffectSlotFactory = { CountingBlurRenderEffectSlot().also(slots::add) },
  ).also { effect = it }

  /** The layers the latest layered draw is holding, in band order. */
  fun bandLayers() = (0 until delegate.bandSlotCount).map { checkNotNull(delegate.bandLayer(it).layer) }

  fun counts() = Counts(
    cache = effect.renderEffectCache.hitCount() + effect.renderEffectCache.missCount(),
    wraps = slots.sumOf { it.wraps },
    assigns = slots.sumOf { it.assigns },
    slotLookups = slots.sumOf { it.lookups },
    generation = effect.renderEffectGeneration,
  )
}

@androidx.compose.runtime.Composable
private fun BlurContent(
  probe: Probe,
  style: HazeBlurStyle,
  lowerColor: Color,
  fillFraction: Float = 1f,
) {
  Box(Modifier.fillMaxSize().background(Color.Black)) {
    Column(
      Modifier
        .fillMaxHeight(fillFraction)
        .fillMaxSize()
        .hazeEffect(
          factory = probe,
          input = HazeInput.Content,
          style = BlurConfiguration(style, HazePerformanceMode.Quality),
        ),
    ) {
      Box(Modifier.weight(1f).fillMaxSize().background(Color.Black))
      Box(Modifier.weight(1f).fillMaxSize().background(lowerColor))
    }
  }
}

private fun blurStyle(progressive: HazeProgressive?) = HazeBlurStyle {
  blurRadius(48.dp)
  noiseFactor(0f)
  progressive(progressive)
}

private fun layeredStyle(progressive: HazeProgressive.LinearGradient) = blurStyle(progressive)

@OptIn(dev.chrisbanes.haze.InternalHazeApi::class)
private class SoftwareDrawContext(
  private val drawScope: CanvasDrawScope = CanvasDrawScope(),
) : HazeEffectRuntimeDrawScope, DrawScope by drawScope {
  override val modifierSize: Size = Size(64f, 64f)
  override val modifierBounds: Rect = Rect(Offset.Zero, modifierSize)
  override val sampling: HazeSampling = HazeSampling.FullResolution
  override val layerSize: Size = modifierSize
  override val layerOffset: Offset = Offset.Zero
  override val hasDrawableInput: Boolean = false
  override val inputSnapshot: HazeEffectInputSnapshot? = null
  override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

  var wasHardwareAccelerated: Boolean = true
    private set

  fun draw(effect: BlurVisualEffect, configuration: BlurConfiguration): ImageBitmap {
    val bitmap = ImageBitmap(64, 64)
    drawScope.draw(
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
      canvas = Canvas(bitmap),
      size = modifierSize,
    ) {
      this@SoftwareDrawContext.wasHardwareAccelerated =
        drawContext.canvas.nativeCanvas.isHardwareAccelerated
      drawRect(Color.Blue)
      with(effect) { this@SoftwareDrawContext.draw(configuration) }
    }
    return bitmap
  }

  override fun requirePlatformContext(): PlatformContext =
    ApplicationProvider.getApplicationContext()

  override fun requireGraphicsContext() = error("The scrim fallback does not need graphics layers")
  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = HazeBlurStyle as T
  override fun invalidateDraw() = Unit
  override fun drawInput() = Unit
  override fun DrawScope.drawInput() = Unit
}

private val ORDINARY = blurStyle(null)
private val LINEAR = blurStyle(HazeProgressive.verticalGradient(startIntensity = 0.5f, endIntensity = 0.5f))
private val RADIAL = blurStyle(HazeProgressive.RadialGradient(centerIntensity = 0.5f, radiusIntensity = 0.5f))
