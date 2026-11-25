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
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

class GlassRimBrushTest {
  @Test
  fun directRim_coversPixelsStraddlingRoundedBoundary() {
    val provider = checkNotNull(createGlassRimBrushProvider())
    val brush = checkNotNull(
      provider(rimKey().copy(specularIntensity = 0f, edgeShadow = Color.White, sampleStepPx = 2f)),
    )
    // Pixel (9,17) straddles the top-left circular arc with its centre just inside it.
    val alpha = drawBrush(brush).toPixelMap()[9, 17].alpha
    assertThat(alpha).isGreaterThan(0.1f)
    assertThat(alpha).isLessThanOrEqualTo(0.9f)
  }

  @Test
  fun directRim_edgeOutline_drawsHardBandOutsideShape() {
    val provider = checkNotNull(createGlassRimBrushProvider())
    val pixels = drawBrush(checkNotNull(provider(outlineKey()))).toPixelMap()

    // The material's left edge is at x = 8; pixel 6 is about 1.5px outside it.
    val band = pixels[6, 48]
    assertThat(band.alpha).isGreaterThanOrEqualTo(0.9f)
    assertThat(band.green).isGreaterThan(maxOf(band.red, band.blue))
    assertThat(pixels[3, 48].alpha).isEqualTo(0f)
    assertThat(pixels[14, 48].alpha).isEqualTo(0f)
  }

  @Test
  fun edgeOutline_subpixelWidths_coverFirstOutsidePixelOnNodeBoundEdges() {
    val provider = checkNotNull(createGlassRimBrushProvider())
    for (widthPx in listOf(0.35f, 0.5f, 0.7f)) {
      val key = nodeBoundEdgeKey().copy(edgeOutline = GlassEdgeOutline(Color.Green.copy(alpha = 0.4f), widthPx))
      val direct = drawBrush(checkNotNull(provider(key)))
      val filtered = drawFilter(key)
      assertThat(maxChannelDifference(direct, filtered), "$widthPx px direct/filter difference")
        .isLessThanOrEqualTo(1f / 255f)

      val pixels = direct.toPixelMap()
      for ((x, y) in listOf(7 to 48, 120 to 48, 64 to 5, 64 to 90)) {
        assertThat(pixels[x, y].alpha, "$widthPx px outline at ($x, $y)").isCloseTo(0.4f * widthPx, 1f / 255f)
      }
      for ((x, y) in listOf(8 to 48, 119 to 48, 64 to 6, 64 to 89, 6 to 48, 121 to 48, 64 to 4, 64 to 91)) {
        assertThat(pixels[x, y].alpha, "$widthPx px outline at ($x, $y)").isEqualTo(0f)
      }
    }
  }

  @Test
  fun edgeOutline_subpixelWidths_surviveHalfResolutionRenderEffect() {
    val provider = checkNotNull(createGlassRimBrushProvider())
    for (widthPx in listOf(0.35f, 0.5f, 0.7f)) {
      val key = nodeBoundEdgeKey().copy(
        coordinates = GlassCoordinates(Size(64f, 48f), Offset(4f, 3f), Size(56f, 42f), 0.5f, pixelAligned = true),
        sampleStepPx = 1f,
        edgeOutline = GlassEdgeOutline(Color.Green.copy(alpha = 0.4f), widthPx * 0.5f),
      )
      // A direct draw samples the presented physical pixels, with x = 8 as the material edge.
      val direct = drawBrush(checkNotNull(provider(key)), canvasScale = 2f).toPixelMap()
      assertThat(direct[7, 48].alpha, "$widthPx px direct outline").isCloseTo(0.4f * widthPx, 1f / 255f)
      assertThat(direct[8, 48].alpha).isEqualTo(0f)
      assertThat(direct[6, 48].alpha).isEqualTo(0f)

      // The filter first rasterizes at working resolution, where x = 4 is the material edge.
      val raster = drawFilter(key).toPixelMap()
      assertThat(raster[3, 24].alpha, "$widthPx px rasterized outline").isCloseTo(0.4f * widthPx * 0.5f, 1f / 255f)
      assertThat(raster[4, 24].alpha).isEqualTo(0f)
      assertThat(raster[2, 24].alpha).isEqualTo(0f)
    }
  }

