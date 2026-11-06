// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test

class CornerRadiiTest {

  @Test
  fun toCornerRadiiPx_leavesValidRadiiUnchanged() {
    val shape = RoundedCornerShape(10.dp, 20.dp, 30.dp, 40.dp)
    assertThat(
      shape.toCornerRadiiPx(Size(100f, 100f), Density(1f), LayoutDirection.Ltr),
    ).isEqualTo(CornerRadii(10f, 20f, 30f, 40f))
  }

  @Test
  fun toCornerRadiiPx_matchesComposePerEdgeNormalization() {
    val shape = RoundedCornerShape(
      topStart = 80.dp,
      topEnd = 40.dp,
      bottomEnd = 20.dp,
      bottomStart = 120.dp,
    )
    assertThat(
      shape.toCornerRadiiPx(Size(200f, 100f), Density(1f), LayoutDirection.Ltr),
    ).isEqualTo(CornerRadii(40f, 40f, 20f, 60f))
  }

  @Test
  fun toCornerRadiiPx_matchesSkiaGlobalEdgeNormalization() {
    val shape = RoundedCornerShape(
      topStart = 100.dp,
      topEnd = 100.dp,
      bottomEnd = 0.dp,
      bottomStart = 0.dp,
    )

    assertThat(
      shape.toCornerRadiiPx(Size(120f, 100f), Density(1f), LayoutDirection.Ltr),
    ).isEqualTo(CornerRadii(60f, 60f, 0f, 0f))
  }

  @Test
  fun toCornerRadiiPx_computesSkiaEdgeSumInDoublePrecision() {
    val edgeLength = 16_777_216f
    val shape = RoundedCornerShape(
      topStart = edgeLength.dp,
      topEnd = 1.dp,
      bottomEnd = 0.dp,
      bottomStart = 0.dp,
    )
    val scale = edgeLength.toDouble() / (edgeLength.toDouble() + 1.0)

    assertThat(
      shape.toCornerRadiiPx(Size(edgeLength, edgeLength), Density(1f), LayoutDirection.Ltr),
    ).isEqualTo(
      CornerRadii(
        topLeft = (edgeLength.toDouble() * scale).toFloat(),
        topRight = scale.toFloat(),
        bottomRight = 0f,
        bottomLeft = 0f,
      ),
    )
  }

  @Test
  fun toCornerRadiiPx_mapsLogicalCornersInRtl() {
    val shape = RoundedCornerShape(10.dp, 20.dp, 30.dp, 40.dp)

    assertThat(
      shape.toCornerRadiiPx(Size(100f, 100f), Density(1f), LayoutDirection.Rtl),
    ).isEqualTo(CornerRadii(20f, 10f, 40f, 30f))
  }

