// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.asBrush
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

/**
 * An unchanged Blur draw reuses its RenderEffect, Compose wrapper and layer assignment on every
 * Android branch, while a changed input, a cache clear or (on API 31) a re-captured input still
 * gives a fresh effect.
 */
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

private val ORDINARY = blurStyle(null)
private val LINEAR = blurStyle(HazeProgressive.verticalGradient(startIntensity = 0.5f, endIntensity = 0.5f))
private val RADIAL = blurStyle(HazeProgressive.RadialGradient(centerIntensity = 0.5f, radiusIntensity = 0.5f))