  @Test
  fun directRim_nodeBoundEdges_keepOutlineOffInsideEdgePixel() {
    val provider = checkNotNull(createGlassRimBrushProvider())
    val key = nodeBoundEdgeKey().copy(edgeOutline = GlassEdgeOutline(Color.Green, 3f))
    val aligned = drawBrush(checkNotNull(provider(key))).toPixelMap()
    val unaligned = drawBrush(checkNotNull(provider(key.notPixelAligned()))).toPixelMap()

    // The material's left edge is at x = 8, so pixel 8 is the outermost pixel inside it.
    assertThat(unaligned[8, 48].alpha).isGreaterThan(0.1f)
    assertThat(aligned[8, 48].alpha).isEqualTo(0f)
    assertThat(aligned[63, 89].alpha).isEqualTo(0f)
    // All three outside pixels in the band are fully covered on the aligned side.
    for (x in 5..7) assertThat(aligned[x, 48].alpha).isEqualTo(1f)
    assertThat(aligned[4, 48].alpha).isEqualTo(0f)
    assertThat(aligned[63, 90]).isEqualTo(unaligned[63, 90])
  }

  @Test
  fun directRim_nodeBoundEdges_drawRimHighlightAtFullStrengthOnEdgePixel() {
    val provider = checkNotNull(createGlassRimBrushProvider())
    val key = nodeBoundEdgeKey().copy(rimHighlight = GlassRimHighlight(intensity = 1f, widthPx = 4f, sides = 1f))
    val aligned = drawBrush(checkNotNull(provider(key))).toPixelMap()
    val unaligned = drawBrush(checkNotNull(provider(key.notPixelAligned()))).toPixelMap()

    assertThat(unaligned[8, 48].alpha).isLessThanOrEqualTo(0.6f)
    assertThat(aligned[8, 48].alpha).isEqualTo(aligned[9, 48].alpha)
    assertThat(aligned[9, 48].alpha).isGreaterThanOrEqualTo(0.9f)
  }

  @Test
  fun directRim_nodeBoundEdges_keepRoundedCornerAntialiasing() {
    val provider = checkNotNull(createGlassRimBrushProvider())
    val key = nodeBoundEdgeKey().copy(
      cornerRadii = CornerRadii(18f, 18f, 18f, 18f),
      edgeOutline = GlassEdgeOutline(Color.Green, 3f),
    )
    val aligned = drawBrush(checkNotNull(provider(key))).toPixelMap()
    val unaligned = drawBrush(checkNotNull(provider(key.notPixelAligned()))).toPixelMap()

    // Pixel (13,11) straddles the top-left arc, away from the material rectangle; (9,7) is outside.
    assertThat(aligned[13, 11]).isEqualTo(unaligned[13, 11])
    assertThat(aligned[13, 11].alpha).isGreaterThan(0.1f)
    assertThat(aligned[9, 7]).isEqualTo(unaligned[9, 7])
  }

  @Test
  fun directRim_continuousCorners_edgeOutlineFollowsSmoothedShape() {
    val key = outlineKey().copy(cornerRadii = CornerRadii(18f, 18f, 18f, 18f, 1f, 1f, 1f, 1f))
    val provider = checkNotNull(createGlassRimBrushProvider(corners = GlassCornerShader.Continuous))
    val pixels = drawBrush(checkNotNull(provider(key))).toPixelMap()
    val circular = drawBrush(checkNotNull(createGlassRimBrushProvider()?.invoke(outlineKey()))).toPixelMap()

    // The straight edges keep the same band; the corners follow the smoothed shape instead.
    assertThat(pixels[6, 48].alpha).isGreaterThanOrEqualTo(0.9f)
    assertThat(pixels[3, 48].alpha).isEqualTo(0f)
    assertThat(pixels[14, 48].alpha).isEqualTo(0f)
    var cornerDifference = 0f
    for (y in 0 until 30) for (x in 0 until 30) {
      cornerDifference = maxOf(cornerDifference, abs(pixels[x, y].alpha - circular[x, y].alpha))
    }
    assertThat(cornerDifference).isGreaterThan(0.1f)
  }

