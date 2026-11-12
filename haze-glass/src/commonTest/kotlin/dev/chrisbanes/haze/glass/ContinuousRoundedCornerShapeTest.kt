// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Interpolatable
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import kotlin.test.Test

class ContinuousRoundedCornerShapeTest {

  @Test
  fun factories_resolveCornerSizesWithFullSmoothingByDefault() {
    val all = CornerSize(16.dp)
    assertThat(ContinuousRoundedCornerShape(16.dp)).isEqualTo(ContinuousRoundedCornerShape(all, all, all, all, 1f))
    assertThat(ContinuousRoundedCornerShape(all)).isEqualTo(ContinuousRoundedCornerShape(all, all, all, all, 1f))
    assertThat(ContinuousRoundedCornerShape(16f).topStart).isEqualTo(CornerSize(16f))
    assertThat(ContinuousRoundedCornerShape(percent = 50).topStart).isEqualTo(CornerSize(50))
    assertThat(ContinuousRoundedCornerShape(16.dp, smoothing = 0.6f).smoothing).isEqualTo(0.6f)

    val dp = ContinuousRoundedCornerShape(topStart = 1.dp, topEnd = 2.dp, bottomEnd = 3.dp, bottomStart = 4.dp)
    assertThat(dp).isEqualTo(
      ContinuousRoundedCornerShape(CornerSize(1.dp), CornerSize(2.dp), CornerSize(3.dp), CornerSize(4.dp)),
    )
    val px = ContinuousRoundedCornerShape(topStart = 1f, topEnd = 2f, bottomEnd = 3f, bottomStart = 4f, smoothing = 0.5f)
    assertThat(px).isEqualTo(
      ContinuousRoundedCornerShape(CornerSize(1f), CornerSize(2f), CornerSize(3f), CornerSize(4f), 0.5f),
    )
    val percent = ContinuousRoundedCornerShape(topStartPercent = 1, topEndPercent = 2, bottomEndPercent = 3, bottomStartPercent = 4)
    assertThat(percent).isEqualTo(
      ContinuousRoundedCornerShape(CornerSize(1), CornerSize(2), CornerSize(3), CornerSize(4)),
    )
  }

  @Test
  fun copy_preservesOtherValues() {
    val shape = ContinuousRoundedCornerShape(16.dp, smoothing = 0.6f)

    assertThat(shape.copy()).isEqualTo(shape)
    assertThat(shape.copy(topStart = CornerSize(4.dp))).isEqualTo(
      ContinuousRoundedCornerShape(CornerSize(4.dp), CornerSize(16.dp), CornerSize(16.dp), CornerSize(16.dp), 0.6f),
    )
    val all: CornerBasedShape = shape.copy(CornerSize(8.dp))
    assertThat(all).isEqualTo(ContinuousRoundedCornerShape(8.dp, smoothing = 0.6f))
    assertThat(shape.copy(smoothing = 0.2f)).isEqualTo(ContinuousRoundedCornerShape(16.dp, smoothing = 0.2f))
  }

  @Test
  fun smoothing_rejectsNonFiniteAndCoercesIntoRange() {
    assertFailure { ContinuousRoundedCornerShape(16.dp, smoothing = Float.NaN) }
      .isInstanceOf<IllegalArgumentException>()
    assertFailure { ContinuousRoundedCornerShape(16.dp, smoothing = Float.POSITIVE_INFINITY) }
      .isInstanceOf<IllegalArgumentException>()
    assertThat(ContinuousRoundedCornerShape(16.dp, smoothing = 1.5f).smoothing).isEqualTo(1f)
    assertThat(ContinuousRoundedCornerShape(16.dp, smoothing = -0.5f).smoothing).isEqualTo(0f)
  }

  @Test
  fun equality_coversSmoothingAndShapeType() {
    val shape = ContinuousRoundedCornerShape(16.dp)
    assertThat(shape).isEqualTo(ContinuousRoundedCornerShape(16.dp))
    assertThat(shape.hashCode()).isEqualTo(ContinuousRoundedCornerShape(16.dp).hashCode())
    assertThat(shape).isNotEqualTo(ContinuousRoundedCornerShape(16.dp, smoothing = 0.5f))
    assertThat(shape.hashCode()).isNotEqualTo(ContinuousRoundedCornerShape(16.dp, smoothing = 0.5f).hashCode())
    assertThat<Any>(ContinuousRoundedCornerShape(16.dp, smoothing = 0f)).isNotEqualTo(RoundedCornerShape(16.dp))
  }

  @Test
  fun smoothing_negativeZeroEqualsAndHashesLikeZero() {
    val negative = ContinuousRoundedCornerShape(16.dp, smoothing = -0f)
    val zero = ContinuousRoundedCornerShape(16.dp, smoothing = 0f)

    assertThat(negative).isEqualTo(zero)
    assertThat(negative.hashCode()).isEqualTo(zero.hashCode())
  }

  @Test
  fun createOutline_zeroSmoothingMatchesRoundedCornerShape() {
    val size = Size(200f, 100f)
    val density = Density(1f)
    assertThat(ContinuousRoundedCornerShape(16.dp, smoothing = 0f).createOutline(size, LayoutDirection.Ltr, density))
      .isEqualTo(RoundedCornerShape(16.dp).createOutline(size, LayoutDirection.Ltr, density))
  }

