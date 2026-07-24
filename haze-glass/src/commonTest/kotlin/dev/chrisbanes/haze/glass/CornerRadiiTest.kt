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
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
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
}
