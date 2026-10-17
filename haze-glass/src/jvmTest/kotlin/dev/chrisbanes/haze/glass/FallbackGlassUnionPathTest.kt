// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.Assert
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import kotlin.test.Test

class FallbackGlassUnionPathTest {
  @Test
  fun inputMasks_excludeWinningReducedMemberIncludingDowngrade() {
    val reduced = GlassMergedMember(
      Rect(20f, 20f, 120f, 100f),
      CornerRadii.zero,
      Color.Transparent,
      Color.Transparent,
      fallbackBaseColor = Color.White,
    )
    val ordinary = GlassMergedMember(
      Rect(90f, 20f, 190f, 100f),
      CornerRadii.zero,
      Color.Transparent,
      Color.Red,
    )
    val paths = FallbackMergedPaths(field(reduced, ordinary))
    for (path in listOf(checkNotNull(paths.inputMask), checkNotNull(paths.forceInputMask))) {
      val image = rasterize(path)
      assertThat(image, 60, 60).isEmpty()
      assertThat(image, 105, 60).isEmpty()
      assertThat(image, 150, 60).isFilled()
    }
    val reversed = FallbackMergedPaths(field(ordinary, reduced))
    val image = rasterize(checkNotNull(reversed.forceInputMask))
    assertThat(image, 105, 60).isFilled()
    assertThat(image, 60, 60).isEmpty()
  }

  @Test
  fun fallbackMergedPaths_oneMember_unionMatchesRoundRect() {
    val rect = Rect(20f, 20f, 120f, 100f)
    val path = FallbackMergedPaths(field(member(rect, radius = 12f))).union
    val pixels = rasterize(path)
    assertThat(pixels, 70, 60).isFilled()
    assertThat(pixels, 21, 21).isEmpty()
    assertThat(pixels, 130, 60).isEmpty()
    assertThat(path.getBounds()).isEqualTo(rect)
  }

  @Test
  fun fallbackMergedPaths_twoOverlappingMembers_unionFillsBoth() {
    val path = FallbackMergedPaths(
      field(member(Rect(20f, 20f, 120f, 100f)), member(Rect(90f, 40f, 190f, 120f))),
    ).union
    val pixels = rasterize(path)
    assertThat(pixels, 70, 60).isFilled()
    assertThat(pixels, 140, 80).isFilled()
    assertThat(pixels, 105, 70).isFilled()
    assertThat(pixels, 30, 110).isEmpty()
    assertThat(path.getBounds()).isEqualTo(Rect(20f, 20f, 190f, 120f))
  }

  @Test
  fun fallbackMergedPaths_twoSeparateMembers_unionHasNoBridge() {
    // The gap of 10 is under half the spacing, so the shader field would bridge it.
    val path = FallbackMergedPaths(
      field(member(Rect(20f, 20f, 100f, 100f)), member(Rect(110f, 20f, 190f, 100f))),
    ).union
    val pixels = rasterize(path)
    assertThat(pixels, 60, 60).isFilled()
    assertThat(pixels, 150, 60).isFilled()
    assertThat(pixels, 105, 60).isEmpty()
  }

  @Test
  fun fallbackMergedPaths_threeMembers_unionFillsEachMember() {
    val rects = listOf(Rect(10f, 20f, 70f, 80f), Rect(80f, 20f, 140f, 80f), Rect(130f, 60f, 190f, 120f))
    val path = FallbackMergedPaths(field(*rects.map { member(it) }.toTypedArray())).union
    val pixels = rasterize(path)
    rects.forEach { assertThat(pixels, it.center.x.toInt(), it.center.y.toInt()).isFilled() }
    assertThat(pixels, 40, 110).isEmpty()
    assertThat(path.getBounds()).isEqualTo(Rect(10f, 20f, 190f, 120f))
  }