  @Test
  fun createOutline_capsuleIsRounded() {
    val outline = ContinuousRoundedCornerShape(percent = 50).createOutline(Size(200f, 80f), LayoutDirection.Ltr, Density(1f))

    assertThat(outline).isInstanceOf<Outline.Rounded>()
  }

  @Test
  fun lerp_mixedTypesUseComposeFallbackInBothOrders() {
    val continuous = ContinuousRoundedCornerShape(12f)
    val unsupported = object : Shape {
      override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        RectangleShape.createOutline(size, layoutDirection, density)
    }
    for (other in listOf(RoundedCornerShape(24.dp), RectangleShape, unsupported)) {
      assertThat(continuous.lerp(other, 0.5f)).isNull()
      for (fraction in listOf(0f, 0.49f, 0.5f, 1f)) {
        assertThat(Interpolatable.lerp(continuous, other, fraction))
          .isSameInstanceAs(if (fraction < 0.5f) continuous else other)
        assertThat(Interpolatable.lerp(other, continuous, fraction))
          .isSameInstanceAs(if (fraction < 0.5f) other else continuous)
      }
    }
    assertThat(continuous.lerp(null, 0.5f)).isNull()
  }

  @Test
  fun lerp_deferredMixedCornersResolveAtEachSizeAndDensity() {
    val custom = object : CornerSize {
      override fun toPx(shapeSize: Size, density: Density): Float = shapeSize.width / 40f + density.density
    }
    val start = ContinuousRoundedCornerShape(CornerSize(4.dp), CornerSize(8f), CornerSize(10), custom, 0.2f)
    val end = ContinuousRoundedCornerShape(CornerSize(12f), CornerSize(10), custom, CornerSize(8.dp), 0.8f)
    assertThat(Interpolatable.lerp(start, end, 0f)).isSameInstanceAs(start)
    assertThat(Interpolatable.lerp(start, end, 1f)).isSameInstanceAs(end)
    val result = Interpolatable.lerp(start, end, 0.5f) as ContinuousRoundedCornerShape
    assertThat(result.smoothing).isEqualTo(0.5f)
    val repeated = Interpolatable.lerp(start, end, 0.5f) as ContinuousRoundedCornerShape
    assertThat(result).isEqualTo(repeated)
    assertThat(result.hashCode()).isEqualTo(repeated.hashCode())
    assertThat(result.copy()).isEqualTo(result)
    assertThat(result.copy().hashCode()).isEqualTo(result.hashCode())
    val equal = start.copy()
    assertThat(Interpolatable.lerp(start, equal, Float.MAX_VALUE)).isEqualTo(start)
    for ((size, density) in listOf(Size(240f, 120f) to Density(1f), Size(360f, 180f) to Density(2f))) {
      val starts = listOf(start.topStart, start.topEnd, start.bottomEnd, start.bottomStart)
      val ends = listOf(end.topStart, end.topEnd, end.bottomEnd, end.bottomStart)
      val corners = listOf(result.topStart, result.topEnd, result.bottomEnd, result.bottomStart)
      val expected = starts.zip(ends).map { (a, b) -> (a.toPx(size, density) + b.toPx(size, density)) / 2f }
      assertThat(corners.map { it.toPx(size, density) }).isEqualTo(expected)
      val ltr = result.resolveCornerRadiiPx(size, density, LayoutDirection.Ltr)
      val rtl = result.resolveCornerRadiiPx(size, density, LayoutDirection.Rtl)
      assertThat(listOf(ltr.topLeft, ltr.topRight, ltr.bottomRight, ltr.bottomLeft)).isEqualTo(expected)
      assertThat(listOf(rtl.topLeft, rtl.topRight, rtl.bottomRight, rtl.bottomLeft))
        .isEqualTo(listOf(expected[1], expected[0], expected[3], expected[2]))
      assertThat(ltr).isNotEqualTo(start.resolveCornerRadiiPx(size, density, LayoutDirection.Ltr))
    }
  }

  @Test
  fun lerp_extremeFiniteGrowthKeepsResolvedGlassCornersFinite() {
    val start = ContinuousRoundedCornerShape(10f, smoothing = 0.2f)
    val end = ContinuousRoundedCornerShape(30f, smoothing = 0.8f)
    val size = Size(240f, 120f)
    val density = Density(1f)
    for ((from, to, fraction) in listOf(
      Triple(start, end, Float.MAX_VALUE),
      Triple(end, start, -Float.MAX_VALUE),
      Triple(start, end, Float.MAX_VALUE / 30f),
    )) {
      val result = Interpolatable.lerp(from, to, fraction) as ContinuousRoundedCornerShape
      for (corner in listOf(result.topStart, result.topEnd, result.bottomEnd, result.bottomStart)) {
        assertThat(corner.toPx(size, density).isFinite()).isTrue()
      }
      for (direction in LayoutDirection.entries) {
        val radii = result.resolveCornerRadiiPx(size, density, direction)
        for (radius in listOf(radii.topLeft, radii.topRight, radii.bottomRight, radii.bottomLeft)) {
          assertThat(radius).isCloseTo(60f, 0.001f)
        }
      }
    }
  }

