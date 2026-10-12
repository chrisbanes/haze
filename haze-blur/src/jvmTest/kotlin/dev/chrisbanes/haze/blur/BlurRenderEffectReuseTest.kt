// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.HazeEffectBackdrop
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.TrimMemoryLevel
import kotlin.test.Test

/**
 * An unchanged draw reuses the previous platform effect, Compose wrapper and layer assignment.
 * Every changed input must still give the effect an uncached lookup would, so each draw is checked
 * against a lookup that bypasses the slot.
 */
class BlurRenderEffectReuseTest {

  @Test
  fun unchangedDraws_doNoCacheLookupWrapOrAssignment() {
    val h = Harness()

    h.draw()
    assertThat(h.cacheLookups).isEqualTo(1)
    assertThat(h.slot.wraps).isEqualTo(1)
    assertThat(h.slot.assigns).isEqualTo(1)

    h.draw()
    h.draw()
    h.context.inputSnapshot = DifferentCaptureSnapshot
    h.draw()

    assertThat(h.context.inputCaptureCount).isEqualTo(2)
    assertThat(h.slot.lookups).isEqualTo(4)
    assertThat(h.cacheLookups).isEqualTo(1)
    assertThat(h.slot.wraps).isEqualTo(1)
    assertThat(h.slot.assigns).isEqualTo(1)
  }

  @Test
  fun nodeSizeChange_withMask_givesTheEffectForTheNewSize() = assertChange(
    style = maskedStyle(),
    expectNewEffect = true,
  ) { canvasSize = Size(12f, 10f) }

  @Test
  fun nodeSizeChange_withoutMask_reusesTheEffectAndWrapper() = assertChange(
    expectNewEffect = false,
    expectCacheLookup = true,
  ) { canvasSize = Size(12f, 10f) }

  @Test
  fun layerOffsetChange_withMask_givesTheEffectForTheNewOffset() = assertChange(
    style = maskedStyle(),
    expectNewEffect = true,
  ) { context.layerOffset = Offset(1f, 0f) }

  @Test
  fun inputScaleChange_givesTheEffectForTheNewScale() = assertChange(expectNewEffect = true) {
    update(mode = HazePerformanceMode.Performance)
  }

  @Test
  fun densityChange_givesTheEffectForTheNewDensity() = assertChange(expectNewEffect = true) {
    context.renderDensity = Density(2f)
  }

  @Test
  fun fontScaleOnlyChange_hitsTheSlot() = assertChange(expectNewEffect = false, expectCacheLookup = false) {
    context.renderDensity = Density(1f, fontScale = 2f)
  }

  @Test
  fun blurRadiusChange_givesTheNewEffect() = assertChange(expectNewEffect = true) {
    update(style = baseStyle(radius = 16.dp))
  }

  @Test
  fun noiseChange_givesTheNewEffect() = assertChange(expectNewEffect = true) {
    update(style = baseStyle(noise = 0.8f))
  }

