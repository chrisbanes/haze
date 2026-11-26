// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class, ExperimentalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.translate

/**
 * Draws a color effect with optional mask.
 *
 * A masked effect is drawn with its masks inside a canvas layer, which is composited once against
 * the real destination with the effect's blend mode and [alpha]. Blending inside the layer would
 * blend against transparent pixels instead of the content behind the effect.
 *
 * Blend modes that change the destination under a transparent source would erase the content
 * where the mask or [alpha] removes the effect, so a masked or faded effect with one of them takes
 * its own route towards `(1 − c)·dst + c·blend(src, dst)`, where `c` is the mask coverage times
 * [alpha]:
 * - [BlendMode.Src] blends inside the layer, which is composited with [BlendMode.SrcOver].
 * - [BlendMode.Modulate] and [BlendMode.SrcIn] are exact only for opaque tints.
 * - [BlendMode.DstIn] is exact for any tint.
 * - [BlendMode.Clear], [BlendMode.SrcOut] and [BlendMode.DstAtop] are unsupported: they draw at
 *   full coverage, ignoring the masks and [alpha].
 *
 * @param colorEffect The color effect to draw
 * @param offset Offset to translate the effect by
 * @param expandedSize Size for drawing (defaults to canvas size)
 * @param mask Optional brush mask to apply to the effect
 * @param progressiveMask Optional progressive alpha mask, multiplied with [mask]
 * @param alpha The effect alpha, applied once when the effect is composited
 */
internal fun DrawScope.drawScrim(colorEffect: HazeColorEffect, offset: Offset = Offset.Zero, expandedSize: Size = this.size, mask: Brush? = null, progressiveMask: Brush? = null, alpha: Float = 1f) {
  val blendMode = when (colorEffect) {
    is TintBrushHazeColorEffect -> colorEffect.blendMode
    is TintColorHazeColorEffect -> colorEffect.blendMode
    is ColorFilterHazeColorEffect -> colorEffect.blendMode
  }
  val masked = mask != null || progressiveMask != null
  if (!masked && !(blendMode.changesDestinationUnderTransparentSource() && alpha < 1f)) {
    return drawUnlayeredTint(colorEffect, blendMode, offset, expandedSize, alpha)
  }
  when (blendMode) {
    BlendMode.Clear, BlendMode.SrcOut, BlendMode.DstAtop ->
      drawUnlayeredTint(colorEffect, blendMode, offset, expandedSize, alpha = 1f)
    BlendMode.Src -> drawInSaveLayer(offset, BlendMode.SrcOver, alpha) {
      drawTint(colorEffect, BlendMode.Src)
      drawMasks(progressiveMask, mask)
    }
    // Apply coverage before the White fill, so uncovered pixels modulate the destination by 1.
    BlendMode.Modulate -> drawInSaveLayer(offset, BlendMode.Modulate, alpha = 1f) {
      drawTint(colorEffect, BlendMode.SrcOver, alpha)
      drawMasks(progressiveMask, mask)
      drawRect(Color.White, blendMode = BlendMode.DstOver)
    }
    BlendMode.SrcIn -> drawInSaveLayer(offset, BlendMode.SrcAtop, alpha) {
      drawTint(colorEffect, BlendMode.SrcOver)
      drawMasks(progressiveMask, mask)
    }
    // The layer holds `1 − srcAlpha`, masked after the tint, and erases that much destination.
    BlendMode.DstIn -> drawInSaveLayer(offset, BlendMode.DstOut, alpha) {
      drawRect(Color.Black)
      drawTint(colorEffect, BlendMode.DstOut)
      drawMasks(progressiveMask, mask)
    }
    else -> drawInSaveLayer(offset, blendMode, alpha) {
      drawTint(colorEffect, BlendMode.SrcOver)
      drawMasks(progressiveMask, mask)
    }
  }
}

private fun DrawScope.drawUnlayeredTint(
  colorEffect: HazeColorEffect,
  blendMode: BlendMode,
  offset: Offset,
  expandedSize: Size,
  alpha: Float,
) {
  if (colorEffect is TintBrushHazeColorEffect) {
    // Translate rather than offset the rect, so the brush stays node-local like the layered branch.
    translate(offset) { drawTint(colorEffect, blendMode, alpha) }
  } else {
    drawTint(colorEffect, blendMode, alpha, expandedSize)
  }
}

private fun DrawScope.drawTint(
  colorEffect: HazeColorEffect,
  blendMode: BlendMode,
  alpha: Float = 1f,
  size: Size = this.size,
) {
  when (colorEffect) {
    is TintBrushHazeColorEffect ->
      drawRect(brush = colorEffect.brush, size = size, alpha = alpha, blendMode = blendMode)
    is TintColorHazeColorEffect ->
      drawRect(color = colorEffect.color, size = size, alpha = alpha, blendMode = blendMode)
    is ColorFilterHazeColorEffect -> drawRect(
      color = Color.White,
      size = size,
      alpha = alpha,
      colorFilter = colorEffect.colorFilter,
      blendMode = blendMode,
    )
  }
}

/** Draws [block] inside a canvas layer at [offset], composited with [blendMode] and [alpha]. */
private inline fun DrawScope.drawInSaveLayer(
  offset: Offset,
  blendMode: BlendMode,
  alpha: Float,
  block: DrawScope.() -> Unit,
) {
  translate(offset) {
    PaintPool.usePaint { paint ->
      paint.blendMode = blendMode
      paint.alpha = alpha
      val canvas = drawContext.canvas
      canvas.saveLayer(Rect(Offset.Zero, size), paint)
      block()
      canvas.restore()
    }
  }
}

/** Whether this mode changes the destination where the source is fully transparent. */
private fun BlendMode.changesDestinationUnderTransparentSource(): Boolean = when (this) {
  BlendMode.Clear, BlendMode.Src, BlendMode.SrcIn, BlendMode.DstIn,
  BlendMode.SrcOut, BlendMode.DstAtop, BlendMode.Modulate,
  -> true
  else -> false
}

private fun DrawScope.drawMasks(first: Brush?, second: Brush?) {
  if (first != null) drawRect(brush = first, blendMode = BlendMode.DstIn)
  if (second != null) drawRect(brush = second, blendMode = BlendMode.DstIn)
}