  @Test
  fun directRim_edgeOutlineTopAndBottomStrength_weightsCircleByEdgeDirection() {
    val provider = checkNotNull(createGlassRimBrushProvider())
    fun alphaAt(strength: Float, degrees: Int): Float {
      val pixels = drawBrush(checkNotNull(provider(circleOutlineKey().withOutlineStrength(strength)))).toPixelMap()
      // 1.5px outside the 42px circle centred at (50, 48); 0 degrees is right, 90 is top.
      val radians = degrees * PI / 180.0
      return pixels[(50.0 + 43.5 * cos(radians)).toInt(), (48.0 - 43.5 * sin(radians)).toInt()].alpha
    }

    for (degrees in listOf(0, 180)) {
      assertThat(alphaAt(0f, degrees), "$degrees").isGreaterThanOrEqualTo(0.9f * alphaAt(1f, degrees))
    }
    for (degrees in listOf(90, 270)) {
      assertThat(alphaAt(1f, degrees), "$degrees").isGreaterThan(0.5f)
      assertThat(alphaAt(0f, degrees), "$degrees").isLessThanOrEqualTo(0.02f)
    }
    val diagonal = alphaAt(0f, 45) / alphaAt(0f, 0)
    assertThat(diagonal).isGreaterThanOrEqualTo(0.35f)
    assertThat(diagonal).isLessThanOrEqualTo(0.65f)
    val quarter = alphaAt(0.25f, 90) / alphaAt(0.25f, 0)
    assertThat(quarter).isGreaterThanOrEqualTo(0.18f)
    assertThat(quarter).isLessThanOrEqualTo(0.32f)
  }

  @Test
  fun directRim_edgeOutlineTopAndBottomStrength_keepsRectangleSidesOnly() {
    val provider = checkNotNull(createGlassRimBrushProvider())
    val pixels = drawBrush(checkNotNull(provider(outlineKey().withOutlineStrength(0f)))).toPixelMap()

    // The material's left edge is at x = 8 and its top at y = 6; each probe is 1.5px outside.
    assertThat(pixels[6, 48].alpha).isGreaterThanOrEqualTo(0.9f)
    assertThat(pixels[64, 4].alpha).isLessThanOrEqualTo(0.02f)
    assertThat(pixels[64, 91].alpha).isLessThanOrEqualTo(0.02f)
  }

  @Test
  fun directRim_edgeOutlineTopAndBottomStrengthOne_matchesUniformOutline() {
    val smoothed = CornerRadii(18f, 18f, 18f, 18f, 1f, 1f, 1f, 1f)
    for (continuous in listOf(false, true)) {
      val provider = checkNotNull(createGlassRimBrushProvider(corners = if (continuous) GlassCornerShader.Continuous else GlassCornerShader.Circular))
      val outline = if (continuous) outlineKey().copy(cornerRadii = smoothed) else outlineKey()
      for (key in listOf(outline, circleOutlineKey())) {
        val expected = drawBrush(checkNotNull(provider(key)))
        val actual = drawBrush(checkNotNull(provider(key.withOutlineStrength(1f))))
        assertThat(maxChannelDifference(expected, actual), "continuous=$continuous").isEqualTo(0f)
      }
    }
  }

  @Test
  fun directRim_disabledOutline_matchesNoOutline() {
    val provider = checkNotNull(createGlassRimBrushProvider())
    val expected = drawBrush(checkNotNull(provider(rimKey())))
    for (outline in listOf(GlassEdgeOutline(Color.Green, 0f), GlassEdgeOutline(Color.Transparent, 3f))) {
      val actual = drawBrush(checkNotNull(provider(rimKey().copy(edgeOutline = outline))))
      assertThat(maxChannelDifference(expected, actual), "$outline").isEqualTo(0f)
    }
  }

