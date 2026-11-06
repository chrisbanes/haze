// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import kotlin.math.abs
import kotlin.test.Test

class GlassRimHighlightShaderTest {
  @Test
  fun sidesZero_drawsTopAndBottomOnly() {
    val pixels = draw(highlightKey(sides = 0f))

    assertThat(pixels[64, 7].alpha).isGreaterThan(0.6f)
    assertThat(pixels[64, 88].alpha).isGreaterThan(0.6f)
    assertThat(pixels[9, 48].alpha).isLessThan(0.02f)
    assertThat(pixels[64, 12].alpha).isLessThan(0.02f)
  }

  @Test
  fun continuous_sidesZero_drawsTopAndBottomOnly() {
    val key = highlightKey(sides = 0f).copy(cornerRadii = CornerRadii(18f, 18f, 18f, 18f, 1f, 1f, 1f, 1f))
    val pixels = draw(key)

    // The band follows the smoothed corners rather than the circular ones.
    assertThat(maxChannelDifference(pixels, draw(highlightKey(sides = 0f)))).isGreaterThan(0.1f)

    assertThat(pixels[64, 7].alpha).isGreaterThan(0.6f)
    assertThat(pixels[64, 88].alpha).isGreaterThan(0.6f)
    assertThat(pixels[9, 48].alpha).isLessThan(0.02f)
    assertThat(pixels[64, 12].alpha).isLessThan(0.02f)
  }

  @Test
  fun sidesOne_isUniform() {
    val pixels = draw(highlightKey(sides = 1f))

    assertThat(pixels[9, 48].alpha).isCloseTo(pixels[64, 7].alpha, 0.02f)
  }

  @Test
  fun ignoresLightPosition() {
    val key = highlightKey(sides = 0.5f)

    val centred = draw(key.copy(lightPosition = Offset(64f, 48f)))
    val moved = draw(key.copy(lightPosition = Offset(12f, 10f)))

    assertThat(maxChannelDifference(centred, moved)).isLessThanOrEqualTo(1f / 255f)
  }

  @Test
  fun zeroIntensity_matchesNoHighlight() {
    val none = draw(rimKey())
    val zero = draw(rimKey().copy(rimHighlight = GlassRimHighlight(0f, 3f, 1f)))

    assertThat(maxChannelDifference(none, zero)).isEqualTo(0f)
  }

  @Test
  fun merged_followsFieldTopEdgeAndBridge() {
    val key = highlightKey(sides = 0f).copy(
      coordinates = GlassCoordinates(Size(128f, 96f), Offset.Zero, Size(128f, 96f), 1f),
      mergedField = mergedField(
        mergedMember(8f, 20f, 50f, 40f, radius = 12f),
        mergedMember(66f, 20f, 50f, 40f, radius = 12f),
        mergeDistancePx = 40f,
      ),
    )

    val pixels = draw(key, merged = true)

    assertThat(pixels[33, 21].alpha).isGreaterThan(0.6f)
    assertThat(pixels[91, 21].alpha).isGreaterThan(0.6f)
    assertThat(pixels[9, 40].alpha).isLessThan(0.02f)
  }

  private fun draw(key: GlassRimEffectKey, merged: Boolean = false): PixelMap {
    val provider = checkNotNull(createGlassRimBrushProvider(merged, continuousCorners(key.cornerRadii, key.mergedField)))
    return drawBrush(checkNotNull(provider(key))).toPixelMap()
  }

  private fun drawBrush(brush: Brush): ImageBitmap = ImageBitmap(128, 96).also { image ->
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(128f, 96f)) {
      assertThat(drawGlassRimWithBrush(brush)).isTrue()
    }
  }

  private fun maxChannelDifference(first: PixelMap, second: PixelMap): Float {
    var difference = 0f
    for (y in 0 until first.height) {
      for (x in 0 until first.width) {
        difference = maxOf(
          difference,
          abs(first[x, y].red - second[x, y].red),
          abs(first[x, y].green - second[x, y].green),
          abs(first[x, y].blue - second[x, y].blue),
          abs(first[x, y].alpha - second[x, y].alpha),
        )
      }
    }
    return difference
  }

  private fun highlightKey(sides: Float) = rimKey().copy(
    specularIntensity = 0f,
    edgeShadow = Color.Transparent,
    rimHighlight = GlassRimHighlight(intensity = 0.8f, widthPx = 3f, sides = sides),
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
