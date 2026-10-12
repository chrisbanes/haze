// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toPixelMap
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.TrimMemoryLevel
import kotlin.test.Test

/** The Scrim fallback keeps its alpha and masked-tint layers across draws. */
class ScrimBlurLayerReuseTest {

  private val verticalMask = Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
  private val brushTint = HazeColorEffect.tint(Brush.verticalGradient(listOf(Color.Red, Color.Blue)))
  private val colorTint = HazeColorEffect.tint(Color.Red.copy(alpha = 0.5f))
  private val colorFilterTint = HazeColorEffect.colorFilter(ColorFilter.tint(Color.Red.copy(alpha = 0.5f)))

  @Test
  fun draw_alphaAndMaskedBrushTint_createsEachLayerOnce() = assertEachLayerCreatedOnce(brushTint)

  @Test
  fun draw_alphaAndMaskedColorTint_createsEachLayerOnce() = assertEachLayerCreatedOnce(colorTint)

  @Test
  fun draw_alphaAndMaskedColorFilterTint_createsEachLayerOnce() = assertEachLayerCreatedOnce(colorFilterTint)

  @Test
  fun draw_unmaskedTint_withAlpha_createsOnlyTheAlphaLayer() {
    val h = Harness(alpha = 0.5f, mask = null, tint = colorTint)

    repeat(5) { h.draw() }

    assertThat(h.context.createdLayers).isEqualTo(1)
    assertThat(h.context.releasedLayers).isEqualTo(0)
  }

  @Test
  fun draw_opaqueAlpha_releasesTheAlphaLayer() {
    val h = Harness(alpha = 0.5f, mask = null, tint = colorTint)
    h.draw()

    h.update(alpha = 1f, mask = null, tint = colorTint)
    h.draw()
    h.draw()

    assertThat(h.context.createdLayers).isEqualTo(1)
    assertThat(h.context.releasedLayers).isEqualTo(1)

    h.update(alpha = 0.5f, mask = null, tint = colorTint)
    h.draw()

    assertThat(h.context.createdLayers).isEqualTo(2)
  }

  @Test
  fun draw_maskRemoved_releasesTheTintLayer() {
    val h = Harness(alpha = 1f, mask = verticalMask, tint = colorTint)
    h.draw()
    assertThat(h.context.createdLayers).isEqualTo(1)

    h.update(alpha = 1f, mask = null, tint = colorTint)
    h.draw()

    assertThat(h.context.releasedLayers).isEqualTo(1)
  }

  @Test
  fun draw_noTint_releasesBothLayers() {
    val h = Harness(alpha = 0.5f, mask = verticalMask, tint = colorTint)
    h.draw()

    h.update(alpha = 0.5f, mask = verticalMask, tint = null)
    h.draw()

    assertThat(h.context.releasedLayers).isEqualTo(2)
  }

  @Test
  fun detach_releasesBothLayers() {
    val h = Harness(alpha = 0.5f, mask = verticalMask, tint = colorTint)
    h.draw()

    h.delegate.detach()

    assertThat(h.context.releasedLayers).isEqualTo(2)
  }

  @Test
  fun clearRetainedOutput_throughBlurVisualEffect_releasesBothLayers() {
    val h = Harness(alpha = 0.5f, mask = verticalMask, tint = colorTint)
    h.draw()

    h.effect.clearRetainedOutput()

    assertThat(h.context.releasedLayers).isEqualTo(2)
    h.draw()
    assertThat(h.context.createdLayers).isEqualTo(4)
  }

  @Test
  fun invalidateRetainedOutput_throughBlurVisualEffect_keepsTheLayers() {
    val h = Harness(alpha = 0.5f, mask = verticalMask, tint = colorTint)
    h.draw()

    h.effect.invalidateRetainedOutput()
    h.draw()

    assertThat(h.context.releasedLayers).isEqualTo(0)
    assertThat(h.context.createdLayers).isEqualTo(2)
  }

  @Test
  fun onTrimMemory_background_keepsTheLayers() {
    val h = Harness(alpha = 0.5f, mask = verticalMask, tint = colorTint)
    h.draw()

    h.delegate.onTrimMemory(RecordingLifecycleScope(), TrimMemoryLevel.BACKGROUND)
    h.draw()

    assertThat(h.context.releasedLayers).isEqualTo(0)
    assertThat(h.context.createdLayers).isEqualTo(2)
  }

  @Test
  fun onTrimMemory_moderate_releasesTheLayersAndTheNextDrawRecreatesThem() {
    val h = Harness(alpha = 0.5f, mask = verticalMask, tint = colorTint)
    h.draw()

    h.delegate.onTrimMemory(RecordingLifecycleScope(), TrimMemoryLevel.MODERATE)
    assertThat(h.context.releasedLayers).isEqualTo(2)

    h.draw()
    assertThat(h.context.createdLayers).isEqualTo(4)
  }

  @Test
  fun draw_afterAlphaAndMaskChanges_matchesAFreshDelegate() {
    val reused = Harness(alpha = 0.5f, mask = verticalMask, tint = brushTint)
    reused.draw()
    reused.update(alpha = 0.25f, mask = null, tint = brushTint)
    reused.draw()

    val fresh = Harness(alpha = 0.25f, mask = null, tint = brushTint)
    fresh.draw()

    assertSamePixels(reused, fresh)
  }

  @Test
  fun draw_afterMaskIsReAdded_matchesAFreshDelegate() {
    val reused = Harness(alpha = 1f, mask = verticalMask, tint = colorFilterTint)
    reused.draw()
    reused.update(alpha = 0.5f, mask = null, tint = colorFilterTint)
    reused.draw()
    reused.update(alpha = 0.5f, mask = verticalMask, tint = colorFilterTint)
    reused.draw()

    val fresh = Harness(alpha = 0.5f, mask = verticalMask, tint = colorFilterTint)
    fresh.draw()

    assertSamePixels(reused, fresh)
  }

  private fun assertEachLayerCreatedOnce(tint: HazeColorEffect) {
    val h = Harness(alpha = 0.5f, mask = verticalMask, tint = tint)

    repeat(5) { h.draw() }

    assertThat(h.context.createdLayers).isEqualTo(2)
    assertThat(h.context.releasedLayers).isEqualTo(0)
  }

  private fun assertSamePixels(actual: Harness, expected: Harness) {
    val a = checkNotNull(actual.context.output).toPixelMap()
    val e = checkNotNull(expected.context.output).toPixelMap()
    for (y in 0 until e.height) {
      for (x in 0 until e.width) {
        assertThat(a[x, y]).isEqualTo(e[x, y])
      }
    }
  }

  private class Harness(alpha: Float, mask: Brush?, tint: HazeColorEffect?) {
    val effect = BlurVisualEffect()
    val delegate = ScrimBlurVisualEffectDelegate(effect)
    val context = RecordingDrawContext()

    init {
      effect.delegate = delegate
      update(alpha, mask, tint)
    }

    fun update(alpha: Float, mask: Brush?, tint: HazeColorEffect?) {
      effect.update(
        BlurLifecycleScope,
        BlurConfiguration(
          HazeBlurStyle {
            alpha(alpha)
            mask(mask)
            fallbackColorEffect(tint)
          },
          HazePerformanceMode.Quality,
        ),
        HazeSampling.FullResolution,
      )
    }

    fun draw() = context.render { with(delegate) { draw(context) } }
  }
}
