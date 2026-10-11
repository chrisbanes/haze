// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import dev.chrisbanes.haze.HazePerformanceMode
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test

/** Renders a merged field through the production Skiko stage effects and uniform setters. */
class MergedGlassRenderTest {
  @Test
  fun bridge_coversMidpointOnlyBelowHalfSpacing() {
    assertThat(render(gap = 10f).alphaAt(bridgeMidpoint(10f)), "bridged midpoint").isGreaterThan(0.99f)
    assertThat(render(gap = 20f).alphaAt(bridgeMidpoint(20f)), "separate midpoint").isLessThan(0.01f)
    assertThat(render(gap = 30f).alphaAt(bridgeMidpoint(30f)), "separate midpoint").isLessThan(0.01f)
  }

  @Test
  fun bridge_blendsMemberTintsAtMidpoint() {
    // An 11 px gap puts the midpoint at a pixel centre, so both members are equally near.
    val pixels = render(gap = 11f)
    assertColor(pixels.colorAt(bridgeMidpoint(11f)), Color(0.5f, 0f, 0.5f))
    assertColor(pixels.colorAt(Offset(70f, 40f)), Color.Red)
    assertColor(pixels.colorAt(Offset(181f, 40f)), Color.Blue)
  }

  @Test
  fun balancedInputScale_keepsBridgeAtReducedScale() {
    val scale = HazePerformanceMode.Balanced.resolveGlassInputScale()
    assertThat(scale).isLessThan(1f)
    assertThat(render(gap = 10f, scale = scale).alphaAt(bridgeMidpoint(10f) * scale)).isGreaterThan(0.99f)
    assertThat(render(gap = 20f, scale = scale).alphaAt(bridgeMidpoint(20f) * scale)).isLessThan(0.01f)
  }

  @Test
  fun mergedBackground_fillsEachMemberAndBlendsAcrossBridge() {
    val field = mergedField(
      mergedMember(20f, 20f, 100f, 40f, backgroundColor = Color.Red),
      mergedMember(131f, 20f, 100f, 40f, backgroundColor = Color.Blue),
      spacingPx = SPACING,
    )
    val provider = createGlassMergedBackgroundShaderProvider()
    assertThat(provider).isNotNull()
    val shader = provider!!(Offset.Zero, field)
    assertThat(shader).isNotNull()
    val image = ImageBitmap(SAMPLE_SIZE.width.toInt(), SAMPLE_SIZE.height.toInt())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), SAMPLE_SIZE) {
      drawRect(ShaderBrush(shader!!))
    }
    val pixels = image.toPixelMap()
    assertColor(pixels.colorAt(Offset(70f, 40f)), Color.Red)
    assertColor(pixels.colorAt(Offset(180f, 40f)), Color.Blue)
    assertColor(pixels.colorAt(Offset(125.5f, 40.5f)), Color(0.5f, 0f, 0.5f))
  }

  private fun params(gap: Float, scale: Float): GlassRenderParams {
    val effect = GlassRuntimeEffect().apply {
      style = GlassStyle {
        ambientResponse(0f)
        edgeSoftness(0.dp)
        specularIntensity(0f)
      }
      mergedField = mergedField(
        mergedMember(20f, 20f, 100f, 40f, radius = 20f, tint = Color.Red),
        mergedMember(120f + gap, 20f, 100f, 40f, radius = 20f, tint = Color.Blue),
        spacingPx = SPACING,
        opticalSize = Size(100f, 40f),
      )
    }
    val style = resolveGlassStyle(effect, SAMPLE_SIZE, Density(1f), LayoutDirection.Ltr)
    return buildGlassRenderParams(
      style,
      GlassCoordinates(SAMPLE_SIZE * scale, Offset.Zero, SAMPLE_SIZE * scale, scale),
    )
  }

  private fun render(gap: Float, scale: Float = 1f): PixelMap {
    val params = params(gap, scale)
    val key = params.opticalEffectKey()
    val optical = createRetainedGlassOpticalRenderEffect(merged = true).updateUniforms {
      setOpticalUniforms(key)
    }
    val sampleSize = params.coordinates.sampleSize
    return ImageBitmap(sampleSize.width.roundToInt(), sampleSize.height.roundToInt()).also { image ->
      with(Canvas(image)) {
        val bounds = Rect(Offset.Zero, sampleSize)
        saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = optical })
        drawRect(bounds, Paint().apply { color = Color(0xFF131116) })
        restore()
      }
    }.toPixelMap()
  }

  /** The pixel whose area contains [point]; its centre is [point] when the point is at x.5. */
  private fun PixelMap.colorAt(point: Offset): Color = this[point.x.toInt(), point.y.toInt()]

  private fun PixelMap.alphaAt(point: Offset): Float = colorAt(point).alpha

  private fun assertColor(actual: Color, expected: Color) {
    assertThat(abs(actual.red - expected.red), "red: $actual").isLessThan(3f / 255f)
    assertThat(abs(actual.green - expected.green), "green: $actual").isLessThan(3f / 255f)
    assertThat(abs(actual.blue - expected.blue), "blue: $actual").isLessThan(3f / 255f)
    assertThat(abs(actual.alpha - expected.alpha), "alpha: $actual").isLessThan(3f / 255f)
  }

  private companion object {
    const val SPACING = 40f
    val SAMPLE_SIZE = Size(280f, 80f)

    fun bridgeMidpoint(gap: Float) = Offset(120f + gap / 2f, 40f)
  }
}