  @Test
  fun lerp_equalLargeCornerAndGrowingNeighbourKeepNonZeroGlassRadii() {
    val large = Float.MAX_VALUE * 0.75f
    val start = ContinuousRoundedCornerShape(large, 0f, 0f, 0f, 0f)
    val end = ContinuousRoundedCornerShape(large, 0f, 0f, 10f, 0f)
    val size = Size(240f, 120f)
    val density = Density(1f)
    assertThat(Interpolatable.lerp(start, end, 0f)).isSameInstanceAs(start)
    assertThat(Interpolatable.lerp(start, end, 1f)).isSameInstanceAs(end)
    for ((from, to, fraction) in listOf(Triple(start, end, Float.MAX_VALUE), Triple(end, start, -Float.MAX_VALUE))) {
      val result = Interpolatable.lerp(from, to, fraction) as ContinuousRoundedCornerShape
      for (direction in LayoutDirection.entries) {
        val radii = result.resolveCornerRadiiPx(size, density, direction)
        val expected = if (direction == LayoutDirection.Ltr) listOf(60f, 0f, 0f, 60f) else listOf(0f, 60f, 60f, 0f)
        for ((actual, radius) in listOf(radii.topLeft, radii.topRight, radii.bottomRight, radii.bottomLeft).zip(expected)) {
          assertThat(actual).isCloseTo(radius, 0.001f)
        }
      }
    }
    val smoothStart = ContinuousRoundedCornerShape(16f, smoothing = 0.2f)
    val smoothEnd = ContinuousRoundedCornerShape(16f, smoothing = 0.8f)
    val saturated = Interpolatable.lerp(smoothStart, smoothEnd, 2f) as ContinuousRoundedCornerShape
    val equivalent = Interpolatable.lerp(smoothStart, smoothEnd, 3f) as ContinuousRoundedCornerShape
    assertThat(saturated).isEqualTo(equivalent)
    assertThat(saturated.hashCode()).isEqualTo(equivalent.hashCode())
  }

  @Test
  fun lerp_finiteOvershootClampsCornersAndSmoothing() {
    val start = ContinuousRoundedCornerShape(10f, smoothing = 0.2f)
    val end = ContinuousRoundedCornerShape(30f, smoothing = 0.8f)
    val size = Size(240f, 120f)
    val density = Density(1f)
    val before = Interpolatable.lerp(start, end, -0.5f) as ContinuousRoundedCornerShape
    val after = Interpolatable.lerp(start, end, 1.5f) as ContinuousRoundedCornerShape
    assertThat(before.topStart.toPx(size, density)).isEqualTo(0f)
    assertThat(after.topStart.toPx(size, density)).isEqualTo(40f)
    assertThat(before.smoothing).isEqualTo(0f)
    assertThat(after.smoothing).isEqualTo(1f)
    before.createOutline(size, LayoutDirection.Ltr, density)
    val extreme = Interpolatable.lerp(end, start, Float.MAX_VALUE) as ContinuousRoundedCornerShape
    assertThat(extreme.smoothing).isEqualTo(0f)
    extreme.createOutline(size, LayoutDirection.Ltr, density)
    val zero = Interpolatable.lerp(ContinuousRoundedCornerShape(1f, smoothing = -0f), start, -0.5f) as ContinuousRoundedCornerShape
    assertThat(zero.smoothing.toBits()).isEqualTo(0f.toBits())
    for (invalidRadius in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
      val invalid = ContinuousRoundedCornerShape(CornerSize(invalidRadius))
      for ((from, to) in listOf(invalid to start, start to invalid)) {
        val invalidMidpoint = Interpolatable.lerp(from, to, 0.5f) as ContinuousRoundedCornerShape
        assertThat(invalidMidpoint.resolveCornerRadiiPx(size, density, LayoutDirection.Ltr))
          .isEqualTo(GlassDefaults.continuousShape.resolveCornerRadiiPx(size, density, LayoutDirection.Ltr))
      }
      val invalidCorner = CornerSize(invalidRadius)
      val staticStart = ContinuousRoundedCornerShape(invalidCorner, CornerSize(0f), CornerSize(0f), CornerSize(0f), 0f)
      val staticEnd = staticStart.copy(bottomStart = CornerSize(10f))
      val staticMidpoint = Interpolatable.lerp(staticStart, staticEnd, 0.5f) as ContinuousRoundedCornerShape
      assertThat(staticMidpoint.topStart.toPx(size, density).toBits()).isEqualTo(invalidRadius.toBits())
      assertThat(staticMidpoint.resolveCornerRadiiPx(size, density, LayoutDirection.Ltr))
        .isEqualTo(GlassDefaults.continuousShape.resolveCornerRadiiPx(size, density, LayoutDirection.Ltr))
    }
    assertFailure { ContinuousRoundedCornerShape(1f, smoothing = Float.NEGATIVE_INFINITY) }
      .isInstanceOf<IllegalArgumentException>()
  }
}