  @Test
  fun colorEffectsChange_givesTheNewEffect() = assertChange(expectNewEffect = true) {
    update(style = baseStyle(tints = listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.5f)))))
  }

  @Test
  fun maskChange_givesTheNewEffect() = assertChange(expectNewEffect = true) {
    update(style = maskedStyle())
  }

  @Test
  fun progressiveSet_givesTheNewEffect() = assertChange(expectNewEffect = true) {
    update(style = baseStyle(progressive = HazeProgressive.verticalGradient()))
  }

  @Test
  fun progressiveChanged_givesTheNewEffect() = assertChange(
    style = baseStyle(progressive = HazeProgressive.verticalGradient()),
    expectNewEffect = true,
  ) { update(style = baseStyle(progressive = HazeProgressive.verticalGradient(endIntensity = 0.4f))) }

  @Test
  fun progressiveCleared_givesTheOrdinaryEffect() = assertChange(
    style = baseStyle(progressive = HazeProgressive.verticalGradient()),
    expectNewEffect = true,
  ) { update(style = baseStyle()) }

  @Test
  fun trimMemoryBackground_clearsTheCacheSoTheNextDrawBuildsAFreshEffect() = assertChange(
    expectNewEffect = true,
  ) { effect.onTrimMemory(TrimMemoryLevel.BACKGROUND) }

  @Test
  fun detach_clearsTheCacheSoTheNextDrawBuildsAFreshEffect() = assertChange(
    expectNewEffect = true,
  ) {
    effect.attach(BlurLifecycleScope)
    effect.delegate = delegate
    effect.detach()
  }

  @Test
  fun replacedLayer_isAssignedTheSameEffectWithoutANewWrapper() {
    val h = Harness()
    h.draw()
    val effect = h.slot.platformEffect

    h.context.releaseLatestLayer()
    val before = h.counts()
    val draw = h.draw()

    assertThat(draw.slotEffect).isSameInstanceAs(effect)
    assertThat(draw.counts - before).isEqualTo(Counts(cache = 0, wraps = 0, assigns = 1))
  }

  @Test
  fun backdropEffect_reusesTheEffectUntilTheCacheIsCleared() {
    val effect = BlurVisualEffect()
    val configuration = BlurConfiguration(baseStyle(), HazePerformanceMode.Quality)
    effect.update(BlurLifecycleScope, configuration, HazeSampling.FullResolution)
    val context = RecordingDrawContext(recordedSize = Size(100f, 100f))
    context.render { with(effect) { with(context) { prepareDraw(configuration) } } }

    fun backdrop(): PlatformRenderEffect {
      var backdrop: HazeEffectBackdrop? = null
      context.render { backdrop = with(effect) { with(context) { backdropEffect(configuration) } } }
      return checkNotNull(backdrop).getPlatformEffect()
    }

    val first = backdrop()
    val second = backdrop()
    assertThat(second).isSameInstanceAs(first)
    assertThat(effect.renderEffectCache.hitCount() + effect.renderEffectCache.missCount()).isEqualTo(1)

    effect.onTrimMemory(TrimMemoryLevel.BACKGROUND)

    assertThat(backdrop()).isNotSameInstanceAs(first)
  }

  @Test
  fun unchangedDraw_matchesAFreshDelegatesFirstDraw() {
    for (style in listOf(baseStyle(), baseStyle(progressive = HazeProgressive.verticalGradient()))) {
      val reused = Harness(style)
      repeat(3) { reused.draw() }

      val fresh = Harness(style)
      fresh.draw()

      val actual = checkNotNull(reused.context.output).toPixelMap()
      val expected = checkNotNull(fresh.context.output).toPixelMap()
      for (y in 0 until actual.height) {
        for (x in 0 until actual.width) {
          assertThat(actual[x, y], "$style pixel ($x, $y)").isEqualTo(expected[x, y])
        }
      }
    }
  }

  /**
   * Draws once, applies [change] and draws again, asserting the second draw gave the effect an
   * uncached lookup gives, and reused (or replaced) the wrapper and layer assignment to match.
   */
  private fun assertChange(
    style: HazeBlurStyle = baseStyle(),
    expectNewEffect: Boolean,
    expectCacheLookup: Boolean = true,
    change: Harness.() -> Unit,
  ) {
    val h = Harness(style)
    h.draw()
    val previous = h.slot.platformEffect
    h.change()

    val before = h.counts()
    val draw = h.draw()

    assertThat(draw.slotEffect).isNotNull()
    assertThat(draw.slotEffect).isSameInstanceAs(draw.uncachedEffect)
    val delta = draw.counts - before
    if (expectNewEffect) {
      assertThat(draw.slotEffect).isNotSameInstanceAs(previous)
      assertThat(delta).isEqualTo(Counts(cache = 1, wraps = 1, assigns = 1))
    } else {
      assertThat(draw.slotEffect).isSameInstanceAs(previous)
      assertThat(delta).isEqualTo(Counts(cache = if (expectCacheLookup) 1 else 0, wraps = 0, assigns = 0))
    }
  }

  @Poko
  private class Counts(val cache: Int, val wraps: Int, val assigns: Int) {
    operator fun minus(other: Counts) = Counts(cache - other.cache, wraps - other.wraps, assigns - other.assigns)
  }

  private class Draw(
    val slotEffect: PlatformRenderEffect?,
    val uncachedEffect: PlatformRenderEffect,
    val counts: Counts,
  )

  private class Harness(style: HazeBlurStyle = baseStyle()) {
    private var style = style
    private var mode: HazePerformanceMode = HazePerformanceMode.Quality
    private var uncachedLookups = 0

    val effect = BlurVisualEffect(renderEffectSlotFactory = { CountingBlurRenderEffectSlot() })
    val delegate = RenderEffectBlurVisualEffectDelegate(effect)
    val context = RecordingDrawContext()
    var canvasSize = Size(10f, 10f)

    /** The content layer's slot. */
    val slot: CountingBlurRenderEffectSlot get() = delegate.effectSlot as CountingBlurRenderEffectSlot

    /** Cache lookups made by draws; the comparison lookups in [draw] are not counted. */
    val cacheLookups: Int
      get() = effect.renderEffectCache.hitCount() + effect.renderEffectCache.missCount() - uncachedLookups

    init {
      update()
    }

    fun update(style: HazeBlurStyle = this.style, mode: HazePerformanceMode = this.mode) {
      this.style = style
      this.mode = mode
      effect.update(BlurLifecycleScope, BlurConfiguration(style, mode), HazeSampling.FullResolution)
    }

    fun counts() = Counts(cacheLookups, slot.wraps, slot.assigns)

    fun draw(): Draw {
      var slotEffect: PlatformRenderEffect? = null
      lateinit var uncached: PlatformRenderEffect
      lateinit var counts: Counts
      context.render(canvasSize) {
        with(delegate) { draw(context) }
        slotEffect = slot.platformEffect
        counts = counts()
        uncached = effect.getOrCreateRenderEffect(
          context,
          inputScale = effect.resolveInputScaleFactor(),
          progressive = effect.progressive,
        )
        uncachedLookups++
      }
      return Draw(slotEffect, uncached, counts)
    }
  }
}

private fun baseStyle(
  radius: Dp = 4.dp,
  noise: Float = 0f,
  tints: List<HazeColorEffect> = emptyList(),
  mask: Brush? = null,
  progressive: HazeProgressive? = null,
) = HazeBlurStyle {
  blurRadius(radius)
  noiseFactor(noise)
  colorEffects(tints)
  mask(mask)
  progressive(progressive)
}

private fun maskedStyle() = baseStyle(mask = Brush.verticalGradient(listOf(Color.Black, Color.Transparent)))
