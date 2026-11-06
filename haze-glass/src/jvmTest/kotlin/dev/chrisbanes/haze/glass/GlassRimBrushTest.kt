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
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.math.abs
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
  fun directRim_continuousCorners_edgeOutlineFollowsSmoothedShape() {
    val key = outlineKey().copy(cornerRadii = CornerRadii(18f, 18f, 18f, 18f, 1f, 1f, 1f, 1f))
    val provider = checkNotNull(createGlassRimBrushProvider(merged = false, continuous = true))
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

  private fun drawBrush(brush: Brush): ImageBitmap = ImageBitmap(128, 96).also { image ->
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(128f, 96f)) {
      assertThat(drawGlassRimWithBrush(brush)).isTrue()
    }
  }

  private fun drawFilter(key: GlassRimEffectKey): ImageBitmap = ImageBitmap(128, 96).also { image ->
    val effect = createRuntimeShaderRenderEffect(
      RuntimeEffect.makeForShader(GlassShaders.buildRim()),
      arrayOf("content"),
      arrayOf(null),
    ) { setRimUniforms(key) }
    val bounds = Rect(0f, 0f, 128f, 96f)
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

  private fun outlineKey() = rimKey().copy(
    specularIntensity = 0f,
    edgeShadow = Color.Transparent,
    edgeOutline = GlassEdgeOutline(Color.Green, 3f),
  )

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
