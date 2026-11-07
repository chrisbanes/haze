// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Interpolatable
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathHitTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.prop
import kotlin.test.Test

/** Path geometry needs a real [androidx.compose.ui.graphics.Path], so it runs on Skiko. */
class ContinuousCornerPathTest {

  @Test
  fun createOutline_fullSmoothingIsGenericPathFillingSize() {
    val outline = ContinuousRoundedCornerShape(16.dp).createOutline(Size(200f, 100f), LayoutDirection.Ltr, Density(1f))

    assertThat(outline).isInstanceOf<Outline.Generic>()
      .prop(Outline::bounds)
      .isEqualTo(Rect(0f, 0f, 200f, 100f))
  }

  @Test
  fun toPath_fullSmoothingFillsSize() {
    val radii = CornerRadii(16f, 16f, 16f, 16f, 1f, 1f, 1f, 1f)

    assertThat(radii.toPath(Size(200f, 100f)).getBounds()).isEqualTo(Rect(0f, 0f, 200f, 100f))
  }

  @Test
  fun lerp_midpointOutlineMatchesAsymmetricGeometryInBothDirections() {
    val start = ContinuousRoundedCornerShape(8f, 20f, 32f, 44f, 0.2f)
    val end = ContinuousRoundedCornerShape(24f, 36f, 48f, 60f, 0.8f)
    val midpoint = Interpolatable.lerp(start, end, 0.5f) as ContinuousRoundedCornerShape
    val expected = ContinuousRoundedCornerShape(16f, 28f, 40f, 52f, 0.5f)
    val size = Size(300f, 200f)
    for (direction in LayoutDirection.entries) {
      val actualPath = (midpoint.createOutline(size, direction, Density(1f)) as Outline.Generic).path
      val expectedPath = (expected.createOutline(size, direction, Density(1f)) as Outline.Generic).path
      val startPath = (start.createOutline(size, direction, Density(1f)) as Outline.Generic).path
      val actualHit = PathHitTester().apply { updatePath(actualPath) }
      val expectedHit = PathHitTester().apply { updatePath(expectedPath) }
      val startHit = PathHitTester().apply { updatePath(startPath) }
      var distinguishesStart = false
      for (x in 0..60 step 2) for (y in 0..60 step 2) {
        for (point in listOf(
          Offset(x.toFloat(), y.toFloat()),
          Offset(300f - x, y.toFloat()),
          Offset(300f - x, 200f - y),
          Offset(x.toFloat(), 200f - y),
        )) {
          assertThat(actualHit.contains(point)).isEqualTo(expectedHit.contains(point))
          if (expectedHit.contains(point) != startHit.contains(point)) distinguishesStart = true
        }
      }
      assertThat(distinguishesStart).isTrue()
    }
  }

  @Test
  fun lerp_positiveOvershootCreatesOutline() {
    val start = ContinuousRoundedCornerShape(10f, smoothing = 0.2f)
    val end = ContinuousRoundedCornerShape(30f, smoothing = 0.8f)
    val overshoot = Interpolatable.lerp(start, end, 1.5f) as ContinuousRoundedCornerShape
    assertThat(overshoot.createOutline(Size(240f, 120f), LayoutDirection.Rtl, Density(1f)))
      .isInstanceOf<Outline.Generic>()
  }

  @Test
  fun lerp_positiveExtremeGrowthPreservesRoundedOutline() = checkExtremeGrowthOutline(10f, 30f, Float.MAX_VALUE)

  @Test
  fun lerp_negativeExtremeGrowthPreservesRoundedOutline() = checkExtremeGrowthOutline(30f, 10f, -Float.MAX_VALUE)

  @Test
  fun lerp_finiteCornerSumOverflowPreservesRoundedOutline() = checkExtremeGrowthOutline(10f, 30f, Float.MAX_VALUE / 30f)

  private fun checkExtremeGrowthOutline(from: Float, to: Float, fraction: Float) {
    val result = Interpolatable.lerp(
      ContinuousRoundedCornerShape(from, smoothing = 0.2f),
      ContinuousRoundedCornerShape(to, smoothing = 0.8f),
      fraction,
    ) as ContinuousRoundedCornerShape
    for (direction in LayoutDirection.entries) {
      val outline = result.createOutline(Size(240f, 120f), direction, Density(1f))
      assertThat(outline).isInstanceOf<Outline.Rounded>()
      val rect = (outline as Outline.Rounded).roundRect
      for (radius in listOf(rect.topLeftCornerRadius, rect.topRightCornerRadius, rect.bottomRightCornerRadius, rect.bottomLeftCornerRadius)) {
        assertThat(radius.x).isCloseTo(60f, 0.001f)
        assertThat(radius.y).isCloseTo(60f, 0.001f)
      }
    }
  }

  @Test
  fun lerp_equalLargeCornerAndGrowingNeighbourKeepRoundedPublicOutline() {
    val large = Float.MAX_VALUE * 0.75f
    val start = ContinuousRoundedCornerShape(large, 0f, 0f, 0f, 0f)
    val end = ContinuousRoundedCornerShape(large, 0f, 0f, 10f, 0f)
    val size = Size(240f, 120f)
    val density = Density(1f)
    for (direction in LayoutDirection.entries) {
      for (endpoint in listOf(start, end)) {
        assertThat(endpoint.createOutline(size, direction, density)).isInstanceOf<Outline.Rounded>()
      }
      for ((from, to, fraction) in listOf(Triple(start, end, Float.MAX_VALUE), Triple(end, start, -Float.MAX_VALUE))) {
        val result = Interpolatable.lerp(from, to, fraction) as ContinuousRoundedCornerShape
        val outline = result.createOutline(size, direction, density)
        assertThat(outline).isInstanceOf<Outline.Rounded>()
        val rect = (outline as Outline.Rounded).roundRect
        val expected = if (direction == LayoutDirection.Ltr) listOf(60f, 0f, 0f, 60f) else listOf(0f, 60f, 60f, 0f)
        val actual = listOf(rect.topLeftCornerRadius, rect.topRightCornerRadius, rect.bottomRightCornerRadius, rect.bottomLeftCornerRadius)
        for ((corner, radius) in actual.zip(expected)) {
          assertThat(corner.x).isCloseTo(radius, 0.001f)
          assertThat(corner.y).isCloseTo(radius, 0.001f)
        }
      }
    }
  }

  @Test
  fun lerp_zeroSmoothingProducesExactRoundedOutline() {
    val start = ContinuousRoundedCornerShape(4f, 8f, 12f, 16f, 0f)
    val end = ContinuousRoundedCornerShape(12f, 16f, 20f, 24f, 0f)
    val midpoint = Interpolatable.lerp(start, end, 0.5f) as ContinuousRoundedCornerShape
    for (direction in LayoutDirection.entries) {
      assertThat(midpoint.createOutline(Size(200f, 100f), direction, Density(1f))).isEqualTo(
        ContinuousRoundedCornerShape(8f, 12f, 16f, 20f, 0f).createOutline(Size(200f, 100f), direction, Density(1f)),
      )
    }
  }
}
