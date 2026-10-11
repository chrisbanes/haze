// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.prop
import kotlin.test.Test

class FallbackGlassUnionPathTest {
  @Test
  fun fallbackUnionPath_oneMember_matchesRoundRect() {
    val rect = Rect(20f, 20f, 120f, 100f)
    val path = field(member(rect, radius = 12f)).fallbackUnionPath()
    val pixels = rasterize(path)
    assertThat(pixels, 70, 60).isFilled()
    assertThat(pixels, 21, 21).isEmpty()
    assertThat(pixels, 130, 60).isEmpty()
    assertThat(path.getBounds()).isEqualTo(rect)
  }

  @Test
  fun fallbackUnionPath_twoOverlappingMembers_fillsUnion() {
    val path = field(member(Rect(20f, 20f, 120f, 100f)), member(Rect(90f, 40f, 190f, 120f))).fallbackUnionPath()
    val pixels = rasterize(path)
    assertThat(pixels, 70, 60).isFilled()
    assertThat(pixels, 140, 80).isFilled()
    assertThat(pixels, 105, 70).isFilled()
    assertThat(pixels, 30, 110).isEmpty()
    assertThat(path.getBounds()).isEqualTo(Rect(20f, 20f, 190f, 120f))
  }

  @Test
  fun fallbackUnionPath_twoSeparateMembers_hasNoBridge() {
    // The gap of 10 is under half the spacing, so the shader field would bridge it.
    val path = field(member(Rect(20f, 20f, 100f, 100f)), member(Rect(110f, 20f, 190f, 100f))).fallbackUnionPath()
    val pixels = rasterize(path)
    assertThat(pixels, 60, 60).isFilled()
    assertThat(pixels, 150, 60).isFilled()
    assertThat(pixels, 105, 60).isEmpty()
  }

  @Test
  fun fallbackUnionPath_threeMembers_fillsEachMember() {
    val rects = listOf(Rect(10f, 20f, 70f, 80f), Rect(80f, 20f, 140f, 80f), Rect(130f, 60f, 190f, 120f))
    val path = field(*rects.map { member(it) }.toTypedArray()).fallbackUnionPath()
    val pixels = rasterize(path)
    rects.forEach { assertThat(pixels, it.center.x.toInt(), it.center.y.toInt()).isFilled() }
    assertThat(pixels, 40, 110).isEmpty()
    assertThat(path.getBounds()).isEqualTo(Rect(10f, 20f, 190f, 120f))
  }

  @Test
  fun fallbackUnionPath_fourMembers_fillsEachMember() {
    val rects = listOf(
      Rect(10f, 10f, 70f, 70f),
      Rect(60f, 10f, 120f, 70f),
      Rect(10f, 80f, 70f, 140f),
      Rect(170f, 90f, 230f, 150f),
    )
    val path = field(*rects.map { member(it, radius = 8f) }.toTypedArray()).fallbackUnionPath()
    val pixels = rasterize(path)
    rects.forEach { assertThat(pixels, it.center.x.toInt(), it.center.y.toInt()).isFilled() }
    assertThat(pixels, 120, 110).isEmpty()
    assertThat(path.getBounds()).isEqualTo(Rect(10f, 10f, 230f, 150f))
  }

  @Test
  fun fallbackUnionPath_subset_excludesUnselectedMembers() {
    val first = member(Rect(20f, 20f, 100f, 100f))
    val second = member(Rect(110f, 20f, 190f, 100f))
    val pixels = rasterize(field(first, second).fallbackUnionPath { it == first })
    assertThat(pixels, 60, 60).isFilled()
    assertThat(pixels, 150, 60).isEmpty()
  }

  private fun member(rect: Rect, radius: Float = 0f) = GlassMergedMember(
    rect = rect,
    cornerRadii = CornerRadii(radius, radius, radius, radius),
    tint = Color.Blue,
    backgroundColor = Color.Transparent,
  )

  private fun field(vararg members: GlassMergedMember) = GlassMergedField(
    members = members.toList(),
    spacingPx = 40f,
    opticalSize = Size(80f, 80f),
  )

  private fun rasterize(path: Path): PixelMap {
    val image = ImageBitmap(240, 160)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(240f, 160f)) {
      drawPath(path, Color.Black)
    }
    return image.toPixelMap()
  }

  private fun assertThat(pixels: PixelMap, x: Int, y: Int): Assert<Float> = assertThat(pixels[x, y]).prop(Color::alpha)

  private fun Assert<Float>.isFilled() = isGreaterThan(0.99f)

  private fun Assert<Float>.isEmpty() = isLessThan(0.01f)
}