  @Test
  fun fallbackMergedPaths_fourMembers_unionFillsEachMember() {
    val rects = listOf(
      Rect(10f, 10f, 70f, 70f),
      Rect(60f, 10f, 120f, 70f),
      Rect(10f, 80f, 70f, 140f),
      Rect(170f, 90f, 230f, 150f),
    )
    val path = FallbackMergedPaths(field(*rects.map { member(it, radius = 8f) }.toTypedArray())).union
    val pixels = rasterize(path)
    rects.forEach { assertThat(pixels, it.center.x.toInt(), it.center.y.toInt()).isFilled() }
    assertThat(pixels, 120, 110).isEmpty()
    assertThat(path.getBounds()).isEqualTo(Rect(10f, 10f, 230f, 150f))
  }

  @Test
  fun drawMergedColours_unionWithinBudget_boundsLayerToUnion() {
    val paths = FallbackMergedPaths(field(member(Rect(20.5f, 20f, 120f, 100f)), member(Rect(90f, 40f, 190f, 120.5f))))
    val layers = mutableListOf<Rect>()
    val pixels = drawColours(paths, layers)
    assertThat(layers).containsExactly(Rect(20f, 20f, 190f, 121f))
    // The first member's colour applies once in the overlap.
    assertThat(pixels[105, 70]).isEqualTo(pixels[60, 60])
  }

  @Test
  fun drawMergedColours_unionOverBudget_drawsWithoutLayer() {
    val wide = MAX_GLASS_LAYER_DIMENSION_PX + 100f
    val paths = FallbackMergedPaths(field(member(Rect(20f, 20f, 120f, 100f)), member(Rect(110f, 20f, wide, 100f))))
    val layers = mutableListOf<Rect>()
    val pixels = drawColours(paths, layers)
    assertThat(paths.layerBounds(null)).isNull()
    assertThat(layers).isEmpty()
    assertThat(pixels, 60, 60).isGreaterThan(0.4f)
    assertThat(pixels, 200, 60).isGreaterThan(0.4f)
  }

  @Test
  fun drawMergedColours_unionOverBudget_overlapUsesFirstMemberOnce() {
    val wide = MAX_GLASS_LAYER_DIMENSION_PX + 100f
    val paths = FallbackMergedPaths(
      field(
        member(Rect(20f, 20f, 120f, 100f), tint = Color.Red.copy(alpha = 0.5f)),
        member(Rect(100f, 20f, wide, 100f), tint = Color.Blue.copy(alpha = 0.5f)),
      ),
    )
    val pixels = drawColours(paths, mutableListOf())
    assertThat(pixels[110, 60]).isEqualTo(pixels[60, 60])
    assertThat(pixels[200, 60].blue).isGreaterThan(pixels[200, 60].red)
  }

  @Test
  fun drawMergedColours_unionOverBudget_visiblePartWithinBudget_boundsLayerToVisible() {
    val wide = MAX_GLASS_LAYER_DIMENSION_PX + 100f
    val paths = FallbackMergedPaths(
      field(
        member(Rect(20f, 20f, 120f, 100f), tint = Color.Red.copy(alpha = 0.5f)),
        member(Rect(100f, 20f, wide, 100f), tint = Color.Blue.copy(alpha = 0.5f)),
      ),
    )
    val layers = mutableListOf<Rect>()
    val pixels = drawColours(paths, layers, visible = Rect(0f, 0f, 240f, 160f))
    assertThat(layers).containsExactly(Rect(20f, 20f, 240f, 100f))
    assertThat(pixels[110, 60]).isEqualTo(pixels[60, 60])
  }

  @Test
  fun drawMergedColours_visibleMissesUnion_drawsNothing() {
    val paths = FallbackMergedPaths(field(member(Rect(20f, 20f, 120f, 100f)), member(Rect(90f, 40f, 190f, 120f))))
    val layers = mutableListOf<Rect>()
    val pixels = drawColours(paths, layers, visible = Rect(200f, 0f, 240f, 160f))
    assertThat(layers).isEmpty()
    for (x in 0 until pixels.width) {
      for (y in 0 until pixels.height) assertThat(pixels, x, y).isEmpty()
    }
  }