  @Test
  fun directRim_matchesImageFilterAcrossUniformChanges() {
    val provider = checkNotNull(createGlassRimBrushProvider())
    val key = rimKey()
    val keys = listOf(
      key,
      key.copy(lightPosition = Offset(110f, 85f)),
      key.copy(cornerRadii = CornerRadii(0f, 8f, 22f, 30f), edgeSoftnessPx = 0.5f),
      key.copy(specularIntensity = 0f, edgeShadow = Color(0.2f, 0.4f, 0.8f, 0.8f)),
      key.copy(
        coordinates = GlassCoordinates(Size(128f, 96f), Offset(23f, 17f), Size(74f, 52f), 0.5f),
        sampleStepPx = 2f,
        specularExponent = 8f,
      ),
      outlineKey(),
      key.copy(edgeOutline = GlassEdgeOutline(Color(0.1f, 0.2f, 0.9f, 0.7f), 4f)),
      outlineKey().withOutlineStrength(0.25f),
    )
    for (current in keys) {
      val direct = drawBrush(checkNotNull(provider(current)))
      val filtered = drawFilter(current)
      assertThat(maxChannelDifference(direct, filtered)).isLessThanOrEqualTo(1f / 255f)
    }
  }

  @Test
  fun uniformUpdate_preservesPreviouslyCreatedBrushSnapshot() {
    val provider = checkNotNull(createGlassRimBrushProvider())
    val first = checkNotNull(provider(rimKey()))
    val before = drawBrush(first)
    val second = checkNotNull(provider(rimKey().copy(lightPosition = Offset(110f, 85f))))

    assertThat(maxChannelDifference(before, drawBrush(second))).isGreaterThan(0.1f)
    assertThat(maxChannelDifference(before, drawBrush(first))).isEqualTo(0f)
  }

  @Test
  fun createGlassRimBrushProvider_twiceForSameVariant_reusesCompiledEffect() {
    for (merged in listOf(false, true)) {
      val first = rimProvider(merged)
      val second = rimProvider(merged)
      assertThat(second).isNotSameInstanceAs(first)
      assertThat(second.effect).isSameInstanceAs(first.effect)
    }
  }

  @Test
  fun createGlassMergedBackgroundShaderProvider_twice_reusesCompiledEffect() {
    val first = mergedBackgroundProvider()
    val second = mergedBackgroundProvider()
    assertThat(second).isNotSameInstanceAs(first)
    assertThat(second.effect).isSameInstanceAs(first.effect)
  }

  @Test
  fun createGlassRimBrushProvider_mergedAndStandalone_useDistinctEffects() {
    assertThat(rimProvider(merged = true).effect).isNotSameInstanceAs(rimProvider(merged = false).effect)
  }

  @Test
  fun createGlassRimBrushProvider_interleavedProviders_drawTheirOwnKeys() {
    val a = rimProvider(merged = false)
    val b = rimProvider(merged = false)
    val keyA = rimKey().copy(lightPosition = Offset(110f, 85f), specularIntensity = 0.3f)
    val keyA2 = rimKey().copy(specularIntensity = 0f, edgeShadow = Color(0.9f, 0.1f, 0.1f, 0.9f))
    val keyB = rimKey().copy(specularIntensity = 0f, edgeShadow = Color(0.1f, 0.9f, 0.1f, 0.9f))

    a(keyA)
    val brushB = checkNotNull(b(keyB))
    val brushA = checkNotNull(a(keyA2))

    assertThat(maxChannelDifference(drawBrush(brushB), drawFilter(keyB))).isLessThanOrEqualTo(1f / 255f)
    assertThat(maxChannelDifference(drawBrush(brushA), drawFilter(keyA2))).isLessThanOrEqualTo(1f / 255f)
  }

