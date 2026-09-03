// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.animation.core.EaseIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

@OptIn(InternalHazeApi::class)
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
  fun radialGradient_usesDataClassFloatEqualityForSignedZero() {
    assertThat(HazeProgressive.RadialGradient(centerIntensity = -0f))
      .isNotEqualTo(HazeProgressive.RadialGradient(centerIntensity = 0f))
  }

  @Test
  fun generatedGradients_retainValidIntensities() {
    listOf(0f, 0.5f, 1f).forEach { intensity ->
      assertThat(HazeProgressive.LinearGradient(startIntensity = intensity).startIntensity)
        .isEqualTo(intensity)
      assertThat(HazeProgressive.LinearGradient(endIntensity = intensity).endIntensity)
        .isEqualTo(intensity)
      assertThat(HazeProgressive.RadialGradient(centerIntensity = intensity).centerIntensity)
        .isEqualTo(intensity)
      assertThat(HazeProgressive.RadialGradient(radiusIntensity = intensity).radiusIntensity)
        .isEqualTo(intensity)
    }
  }

  @Test
  fun linearGradient_rejectsInvalidIntensities() {
    assertInvalidIntensity("startIntensity") {
      HazeProgressive.LinearGradient(startIntensity = it)
    }
    assertInvalidIntensity("endIntensity") {
      HazeProgressive.LinearGradient(endIntensity = it)
    }
  }

  @Test
  fun radialGradient_rejectsInvalidIntensities() {
    assertInvalidIntensity("centerIntensity") {
      HazeProgressive.RadialGradient(centerIntensity = it)
    }
    assertInvalidIntensity("radiusIntensity") {
      HazeProgressive.RadialGradient(radiusIntensity = it)
    }
  }

  @Test
  fun generatedGradients_rejectStopCountsBelowTwo() {
    listOf(
      HazeProgressive.LinearGradient(),
      HazeProgressive.RadialGradient(),
    ).forEach { progressive ->
      listOf(-1, 0, 1).forEach { numStops ->
        val failure = assertFailure { progressive.asBrush(numStops) }
        failure.isInstanceOf<IllegalArgumentException>()
        failure.hasMessage("numStops must be at least 2")
      }
    }
  }

  @Test
  fun brush_ignoresGeneratedGradientStopValidation() {
    val brush = SolidColor(Color.Black)

    assertThat(HazeProgressive.Brush(brush).asBrush(numStops = 0)).isSameInstanceAs(brush)
  }
}

private fun assertInvalidIntensity(property: String, create: (Float) -> Unit) {
  listOf(
    -0.1f,
    Float.NaN,
    Float.NEGATIVE_INFINITY,
    Float.POSITIVE_INFINITY,
    1.1f,
  ).forEach { intensity ->
    val failure = assertFailure { create(intensity) }
    failure.isInstanceOf<IllegalArgumentException>()
    failure.hasMessage("$property must be finite and in 0f..1f")
  }
}