  @Test
  fun toCornerRadiiPx_rejectsNegativeResolvedCornerSizeLikeCompose() {
    val shape = RoundedCornerShape(
      topStart = (-1).dp,
      topEnd = 0.dp,
      bottomEnd = 0.dp,
      bottomStart = 0.dp,
    )

    assertFailure {
      shape.toCornerRadiiPx(Size(100f, 100f), Density(1f), LayoutDirection.Ltr)
    }.isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "Corner size in Px can't be negative(topStart = -1.0, topEnd = 0.0, " +
          "bottomEnd = 0.0, bottomStart = 0.0)!",
      )
  }

  @Test
  fun continuousCornerExponent_zeroIsExactlyCircular() {
    assertThat(continuousCornerExponent(0f)).isEqualTo(2f)
  }

  @Test
  fun continuousCornerExponent_increasesWithSmoothing() {
    assertThat(continuousCornerExponent(0.5f)).isCloseTo(2.630f, 0.001f)
    assertThat(continuousCornerExponent(1f)).isCloseTo(3.259f, 0.001f)
  }

  @Test
  fun continuousCornerExtent_atFullSmoothing() {
    assertThat(continuousCornerExtent(10f, 1f)).isCloseTo(15.2866483f, 0.0001f)
    assertThat(continuousCornerExtent(10f, 0f)).isEqualTo(10f)
  }

  @Test
  fun fitSmoothingTo_capsuleIsCircular() {
    val radii = ContinuousRoundedCornerShape(percent = 50)
      .resolveCornerRadiiPx(Size(200f, 80f), Density(1f), LayoutDirection.Ltr)

    assertThat(radii).isEqualTo(CornerRadii(40f, 40f, 40f, 40f))
  }

  @Test
  fun fitSmoothingTo_reducesSmoothingNotRadius() {
    val radii = ContinuousRoundedCornerShape(40.dp)
      .resolveCornerRadiiPx(Size(100f, 100f), Density(1f), LayoutDirection.Ltr)

    assertThat(radii.topLeft).isEqualTo(40f)
    assertThat(radii.topLeftSmoothing).isLessThan(1f)
    assertThat(continuousCornerExtent(radii.topLeft, radii.topLeftSmoothing) * 2f).isLessThanOrEqualTo(100.001f)
    assertThat(continuousCornerExtent(radii.topLeft, radii.topLeftSmoothing) * 2f).isCloseTo(100f, 0.01f)
  }

  @Test
  fun fitSmoothingTo_unconstrainedKeepsRequestedSmoothing() {
    val radii = ContinuousRoundedCornerShape(16.dp)
      .resolveCornerRadiiPx(Size(300f, 200f), Density(1f), LayoutDirection.Ltr)

    assertThat(radii).isEqualTo(CornerRadii(16f, 16f, 16f, 16f, 1f, 1f, 1f, 1f))
  }

  @Test
  fun fitSmoothingTo_squareCornerHasNoSmoothing() {
    val radii = ContinuousRoundedCornerShape(topStart = 16.dp)
      .resolveCornerRadiiPx(Size(300f, 200f), Density(1f), LayoutDirection.Ltr)

    assertThat(radii).isEqualTo(CornerRadii(16f, 0f, 0f, 0f, topLeftSmoothing = 1f))
  }

  @Test
  fun fitSmoothingTo_overlappingDiagonalCornersAreCircular() {
    // Each edge fits, but the top-left and bottom-right boxes would overlap.
    val radii = ContinuousRoundedCornerShape(topStart = 60.dp, bottomEnd = 60.dp)
      .resolveCornerRadiiPx(Size(100f, 100f), Density(1f), LayoutDirection.Ltr)

    assertThat(radii).isEqualTo(CornerRadii(60f, 0f, 60f, 0f))
  }

  @Test
  fun fitSmoothingTo_diagonalExtentsReduceSmoothing() {
    // The radii fit (80 <= 100), but the full diagonal extents would overlap.
    val radii = ContinuousRoundedCornerShape(topStart = 40.dp, bottomEnd = 40.dp)
      .resolveCornerRadiiPx(Size(100f, 100f), Density(1f), LayoutDirection.Ltr)

    assertThat(radii.topLeft).isEqualTo(40f)
    assertThat(radii.bottomRight).isEqualTo(40f)
    assertThat(radii.topLeftSmoothing).isGreaterThan(0f)
    assertThat(radii.topLeftSmoothing).isLessThan(1f)
    assertThat(radii.bottomRightSmoothing).isGreaterThan(0f)
    assertThat(
      continuousCornerExtent(radii.topLeft, radii.topLeftSmoothing) +
        continuousCornerExtent(radii.bottomRight, radii.bottomRightSmoothing),
    ).isLessThanOrEqualTo(100.001f)
  }

  @Test
  fun fitSmoothingTo_adjacentRadiusOverlapIsCircular() {
    val radii = CornerRadii(70f, 70f, 0f, 0f, 1f, 1f, 0f, 0f).fitSmoothingTo(Size(100f, 100f))

    assertThat(radii).isEqualTo(CornerRadii(70f, 70f, 0f, 0f))
  }

  @Test
  fun resolveCornerRadiiPx_roundedCornerShapeHasZeroSmoothing() {
    val radii = RoundedCornerShape(16.dp)
      .resolveCornerRadiiPx(Size(300f, 200f), Density(1f), LayoutDirection.Ltr)

    assertThat(radii).isEqualTo(CornerRadii(16f, 16f, 16f, 16f))
  }

  @Test
  fun resolveCornerRadiiPx_rtlMapsSmoothingWithItsCorner() {
    // The 70px top edge cannot fit both full extents, so the top corners share a reduced smoothing.
    val radii = ContinuousRoundedCornerShape(topStart = 50.dp, topEnd = 10.dp)
      .resolveCornerRadiiPx(Size(70f, 200f), Density(1f), LayoutDirection.Rtl)

    assertThat(radii.topRight).isEqualTo(50f)
    assertThat(radii.topLeft).isEqualTo(10f)
    assertThat(radii.topLeftSmoothing).isEqualTo(radii.topRightSmoothing)
    assertThat(radii.topRightSmoothing).isLessThan(1f)
    assertThat(radii.bottomLeftSmoothing).isEqualTo(0f)
  }

  @Test
  fun continuousCornerPoint_fullSmoothingKeepsCircularDiagonalAndEdgeTangents() {
    val radius = 80f
    val extent = continuousCornerExtent(radius, 1f)
    val exponent = continuousCornerExponent(1f)
    // Top-left corner box centre is (extent, extent); the circle's centre is (radius, radius).
    val diagonal = continuousCornerPoint(PI * 1.25, extent, exponent)
    val circleDiagonal = radius - radius / sqrt(2f)
    assertThat(extent + diagonal.x).isCloseTo(circleDiagonal, 0.5f)
    assertThat(extent + diagonal.y).isCloseTo(circleDiagonal, 0.5f)

    val leftEdge = continuousCornerPoint(PI, extent, exponent)
    assertThat(extent + leftEdge.x).isCloseTo(0f, 0.0001f)
    assertThat(extent + leftEdge.y).isCloseTo(extent, 0.0001f)
    val topEdge = continuousCornerPoint(PI * 1.5, extent, exponent)
    assertThat(extent + topEdge.x).isCloseTo(extent, 0.0001f)
    assertThat(extent + topEdge.y).isCloseTo(0f, 0.0001f)
  }

  @Test
  fun lerp_interpolatesSmoothingWithinRange() {
    val from = CornerRadii(10f, 10f, 10f, 10f)
    val to = CornerRadii(20f, 20f, 20f, 20f, 1f, 1f, 1f, 1f)
    assertThat(lerp(from, to, 0.5f)).isEqualTo(CornerRadii(15f, 15f, 15f, 15f, 0.5f, 0.5f, 0.5f, 0.5f))
    assertThat(lerp(from, to, 1.5f).topLeftSmoothing).isEqualTo(1f)
  }
}
