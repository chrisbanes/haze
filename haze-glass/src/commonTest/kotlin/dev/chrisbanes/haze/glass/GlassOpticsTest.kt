// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.messageContains
import kotlin.test.Test

class GlassOpticsTest {

  @Test
  fun adaptive_isTheDefaultOptics() {
    assertThat(GlassDefaults.optics).isEqualTo(GlassOptics.Adaptive)
    assertThat(resolveGlassStyleValues(GlassStyle, GlassDefaults.style).optics)
      .isEqualTo(GlassOptics.Adaptive)
  }

  @Test
  fun fixed_rejectsInvalidSemanticValues() {
    assertInvalidFixedFraction("refractionStrength") {
      GlassOptics.Fixed(refractionStrength = it)
    }
    assertInvalidFixedFraction("refractionHeightFraction") {
      GlassOptics.Fixed(refractionHeightFraction = it)
    }
    assertInvalidFixedFraction("depth") {
      GlassOptics.Fixed(depth = it)
    }
    assertInvalidFixedDistance("refractionDisplacement") {
      GlassOptics.Fixed(refractionDisplacement = it)
    }
    assertInvalidFixedDistance("blurRadius") {
      GlassOptics.Fixed(blurRadius = it)
    }
  }

  @Test
  fun fixed_acceptsBoundariesAndLargeRendererIndependentValues() {
    val minimum = GlassOptics.Fixed(
      refractionStrength = 0f,
      refractionHeightFraction = 0f,
      refractionDisplacement = 0.dp,
      depth = 0f,
      blurRadius = 0.dp,
    )
    val maximum = GlassOptics.Fixed(
      refractionStrength = 1f,
      refractionHeightFraction = 1f,
      refractionDisplacement = Float.MAX_VALUE.dp,
      depth = 1f,
      blurRadius = Float.MAX_VALUE.dp,
    )

    assertThat(minimum.refractionStrength).isEqualTo(0f)
    assertThat(minimum.refractionHeightFraction).isEqualTo(0f)
    assertThat(minimum.refractionDisplacement).isEqualTo(0.dp)
    assertThat(minimum.depth).isEqualTo(0f)
    assertThat(minimum.blurRadius).isEqualTo(0.dp)
    assertThat(minimum.progressive).isNull()
    assertThat(maximum.refractionStrength).isEqualTo(1f)
    assertThat(maximum.refractionHeightFraction).isEqualTo(1f)
    assertThat(maximum.refractionDisplacement).isEqualTo(Float.MAX_VALUE.dp)
    assertThat(maximum.depth).isEqualTo(1f)
    assertThat(maximum.blurRadius).isEqualTo(Float.MAX_VALUE.dp)
    assertThat(maximum.progressive).isNull()
  }
}

private fun assertInvalidFixedFraction(property: String, create: (Float) -> Unit) {
  listOf(Float.NaN, Float.NEGATIVE_INFINITY, -0.1f, 1.1f, Float.POSITIVE_INFINITY)
    .forEach { invalid ->
      val failure = assertFailure { create(invalid) }
      failure.isInstanceOf<IllegalArgumentException>()
      failure.messageContains(property)
      failure.messageContains("0f..1f")
    }
}

private fun assertInvalidFixedDistance(property: String, create: (Dp) -> Unit) {
  listOf(
    Dp.Unspecified,
    Float.NaN.dp,
    Float.NEGATIVE_INFINITY.dp,
    (-1).dp,
    Float.POSITIVE_INFINITY.dp,
  ).forEach { invalid ->
    val failure = assertFailure { create(invalid) }
    failure.isInstanceOf<IllegalArgumentException>()
    failure.messageContains(property)
    failure.messageContains("specified, finite, and non-negative")
  }
}
