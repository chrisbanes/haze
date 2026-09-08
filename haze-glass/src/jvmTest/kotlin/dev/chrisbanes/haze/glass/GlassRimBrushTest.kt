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
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.math.abs
import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

class GlassRimBrushTest {
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
