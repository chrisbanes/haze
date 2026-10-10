// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.PlatformRenderEffect
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

/**
 * Pins the analytic tint model that the RenderScript delegate must share with RenderEffect: an
 * opaque tint covers the blurred content, so each pixel depends only on the tint, the brush
 * geometry and the mask, which is applied once.
 */
class BlurTintParityTest {

  private val gradientTint = HazeColorEffect.tint(Brush.verticalGradient(listOf(Color.Red, Color.Blue)))

  @Test
  fun drawScrim_unmaskedBrushTint_alignsWithOffsetNode() {
    val bitmap = ImageBitmap(100, 100)
    // The delegate draws the layer translated by -layerOffset and hands the offset to drawScrim.
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(100f, 100f)) {
      translate(Offset(-20f, -20f)) {
        drawScrim(
          colorEffect = gradientTint,
          context = UnusedDrawContext,
          offset = Offset(20f, 20f),
          expandedSize = Size(140f, 140f),
        )
      }
    }

    bitmap.toPixelMap().assertVerticalGradient(x = 50, rows = listOf(2, 50, 75), originY = 0, height = 100)
  }

  @Test
  fun renderEffect_unmaskedBrushTint_alignsWithOffsetNode() {
    val params = RenderEffectParams(
      blurRadius = 0.dp,
      noiseFactor = 0f,
      scale = 1f,
      contentSize = Size(100f, 100f),
      contentOffset = Offset(20f, 20f),
      colorEffects = listOf(gradientTint),
      blurTileMode = TileMode.Clamp,
    )

    val pixels = raster(params)

    // The node sits at (20, 20) inside the 140x140 layer.
    pixels.assertVerticalGradient(x = 70, rows = listOf(22, 70, 95), originY = 20, height = 100)
  }

  @Test
  fun renderEffect_maskedTint_appliesMaskOnce() {
    val params = RenderEffectParams(
      blurRadius = 0.dp,
      noiseFactor = 0f,
      scale = 1f,
      contentSize = Size(100f, 100f),
      contentOffset = Offset.Zero,
      colorEffects = listOf(HazeColorEffect.tint(Color.Red)),
      mask = Brush.verticalGradient(listOf(Color.Black, Color.Transparent)),
      blurTileMode = TileMode.Clamp,
    )

    val pixels = raster(params)

    for (y in listOf(25, 50, 75)) {
      val pixel = pixels[50, y]
      assertThat(pixel.alpha, "alpha y=$y").isCloseTo(1f - (y + 0.5f) / 100f, TOLERANCE)
      assertThat(pixel.red, "red y=$y").isCloseTo(1f, TOLERANCE)
    }
  }

  private fun PixelMap.assertVerticalGradient(x: Int, rows: List<Int>, originY: Int, height: Int) {
    for (y in rows) {
      val t = (y - originY + 0.5f) / height
      assertThat(this[x, y].red, "red y=$y").isCloseTo(1f - t, TOLERANCE)
      assertThat(this[x, y].blue, "blue y=$y").isCloseTo(t, TOLERANCE)
    }
  }

  private fun raster(params: RenderEffectParams): PixelMap {
    val effect = createRenderEffect(PlatformContext.INSTANCE, Density(1f), params)
    return rasterOverWhite(effect, params)
  }

  private fun rasterOverWhite(effect: PlatformRenderEffect, params: RenderEffectParams): PixelMap {
    val width = (params.contentSize.width + params.contentOffset.x * 2f).toInt()
    val height = (params.contentSize.height + params.contentOffset.y * 2f).toInt()
    val bounds = Rect(0f, 0f, width.toFloat(), height.toFloat())
    return ImageBitmap(width, height).also { bitmap ->
      with(Canvas(bitmap)) {
        saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = effect })
        drawRect(bounds, Paint().apply { color = Color.White })
        restore()
      }
    }.toPixelMap()
  }

  private companion object {
    const val TOLERANCE = 0.02f
  }
}

/** The unmasked [drawScrim] branches never touch the context, so every member is unused. */
private object UnusedDrawContext :
  HazeEffectRuntimeDrawScope,
  DrawScope by CanvasDrawScope() {
  override val modifierBounds: Rect get() = unused()
  override val sampling: HazeSampling get() = unused()
  override val modifierSize: Size get() = unused()
  override val layerSize: Size get() = unused()
  override val layerOffset: Offset get() = unused()
  override val hasDrawableInput: Boolean get() = unused()
  override val inputSnapshot: HazeEffectInputSnapshot? get() = unused()
  override val coroutineScope: CoroutineScope get() = unused()

  override fun requirePlatformContext(): PlatformContext = unused()
  override fun requireGraphicsContext(): GraphicsContext = unused()
  override fun invalidateDraw() = unused()
  override fun drawInput() = unused()
  override fun DrawScope.drawInput() = unused()
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = unused()
}

private fun unused(): Nothing = error("Unused by unmasked drawScrim")
