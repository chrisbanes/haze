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
 * Blend modes that change the destination under a transparent source, such as [BlendMode.Src],
 * would erase the content where the mask or [alpha] removes the effect. Those modes blend inside
 * the layer instead, and the layer is composited with [BlendMode.SrcOver].
 *
 * @param colorEffect The color effect to draw
 * @param context The visual effect context
 * @param offset Offset to translate the effect by
 * @param expandedSize Size for drawing (defaults to canvas size)
 * @param mask Optional brush mask to apply to the effect
 * @param progressiveMask Optional progressive alpha mask, multiplied with [mask]
 * @param maskedLayer Holder for the layer a masked or faded tint is recorded into; released when unused
 * @param alpha The effect alpha, applied once when the effect is composited
 */
internal fun DrawScope.drawScrim(colorEffect: HazeColorEffect, context: HazeEffectRuntimeDrawScope, offset: Offset = Offset.Zero, expandedSize: Size = this.size, mask: Brush? = null, progressiveMask: Brush? = null, maskedLayer: RetainedGraphicsLayer? = null, alpha: Float = 1f) {
  val blendMode = when (colorEffect) {
    is TintBrushHazeColorEffect -> colorEffect.blendMode
    is TintColorHazeColorEffect -> colorEffect.blendMode
    is ColorFilterHazeColorEffect -> colorEffect.blendMode
  }
  val blendInLayer = blendMode.changesDestinationUnderTransparentSource()
  val layered = mask != null || progressiveMask != null || (blendInLayer && alpha < 1f)
  if (!layered) maskedLayer?.release()
  when (colorEffect) {
    is TintBrushHazeColorEffect -> {
      if (layered) {
        drawMaskedLayer(checkNotNull(maskedLayer), context, offset, blendMode, blendInLayer, alpha) { layerBlendMode ->
          drawRect(brush = colorEffect.brush, blendMode = layerBlendMode)
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
      if (layered) {
        drawMaskedLayer(checkNotNull(maskedLayer), context, offset, blendMode, blendInLayer, alpha) { layerBlendMode ->
          drawRect(color = colorEffect.color, blendMode = layerBlendMode)
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
      if (layered) {
        drawMaskedLayer(checkNotNull(maskedLayer), context, offset, blendMode, blendInLayer, alpha) { layerBlendMode ->
          drawRect(color = Color.White, colorFilter = colorEffect.colorFilter, blendMode = layerBlendMode)
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

/**
 * Records [block] into [retainedLayer] and composites it at [offset] with [alpha]. [block] receives
 * the blend mode to draw with: [blendMode] when [blendInLayer], otherwise SrcOver, and the layer is
 * composited with the other one.
 */
private inline fun DrawScope.drawMaskedLayer(
  retainedLayer: RetainedGraphicsLayer,
  context: HazeEffectRuntimeDrawScope,
  offset: Offset,
  blendMode: BlendMode,
  blendInLayer: Boolean,
  alpha: Float,
  crossinline block: DrawScope.(layerBlendMode: BlendMode) -> Unit,
) {
  val layer = retainedLayer.acquire(context.requireGraphicsContext())
  layer.compositingStrategy = CompositingStrategy.Offscreen
  layer.record(size = size.toIntSize()) { block(if (blendInLayer) blendMode else BlendMode.SrcOver) }
  translate(offset) {
    PaintPool.usePaint { paint ->
      paint.blendMode = if (blendInLayer) BlendMode.SrcOver else blendMode
      paint.alpha = alpha
      val canvas = drawContext.canvas
      canvas.saveLayer(Rect(Offset.Zero, size), paint)
      drawLayer(layer)
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
