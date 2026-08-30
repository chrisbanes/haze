// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import kotlin.test.Test

class GlassOpticsTest {

  @Test
  fun default_isSizeAwareOptics() {
    assertThat(GlassDefaults.optics.blurRadius)
      .isInstanceOf<OpticalSizeValue.Responsive<*>>()
    assertThat(GlassDefaults.optics.depth)
      .isInstanceOf<OpticalSizeValue.Responsive<*>>()
    assertThat(resolveGlassStyleValues(GlassStyle, GlassStyle).optics)
      .isEqualTo(GlassDefaults.optics)
  }

  @Test
  fun fixed_rejectsInvalidSemanticValues() {
    assertInvalidFixedFraction("refractionStrength") {
      GlassOptics(refractionStrength = it)
    }
    assertInvalidFixedFraction("refractionFoldStrength") {
      GlassOptics(refractionFoldStrength = it)
    }
    assertInvalidFixedFraction("refractionHeightFraction") {
      GlassOptics(refractionHeightFraction = it)
    }
    assertInvalidFixedFraction("refractionDetailIntensity") {
      GlassOptics(refractionDetailIntensity = it)
    }
    assertInvalidFixedFraction("depth") {
      GlassOptics(depth = OpticalSizeValue.Fixed(it))
    }
    assertInvalidFixedDistance("refractionDisplacement") {
      GlassOptics(refractionDisplacement = it)
    }
    assertInvalidFixedDistance("blurRadius") {
      GlassOptics(blurRadius = OpticalSizeValue.Fixed(it))
    }
  }

  @Test
  fun fixed_acceptsBoundariesAndLargeRendererIndependentValues() {
    val minimum = GlassOptics(
      refractionStrength = 0f,
      refractionFoldStrength = 0f,
      refractionHeightFraction = 0f,
      refractionDisplacement = 0.dp,
      depth = OpticalSizeValue.Fixed(0f),
      blurRadius = OpticalSizeValue.Fixed(0.dp),
    )
    val maximum = GlassOptics(
      refractionStrength = 1f,
      refractionFoldStrength = 1f,
      refractionHeightFraction = 1f,
      refractionDisplacement = Float.MAX_VALUE.dp,
      depth = OpticalSizeValue.Fixed(1f),
      blurRadius = OpticalSizeValue.Fixed(Float.MAX_VALUE.dp),
    )

    assertThat(minimum.refractionStrength).isEqualTo(0f)
    assertThat(minimum.refractionFoldStrength).isEqualTo(0f)
    assertThat(minimum.refractionHeightFraction).isEqualTo(0f)
    assertThat(minimum.refractionDisplacement).isEqualTo(0.dp)
    assertThat(minimum.depth).isEqualTo(OpticalSizeValue.Fixed(0f))
    assertThat(minimum.blurRadius).isEqualTo(OpticalSizeValue.Fixed(0.dp))
    assertThat(minimum.progressive).isNull()
    assertThat(maximum.refractionStrength).isEqualTo(1f)
    assertThat(maximum.refractionFoldStrength).isEqualTo(1f)
    assertThat(maximum.refractionHeightFraction).isEqualTo(1f)
    assertThat(maximum.refractionDisplacement).isEqualTo(Float.MAX_VALUE.dp)
    assertThat(maximum.depth).isEqualTo(OpticalSizeValue.Fixed(1f))
    assertThat(maximum.blurRadius).isEqualTo(OpticalSizeValue.Fixed(Float.MAX_VALUE.dp))
    assertThat(maximum.progressive).isNull()
  }

  @Test
  fun responsive_snapshotsOrderedPoints() {
    val points = mutableListOf(
      OpticalSizePoint(64.dp, 1f),
      OpticalSizePoint(176.dp, 2f),
    )
    val value = OpticalSizeValue.Responsive(points)
    points[0] = OpticalSizePoint(64.dp, 99f)

    assertThat(value.points).isEqualTo(
      listOf(
        OpticalSizePoint(64.dp, 1f),
        OpticalSizePoint(176.dp, 2f),
      ),
    )
  }

  @Test
  fun responsive_rejectsTooFewOrUnorderedPoints() {
    assertFailure {
      OpticalSizeValue.Responsive(OpticalSizePoint(64.dp, 1f))
    }.isInstanceOf<IllegalArgumentException>()
    assertFailure {
      OpticalSizeValue.Responsive(
        OpticalSizePoint(176.dp, 1f),
        OpticalSizePoint(64.dp, 2f),
      )
    }.isInstanceOf<IllegalArgumentException>()
    assertFailure {
      OpticalSizeValue.Responsive(
        OpticalSizePoint(64.dp, 1f),
        OpticalSizePoint(64.dp, 2f),
      )
    }.isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun sizePoint_rejectsInvalidDimensions() {
    listOf(
      Dp.Unspecified,
      Float.NaN.dp,
      Float.POSITIVE_INFINITY.dp,
      Float.NEGATIVE_INFINITY.dp,
      0.dp,
      (-1).dp,
    )
      .forEach { dimension ->
        assertFailure { OpticalSizePoint(dimension, 1f) }
          .isInstanceOf<IllegalArgumentException>()
      }
  }

  @Test
  fun responsiveOptics_rejectInvalidContainedValues() {
    val points = listOf(
      OpticalSizePoint(64.dp, 0f),
      OpticalSizePoint(176.dp, 1f),
    )
    assertFailure {
      GlassOptics(
        depth = OpticalSizeValue.Responsive(points.map { it.copy(value = Float.NaN) }),
      )
    }.hasMessage("depth must be finite and in 0f..1f")
    assertFailure {
      GlassOptics(
        blurRadius = OpticalSizeValue.Responsive(
          points.map { OpticalSizePoint(it.shortestDimension, Float.NaN.dp) },
        ),
      )
    }.hasMessage("blurRadius must be specified, finite, and non-negative")
  }
}

private fun assertInvalidFixedFraction(property: String, create: (Float) -> Unit) {
  listOf(Float.NaN, Float.NEGATIVE_INFINITY, -0.1f, 1.1f, Float.POSITIVE_INFINITY)
    .forEach { invalid ->
      val failure = assertFailure { create(invalid) }
      failure.isInstanceOf<IllegalArgumentException>()
      failure.hasMessage("$property must be finite and in 0f..1f")
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
    failure.hasMessage("$property must be specified, finite, and non-negative")
  }
}
