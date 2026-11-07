// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeProgressive
import kotlin.test.Test

class ProgressiveCaptureGeometryTest {
  @Test
  fun expandedFractionalBounds_intersectWithoutRounding() {
    val scale = ScaleFactor(19f / 25.2f, 18f / 23.4f)
    val crop = progressiveCaptureCrop(Size(21f, 19f), Offset(2.25f, 4.5f), scale, Size(19f, 18f))
    assertThat(crop.left).isCloseTo(2.25f * scale.scaleX, 0.00001f)
    assertThat(crop.top).isCloseTo(4.5f * scale.scaleY, 0.00001f)
    assertThat(crop.right).isCloseTo(23.25f * scale.scaleX, 0.00001f)
    assertThat(crop.bottom).isEqualTo(18f)
  }

  @Test
  fun negativeOrigin_clipsStartWithoutExpandingContent() {
    assertThat(progressiveCaptureCrop(Size(21f, 19f), Offset(-2.25f, -4.5f), ScaleFactor(0.5f, 0.5f), Size(13f, 12f)))
      .isEqualTo(Rect(0f, 0f, 9.375f, 7.25f))
  }

  @Test
  fun directDefault_preservesUnboundedCeilAndFractionalOrigin() {
    assertThat(progressiveCaptureCrop(Size(21f, 19f), Offset(-2.25f, 4.5f), ScaleFactor(0.5f, 0.5f), Size.Unspecified))
      .isEqualTo(Rect(Offset(-1.125f, 2.25f), Size(11f, 10f)))
  }

  @Test
  fun emptyIntersections_haveNoDrawableArea() {
    for (origin in listOf(Offset(-25f, 0f), Offset(30f, 0f), Offset(0f, -25f), Offset(0f, 30f))) {
      assertThat(progressiveCaptureCrop(Size(21f, 19f), origin, ScaleFactor(0.5f, 0.5f), Size(13f, 12f)).isEmpty).isTrue()
    }
    assertThat(progressiveCaptureCrop(Size(21f, 19f), Offset.Zero, ScaleFactor(0.5f, 0.5f), Size.Zero).isEmpty).isTrue()
  }

  @Test
  fun progressiveCache_consumesAllocationWhilePlainGeometryReuses() {
    fun params(capture: Size, progressive: Boolean) = RenderEffectParams(
      blurRadius = 8.dp,
      noiseFactor = 0f,
      scale = 0.5f,
      contentSize = Size(21f, 19f),
      contentOffset = Offset(2f, 4f),
      captureSize = capture,
      progressive = if (progressive) HazeProgressive.Brush(SolidColor(Color.Black)) else null,
      blurTileMode = TileMode.Clamp,
    ).renderEffectCacheKey(Density(1f))
    assertThat(params(Size(13f, 12f), true)).isNotEqualTo(params(Size(12f, 12f), true))
    assertThat(params(Size(13f, 12f), true)).isNotEqualTo(params(Size.Unspecified, true))
    assertThat(params(Size(13f, 12f), false)).isEqualTo(params(Size(12f, 12f), false))
  }
}
