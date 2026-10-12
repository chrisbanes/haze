// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.asBrush
import kotlin.test.Test

class ScrimBlurProgressiveBrushTest {

  @Test
  fun draw_buildsTheProgressiveBrushOncePerConfiguration() {
    var factoryCalls = 0
    val effect = BlurVisualEffect(
      progressiveBrushFactory = {
        factoryCalls++
        it.asBrush()
      },
    )
    fun update(progressive: HazeProgressive) = effect.update(
      BlurLifecycleScope,
      BlurConfiguration(
        HazeBlurStyle {
          colorEffects(listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.5f))))
          progressive(progressive)
        },
        HazePerformanceMode.Quality,
      ),
      HazeSampling.FullResolution,
    )
    val delegate = ScrimBlurVisualEffectDelegate(effect)
    val context = RecordingDrawContext()
    update(HazeProgressive.verticalGradient())

    repeat(3) { context.render { with(delegate) { draw(context) } } }
    assertThat(factoryCalls).isEqualTo(1)

    update(HazeProgressive.verticalGradient(endIntensity = 0.5f))
    repeat(3) { context.render { with(delegate) { draw(context) } } }

    assertThat(factoryCalls).isEqualTo(2)
  }
}
