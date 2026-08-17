// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.animation.core.EaseIn
import androidx.compose.ui.geometry.Offset
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import kotlin.test.Test

class HazeProgressiveTest {

  @Test
  fun radialGradient_hasValueBasedEqualityHashCodeAndToString() {
    val gradient = HazeProgressive.RadialGradient(
      center = Offset(12f, 24f),
      centerIntensity = 0.75f,
      radius = 64f,
      radiusIntensity = 0.25f,
    )
    val equalGradient = HazeProgressive.RadialGradient(
      center = Offset(12f, 24f),
      centerIntensity = 0.75f,
      radius = 64f,
      radiusIntensity = 0.25f,
    )

    assertThat(gradient).isEqualTo(equalGradient)
    assertThat(gradient.hashCode()).isEqualTo(equalGradient.hashCode())
    assertThat(gradient).isNotEqualTo(HazeProgressive.RadialGradient(radius = 128f))
    assertThat(gradient.toString()).isEqualTo(
      "RadialGradient(easing=$EaseIn, center=Offset(12.0, 24.0), centerIntensity=0.75, " +
        "radius=64.0, radiusIntensity=0.25)",
    )
  }

  @Test
  fun radialGradient_usesDataClassFloatEquality() {
    assertThat(HazeProgressive.RadialGradient(centerIntensity = Float.NaN))
      .isEqualTo(HazeProgressive.RadialGradient(centerIntensity = Float.NaN))
    assertThat(HazeProgressive.RadialGradient(centerIntensity = -0f))
      .isNotEqualTo(HazeProgressive.RadialGradient(centerIntensity = 0f))
  }
}
