// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.asBrush
import kotlin.test.Test

class BlurProgressiveBrushTest {

  private var factoryCalls = 0
  private val effect = BlurVisualEffect(
    progressiveBrushFactory = {
      factoryCalls++
      it.asBrush()
    },
  )

  private fun update(progressive: HazeProgressive?) {
    effect.update(
      BlurTestLifecycleScope,
      BlurConfiguration(HazeBlurStyle { progressive(progressive) }, HazePerformanceMode.Quality),
      HazeSampling.FullResolution,
    )
  }

  @Test
  fun progressiveBrush_isBuiltOncePerConfiguration() {
    update(HazeProgressive.verticalGradient())

    val first = effect.progressiveBrush
    repeat(4) { assertThat(effect.progressiveBrush).isSameInstanceAs(first) }

    assertThat(factoryCalls).isEqualTo(1)
  }

  @Test
  fun equalProgressiveUpdate_keepsTheBrush() {
    update(HazeProgressive.verticalGradient())
    val first = effect.progressiveBrush

    update(HazeProgressive.verticalGradient())

    assertThat(effect.progressiveBrush).isSameInstanceAs(first)
    assertThat(factoryCalls).isEqualTo(1)
  }

  @Test
  fun changedProgressive_rebuildsTheBrushOnce() {
    update(HazeProgressive.verticalGradient())
    val linear = effect.progressiveBrush

    update(HazeProgressive.verticalGradient(endIntensity = 0.5f))
    val changedIntensity = effect.progressiveBrush
    effect.progressiveBrush

    update(HazeProgressive.RadialGradient())
    val radial = effect.progressiveBrush

    assertThat(changedIntensity).isNotSameInstanceAs(linear)
    assertThat(radial).isNotSameInstanceAs(changedIntensity)
    assertThat(factoryCalls).isEqualTo(3)
  }

  @Test
  fun brushProgressive_usesTheCallerBrush() {
    val brush: Brush = SolidColor(Color.Black)

    update(HazeProgressive.Brush(brush))

    assertThat(effect.progressiveBrush).isSameInstanceAs(brush)
  }

  @Test
  fun noProgressive_hasNoBrushAndNeverCallsTheFactory() {
    update(null)
    assertThat(effect.progressiveBrush).isNull()

    update(HazeProgressive.verticalGradient())
    effect.progressiveBrush
    update(null)

    assertThat(effect.progressiveBrush).isNull()
    assertThat(factoryCalls).isEqualTo(1)
  }
}
