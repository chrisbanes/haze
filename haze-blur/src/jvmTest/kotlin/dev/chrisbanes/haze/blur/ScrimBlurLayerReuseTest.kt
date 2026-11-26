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
import kotlin.test.Test

/** The Scrim fallback draws masked and faded tints without an intermediate graphics layer. */
class ScrimBlurLayerReuseTest {

  private val verticalMask = Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
  private val brushTint = HazeColorEffect.tint(Brush.verticalGradient(listOf(Color.Red, Color.Blue)))
  private val colorTint = HazeColorEffect.tint(Color.Red.copy(alpha = 0.5f))
  private val colorFilterTint = HazeColorEffect.colorFilter(ColorFilter.tint(Color.Red.copy(alpha = 0.5f)))

  @Test
  fun draw_alphaAndMaskedBrushTint_createsNoLayer() = assertCreatesNoLayer(brushTint)

  @Test
  fun draw_alphaAndMaskedColorTint_createsNoLayer() = assertCreatesNoLayer(colorTint)

  @Test
  fun draw_alphaAndMaskedColorFilterTint_createsNoLayer() = assertCreatesNoLayer(colorFilterTint)

  @Test
  fun draw_unmaskedTint_withAlpha_createsNoLayer() {
    val h = Harness(alpha = 0.5f, mask = null, tint = colorTint)

    repeat(5) { h.draw() }

    assertThat(h.context.createdLayers).isEqualTo(0)
    assertThat(h.context.releasedLayers).isEqualTo(0)
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

  private fun assertCreatesNoLayer(tint: HazeColorEffect) {
    val h = Harness(alpha = 0.5f, mask = verticalMask, tint = tint)

    repeat(5) { h.draw() }

    assertThat(h.context.createdLayers).isEqualTo(0)
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

  private class Harness(alpha: Float, mask: Brush?, tint: HazeColorEffect) {
    val effect = BlurVisualEffect()
    val delegate = ScrimBlurVisualEffectDelegate(effect)
    val context = RecordingDrawContext()

    init {
      effect.delegate = delegate
      update(alpha, mask, tint)
    }

    fun update(alpha: Float, mask: Brush?, tint: HazeColorEffect) {
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