  @Test
  fun drawMergedInteractionLighting_withinBudget_overlapUsesFirstMemberLightOnce() {
    // Member 0 spans x 20..120 and member 1 x 90..190; each light centres on (105, 60).
    val paths = FallbackMergedPaths(field(member(Rect(20f, 20f, 120f, 100f)), member(Rect(90f, 20f, 190f, 100f))))
    val layers = mutableListOf<Rect>()
    val firstOnly = drawLights(paths, listOf(light(), null), layers)
    assertThat(layers).containsExactly(Rect(20f, 20f, 190f, 100f))
    assertThat(paths.exclusiveBuilt()).isFalse()
    // (105, 30) is in the overlap and (75, 60) only in member 0, both 30 px from the centre.
    assertThat(firstOnly[105, 30].alpha).isGreaterThan(0.1f)
    assertThat(firstOnly[105, 30]).isEqualTo(firstOnly[75, 60])
    // Unlit member 1 shows no light, even within the radius.
    assertThat(firstOnly, 135, 60).isEmpty()

    val both = drawLights(paths, listOf(light(), light()), mutableListOf())
    assertThat(both[105, 30]).isEqualTo(firstOnly[105, 30])
  }

  @Test
  fun drawMergedInteractionLighting_withinBudget_unlitFirstMemberClearsLaterLightInOverlap() {
    // Member 0 spans x 20..120 and member 1 x 90..190; only member 1 is lit, centred on (105, 60).
    val paths = FallbackMergedPaths(field(member(Rect(20f, 20f, 120f, 100f)), member(Rect(90f, 20f, 190f, 100f))))
    val secondOnly = drawLights(paths, listOf(null, light()), mutableListOf())
    // Unlit member 0 owns the overlap, so it shows no light there.
    assertThat(secondOnly, 105, 30).isEmpty()
    // (135, 60) is only in member 1, 30 px from the centre.
    assertThat(secondOnly[135, 60].alpha).isGreaterThan(0.1f)
  }

  @Test
  fun drawMergedInteractionLighting_visibleOverBudget_usesExclusivePaths() {
    val wide = MAX_GLASS_LAYER_DIMENSION_PX + 100f
    val paths = FallbackMergedPaths(field(member(Rect(20f, 20f, 120f, 100f)), member(Rect(90f, 20f, wide, 100f))))
    val layers = mutableListOf<Rect>()
    val pixels = drawLights(paths, listOf(light(), light()), layers, visible = null)
    assertThat(layers).isEmpty()
    assertThat(paths.exclusiveBuilt()).isTrue()
    assertThat(pixels[105, 30]).isEqualTo(pixels[75, 60])
  }

  private fun drawColours(paths: FallbackMergedPaths, layers: MutableList<Rect>, visible: Rect? = null): PixelMap =
    draw(layers) { drawMergedColours(paths, alphaMultiplier = 1f, visible = visible) { it.tint } }

  private fun drawLights(
    paths: FallbackMergedPaths,
    brushes: List<Brush?>,
    layers: MutableList<Rect>,
    visible: Rect? = Rect(0f, 0f, 240f, 160f),
  ): PixelMap = draw(layers) { drawMergedInteractionLighting(paths, brushes, visible) }

  private fun light() = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent), center = Offset(105f, 60f), radius = 40f)

  private fun draw(layers: MutableList<Rect>, block: DrawScope.() -> Unit): PixelMap {
    val image = ImageBitmap(240, 160)
    val delegate = Canvas(image)
    val canvas = object : Canvas by delegate {
      override fun saveLayer(bounds: Rect, paint: Paint) {
        layers += bounds
        delegate.saveLayer(bounds, paint)
      }
    }
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(240f, 160f), block)
    return image.toPixelMap()
  }

  private fun member(rect: Rect, radius: Float = 0f, tint: Color = Color.Blue.copy(alpha = 0.5f)) = GlassMergedMember(
    rect = rect,
    cornerRadii = CornerRadii(radius, radius, radius, radius),
    tint = tint,
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
