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
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.toIntSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.translate

/**
 * Draws a color effect with optional mask.
 *
 * A masked effect is recorded with its masks into [maskedLayer], then composited once against the
 * real destination with the effect's blend mode and [alpha]. Recording the blend inside the layer
 * would blend against transparent pixels instead of the content behind the effect.
 *
 * @param colorEffect The color effect to draw
 * @param context The visual effect context
 * @param offset Offset to translate the effect by
 * @param expandedSize Size for drawing (defaults to canvas size)
 * @param mask Optional brush mask to apply to the effect
 * @param progressiveMask Optional progressive alpha mask, multiplied with [mask]
 * @param maskedLayer Holder for the layer a masked tint is recorded into; released when unmasked
 * @param alpha The effect alpha, applied once when the effect is composited
 */
internal fun DrawScope.drawScrim(colorEffect: HazeColorEffect, context: HazeEffectRuntimeDrawScope, offset: Offset = Offset.Zero, expandedSize: Size = this.size, mask: Brush? = null, progressiveMask: Brush? = null, maskedLayer: RetainedGraphicsLayer? = null, alpha: Float = 1f) {
  val masked = mask != null || progressiveMask != null
  if (!masked) maskedLayer?.release()
  when (colorEffect) {
    is TintBrushHazeColorEffect -> {
      if (masked) {
        drawMaskedLayer(checkNotNull(maskedLayer), context, offset, colorEffect.blendMode, alpha) {
          drawRect(brush = colorEffect.brush)
          drawMasks(progressiveMask, mask)
        }
      } else {
        // Translate rather than offset the rect, so the brush stays node-local like the masked branch.
        translate(offset) {
          drawRect(brush = colorEffect.brush, size = size, alpha = alpha, blendMode = colorEffect.blendMode)
        }
      }
    }
    is TintColorHazeColorEffect -> {
      if (masked) {
        drawMaskedLayer(checkNotNull(maskedLayer), context, offset, colorEffect.blendMode, alpha) {
          drawRect(color = colorEffect.color)
          drawMasks(progressiveMask, mask)
        }
      } else {
        drawRect(
          color = colorEffect.color,
          size = expandedSize,
          alpha = alpha,
          blendMode = colorEffect.blendMode,
        )
      }
    }
    is ColorFilterHazeColorEffect -> {
      if (masked) {
        drawMaskedLayer(checkNotNull(maskedLayer), context, offset, colorEffect.blendMode, alpha) {
          drawRect(color = Color.White, colorFilter = colorEffect.colorFilter)
          drawMasks(progressiveMask, mask)
        }
      } else {
        drawRect(
          color = Color.White,
          size = expandedSize,
          alpha = alpha,
          colorFilter = colorEffect.colorFilter,
          blendMode = colorEffect.blendMode,
        )
      }
    }
  }
}

/** Records [block] into [retainedLayer] and composites it at [offset] with [blendMode] and [alpha]. */
private inline fun DrawScope.drawMaskedLayer(
  retainedLayer: RetainedGraphicsLayer,
  context: HazeEffectRuntimeDrawScope,
  offset: Offset,
  blendMode: BlendMode,
  alpha: Float,
  crossinline block: DrawScope.() -> Unit,
) {
  val layer = retainedLayer.acquire(context.requireGraphicsContext())
  layer.compositingStrategy = CompositingStrategy.Offscreen
  layer.record(size = size.toIntSize()) { block() }
  translate(offset) {
    PaintPool.usePaint { paint ->
      paint.blendMode = blendMode
      paint.alpha = alpha
      val canvas = drawContext.canvas
      canvas.saveLayer(Rect(Offset.Zero, size), paint)
      drawLayer(layer)
      canvas.restore()
    }
  }
}

private fun DrawScope.drawMasks(first: Brush?, second: Brush?) {
  if (first != null) drawRect(brush = first, blendMode = BlendMode.DstIn)
  if (second != null) drawRect(brush = second, blendMode = BlendMode.DstIn)
}