  private fun rimProvider(merged: Boolean): SkiaGlassRimBrushProvider {
    val provider = checkNotNull(createGlassRimBrushProvider(merged))
    assertThat(provider).isInstanceOf<SkiaGlassRimBrushProvider>()
    return provider as SkiaGlassRimBrushProvider
  }

  private fun mergedBackgroundProvider(): SkiaGlassMergedBackgroundShaderProvider {
    val provider = checkNotNull(createGlassMergedBackgroundShaderProvider())
    assertThat(provider).isInstanceOf<SkiaGlassMergedBackgroundShaderProvider>()
    return provider as SkiaGlassMergedBackgroundShaderProvider
  }

  private fun drawBrush(brush: Brush, canvasScale: Float = 1f): ImageBitmap = ImageBitmap(128, 96).also { image ->
    val canvas = Canvas(image).apply { scale(canvasScale, canvasScale) }
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(128f, 96f) / canvasScale) {
      assertThat(drawGlassRimWithBrush(brush)).isTrue()
    }
  }

  private fun drawFilter(key: GlassRimEffectKey): ImageBitmap = ImageBitmap(128, 96).also { image ->
    val effect = createRuntimeShaderRenderEffect(
      RuntimeEffect.makeForShader(GlassShaders.buildRim()),
      arrayOf("content"),
      arrayOf(null),
    ) { setRimUniforms(key, rasterized = true) }
    val bounds = Rect(Offset.Zero, key.coordinates.sampleSize)
    val paint = Paint().apply { skiaPaint.imageFilter = effect }
    with(Canvas(image)) {
      saveLayer(bounds, paint)
      drawRect(bounds, Paint().apply { color = Color.Black })
      restore()
    }
  }

  private fun maxChannelDifference(first: ImageBitmap, second: ImageBitmap): Float {
    val a = first.toPixelMap()
    val b = second.toPixelMap()
    var difference = 0f
    for (y in 0 until a.height) {
      for (x in 0 until a.width) {
        difference = maxOf(
          difference,
          abs(a[x, y].red - b[x, y].red),
          abs(a[x, y].green - b[x, y].green),
          abs(a[x, y].blue - b[x, y].blue),
          abs(a[x, y].alpha - b[x, y].alpha),
        )
      }
    }
    return difference
  }

  // A rectangle with production's sample step: the shape's coverage spans one pixel.
  private fun nodeBoundEdgeKey() = rimKey().copy(
    coordinates = rimKey().coordinates.copy(pixelAligned = true),
    specularIntensity = 0f,
    edgeShadow = Color.Transparent,
    cornerRadii = CornerRadii(0f, 0f, 0f, 0f),
    sampleStepPx = 2f,
  )

  private fun GlassRimEffectKey.notPixelAligned() = copy(coordinates = coordinates.copy(pixelAligned = false))

  private fun outlineKey() = rimKey().copy(
    specularIntensity = 0f,
    edgeShadow = Color.Transparent,
    edgeOutline = GlassEdgeOutline(Color.Green, 3f),
  )

  private fun circleOutlineKey() = outlineKey().copy(
    coordinates = GlassCoordinates(Size(128f, 96f), Offset(8f, 6f), Size(84f, 84f), 1f),
    cornerRadii = CornerRadii(42f, 42f, 42f, 42f),
  )

  private fun GlassRimEffectKey.withOutlineStrength(strength: Float) =
    copy(edgeOutline = edgeOutline.copy(topAndBottomStrength = strength))

  private fun rimKey() = GlassRimEffectKey(
    coordinates = GlassCoordinates(Size(128f, 96f), Offset(8f, 6f), Size(112f, 84f), 1f),
    specularIntensity = 0.8f,
    edgeShadow = Color(0.1f, 0.2f, 0.3f, 0.6f),
    specularExponent = 3f,
    edgeSoftnessPx = 2f,
    cornerRadii = CornerRadii(18f, 18f, 18f, 18f),
    lightPosition = Offset(12f, 10f),
    sampleStepPx = 1f,
  )
}
