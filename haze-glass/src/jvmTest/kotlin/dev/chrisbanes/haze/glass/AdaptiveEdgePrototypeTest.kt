// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.math.abs
import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

class AdaptiveEdgePrototypeTest {
  private val key = GlassRimEffectKey(
    coordinates = GlassCoordinates(Size(128f, 96f), Offset(8f, 6f), Size(112f, 84f), 1f),
    specularIntensity = 0.8f,
    edgeShadow = Color(0.1f, 0.2f, 0.3f, 0.6f),
    specularExponent = 3f,
    edgeSoftnessPx = 2f,
    cornerRadii = CornerRadii(18f, 18f, 18f, 18f),
    lightPosition = Offset(12f, 10f),
    sampleStepPx = 1f,
  )
  private val outline = key.copy(
    specularIntensity = 0f,
    edgeShadow = Color.Transparent,
    edgeOutline = GlassEdgeOutline(Color.Red.copy(alpha = 0.5f), 3f),
  )

  @Test
  fun zeroGain_preservesFixedRimPixels() {
    assertThat(difference(render(key, Color.Gray, fixed = true), render(key, Color.Gray, rimGain = 0f))).isLessThanOrEqualTo(1f / 255f)
  }

  @Test
  fun transparentSource_retainsFixedStrength() {
    assertThat(difference(render(key, Color.Transparent, fixed = true), render(key, Color.Transparent))).isLessThanOrEqualTo(1f / 255f)
  }

  @Test
  fun translucentSource_unpremultipliesBeforeLuminance() {
    val source = Color(0.5f, 0.5f, 0.5f, 0.5f)
    val fixed = render(key, source, fixed = true)[8, 48].alpha
    val adaptive = render(key, source)[8, 48].alpha
    assertThat(fixed).isGreaterThan(0.05f)
    val linear = Math.pow((0.5 + 0.055) / 1.055, 2.4).toFloat()
    assertThat(abs(adaptive - (fixed * (0.5f + linear)).coerceIn(0f, 1f))).isLessThanOrEqualTo(2f / 255f)
  }

  @Test
  fun authoredRedOutline_preservesStraightHue() {
    val color = render(outline, Color.Gray, outlineGain = 1f)[6, 48]
    assertThat(color.alpha).isGreaterThan(0.05f)
    assertThat(abs(color.red - 1f)).isLessThanOrEqualTo(1f / 255f)
    assertThat(color.green).isLessThanOrEqualTo(1f / 255f)
    assertThat(color.blue).isLessThanOrEqualTo(1f / 255f)
  }

  @Test
  fun authoredOutlineAlpha_followsLocalLuminance() {
    val source = Color(0.9f, 0.9f, 0.9f)
    val fixed = render(outline, source, fixed = true)[6, 48].alpha
    val adaptive = render(outline, source, outlineGain = 1f)[6, 48].alpha
    assertThat(fixed).isGreaterThan(0.05f)
    val encoded = 230.0 / 255.0
    val linear = Math.pow((encoded + 0.055) / 1.055, 2.4).toFloat()
    assertThat(abs(adaptive - (fixed * (0.5f + linear)).coerceIn(0f, 1f))).isLessThanOrEqualTo(2f / 255f)
  }

  @Test
  fun rimGain_doesNotChangeOutlineWhenRimDisabled() {
    assertThat(
      difference(render(outline, Color.Gray, rimGain = 0f), render(outline, Color.Gray, rimGain = 1f)),
    ).isLessThanOrEqualTo(1f / 255f)
  }

  @Test
  fun outlineGain_doesNotChangeRimWhenOutlineDisabled() {
    assertThat(difference(render(key, Color.Gray, rimGain = 0f, outlineGain = 0f), render(key, Color.Gray, rimGain = 0f, outlineGain = 1f))).isLessThanOrEqualTo(1f / 255f)
  }

  @Test
  fun gainAboveOne_clampsToOne() {
    assertThat(difference(render(key, Color.Gray), render(key, Color.Gray, rimGain = 2f, outlineGain = 2f))).isLessThanOrEqualTo(1f / 255f)
  }

  @Test
  fun darkAndBrightSource_produceDifferentRimAlpha() {
    val dark = render(key, Color(0.1f, 0.1f, 0.1f))
    val bright = render(key, Color(0.9f, 0.9f, 0.9f))
    var difference = 0f
    for (y in 0 until 96) for (x in 0 until 128) difference = maxOf(difference, abs(dark[x, y].alpha - bright[x, y].alpha))
    assertThat(difference).isGreaterThan(3f / 255f)
  }

  @Test
  fun transparentOutline_hasZeroAlpha() {
    val transparent = outline.copy(edgeOutline = GlassEdgeOutline(Color.Transparent, 3f))
    assertThat(render(transparent, Color.Gray)[6, 48].alpha).isEqualTo(0f)
  }

  @Test
  fun interior_hasNoEdgeOutput() {
    assertThat(render(key, Color.Gray)[64, 48].alpha).isEqualTo(0f)
  }

  @Test
  fun splitSource_samplesWorkingCoordinatesInsteadOfMaterialCoordinates() {
    val fixed = render(key, Color(0.1f, 0.1f, 0.1f), fixed = true, split = true)
    val adaptive = render(key, Color(0.1f, 0.1f, 0.1f), split = true)
    for ((x, encoded) in listOf(60 to 26, 68 to 230)) {
      val a = fixed[x, 7].alpha
      assertThat(a).isGreaterThan(0.05f)
      val e = encoded / 255.0
      val linear = Math.pow((e + 0.055) / 1.055, 2.4).toFloat()
      assertThat(abs(adaptive[x, 7].alpha - (a * (0.5f + linear)).coerceIn(0f, 1f))).isLessThanOrEqualTo(2f / 255f)
    }
  }

  private fun render(
    key: GlassRimEffectKey,
    color: Color,
    rimGain: Float = 1f,
    outlineGain: Float = 1f,
    fixed: Boolean = false,
    split: Boolean = false,
  ): PixelMap {
    val source = if (fixed) GlassShaders.buildRim() else adaptiveEdgeShader()
    val effect = createRuntimeShaderRenderEffect(RuntimeEffect.makeForShader(source), arrayOf("content"), arrayOf(null)) {
      setRimUniforms(key)
      if (!fixed) {
        setFloatUniform("rimGain", rimGain)
        setFloatUniform("outlineGain", outlineGain)
      }
    }
    val image = ImageBitmap(128, 96)
    val bounds = Rect(0f, 0f, 128f, 96f)
    with(Canvas(image)) {
      saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = effect })
      drawRect(bounds, Paint().apply { this.color = color })
      if (split) drawRect(Rect(64f, 0f, 128f, 96f), Paint().apply { this.color = Color(0.9f, 0.9f, 0.9f) })
      restore()
    }
    return image.toPixelMap()
  }

  private fun difference(a: PixelMap, b: PixelMap): Float {
    var result = 0f
    for (y in 0 until a.height) for (x in 0 until a.width) {
      val first = a[x, y]
      val second = b[x, y]
      result = maxOf(result, abs(first.red - second.red), abs(first.green - second.green), abs(first.blue - second.blue), abs(first.alpha - second.alpha))
    }
    return result
  }
}
