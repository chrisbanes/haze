// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.skiaShader
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.PlatformRenderEffect
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.test.Test
import org.jetbrains.skia.Matrix33

class BlurGradientCoordinatesTest {
  @Test
  fun absoluteMask_fadesInsideHeaderAtBalancedScale() = assertFade(progressive = false)

  @Test
  fun absoluteProgressive_fadesInsideHeaderAtBalancedScale() = assertFade(progressive = true)

  @Test
  fun relativeEndpoints_preserveFadeAtBalancedScale() {
    assertFade(progressive = false, relative = true)
    assertFade(progressive = true, relative = true)
  }

  @Test
  fun sharedTranslatedShader_preservesGeometryAndPreviouslyCreatedEffects() {
    val size = Size(100.25f, 100.75f)
    val shader = (
      Brush.verticalGradient(
        listOf(Color.Black, Color.Transparent),
        startY = 60f,
        endY = 80f,
      ) as ShaderBrush
      ).createShader(size).skiaShader
      .makeWithLocalMatrix(Matrix33.makeTranslate(0f, 20f)).asComposeShader()
    var requestedSize: Size? = null
    val brush = object : ShaderBrush() {
      override fun createShader(size: Size): Shader {
        requestedSize = size
        return shader
      }
    }
    val retained = mutableListOf<Pair<RenderEffectParams, PlatformRenderEffect>>()
    for (scale in listOf(1f, 0.5f, 0.8f, 0.5f, 1f)) {
      val params = maskParams(brush, scale, size)
      retained += params to createRenderEffect(PlatformContext.INSTANCE, Density(1f), params)
      for ((oldParams, effect) in retained) {
        val pixels = raster(effect, oldParams, Color.White)
        val y = (90f * oldParams.scale).toInt()
        val expected = (100f - (y + 0.5f) / oldParams.scale) / 20f
        assertThat(pixels[pixels.width / 2, y].alpha, "shared shader scale=${oldParams.scale}").isCloseTo(expected, 0.01f)
      }
      assertThat(requestedSize, "logical shader size").isEqualTo(size)
    }
  }

  @Test
  fun radialMask_preservesFiniteCenterAndRadiusWithPadding() {
    for (scale in listOf(1f, 0.5f, sqrt(0.625f))) {
      val offset = Offset(8.4f, 6.6f)
      val params = maskParams(
        Brush.radialGradient(listOf(Color.Black, Color.Transparent), center = Offset(50f, 50f), radius = 40f),
        scale,
        offset = offset,
      )
      val pixels = raster(createRenderEffect(PlatformContext.INSTANCE, Density(1f), params), params, Color.White)
      val shift = (offset * scale).round()
      val y = shift.y.toInt() + (50f * scale).toInt()
      for (localX in listOf(50f, 70f, 85f)) {
        val x = shift.x.toInt() + (localX * scale).toInt()
        val dx = (x + 0.5f - shift.x) / scale - 50f
        val dy = (y + 0.5f - shift.y) / scale - 50f
        val expected = (1f - sqrt(dx * dx + dy * dy) / 40f).coerceIn(0f, 1f)
        assertThat(pixels[x, y].alpha, "radial scale=$scale x=$localX").isCloseTo(expected, 0.01f)
      }
    }
  }

  private fun assertFade(progressive: Boolean, relative: Boolean = false) {
    for (scale in listOf(1f, 0.5f, sqrt(0.625f))) {
      val height = ceil(100f * scale).toInt()
      val brush = Brush.verticalGradient(
        colors = listOf(Color.Black, Color.Transparent),
        startY = if (relative) 0f else 80f,
        endY = if (relative) Float.POSITIVE_INFINITY else 100f,
      )
      val effect = createRenderEffect(
        PlatformContext.INSTANCE,
        Density(1f),
        RenderEffectParams(
          blurRadius = 0.dp,
          noiseFactor = 0f,
          scale = scale,
          contentSize = Size(100f, 100f),
          contentOffset = Offset.Zero,
          colorEffects = if (progressive) listOf(HazeColorEffect.tint(Color.White)) else emptyList(),
          colorEffectsAlphaModulate = 1f,
          mask = if (progressive) null else brush,
          progressive = if (progressive) {
            HazeProgressive.verticalGradient(
              easing = LinearEasing,
              startY = if (relative) 0f else 80f,
              startIntensity = 1f,
              endY = if (relative) Float.POSITIVE_INFINITY else 100f,
              endIntensity = 0f,
            )
          } else {
            null
          },
          retainInputWhenMasked = false,
          blurTileMode = TileMode.Clamp,
        ),
      )
      val pixels = raster(effect, maskParams(brush, scale), if (progressive) Color.Black else Color.White)
      for (y in listOf((70f * scale).toInt(), (90f * scale).toInt(), (98f * scale).toInt())) {
        val expected = if (relative) {
          1f - (y + 0.5f) / (100f * scale)
        } else {
          ((100f - (y + 0.5f) / scale) / 20f).coerceIn(0f, 1f)
        }
        val actual = pixels[height / 2, y].let { if (progressive) it.red else it.alpha }
        assertThat(actual, "fade progressive=$progressive scale=$scale y=$y").isCloseTo(expected, 0.025f)
      }
    }
  }

  private fun maskParams(brush: Brush, scale: Float, size: Size = Size(100f, 100f), offset: Offset = Offset.Zero) = RenderEffectParams(
    blurRadius = 0.dp,
    noiseFactor = 0f,
    scale = scale,
    contentSize = size,
    contentOffset = offset,
    mask = brush,
    blurTileMode = TileMode.Clamp,
  )

  private fun raster(effect: PlatformRenderEffect, params: RenderEffectParams, color: Color): PixelMap {
    val width = ceil((params.contentSize.width + params.contentOffset.x * 2f) * params.scale).toInt()
    val height = ceil((params.contentSize.height + params.contentOffset.y * 2f) * params.scale).toInt()
    val bounds = Rect(0f, 0f, width.toFloat(), height.toFloat())
    return ImageBitmap(width, height).also { bitmap ->
      with(Canvas(bitmap)) {
        saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = effect })
        drawRect(bounds, Paint().apply { this.color = color })
        restore()
      }
    }.toPixelMap()
  }
}
