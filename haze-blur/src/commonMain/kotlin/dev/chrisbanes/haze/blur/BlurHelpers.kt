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
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.toIntSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.translate

@OptIn(InternalHazeApi::class)
/**
 * Draws a color effect with optional mask.
 *
 * Rendering order: content → color effect → mask → alpha → blendMode
 *
 * @param colorEffect The color effect to draw
 * @param context The visual effect context
 * @param offset Offset to translate the effect by
 * @param expandedSize Size for drawing (defaults to canvas size)
 * @param mask Optional brush mask to apply to the effect
 * @param progressiveMask Optional progressive alpha mask, multiplied with [mask]
 * @param maskedLayer Holder for the layer a masked tint is recorded into; released when unmasked
 */
internal fun DrawScope.drawScrim(colorEffect: HazeColorEffect, context: HazeEffectRuntimeDrawScope, offset: Offset = Offset.Zero, expandedSize: Size = this.size, mask: Brush? = null, progressiveMask: Brush? = null, maskedLayer: RetainedGraphicsLayer? = null) {
  val masked = mask != null || progressiveMask != null
  if (!masked) maskedLayer?.release()
  when (colorEffect) {
    is TintBrushHazeColorEffect -> {
      if (masked) {
        val layer = checkNotNull(maskedLayer).acquire(context.requireGraphicsContext())
        layer.compositingStrategy = CompositingStrategy.Offscreen
        layer.record(size = size.toIntSize()) {
          drawRect(brush = colorEffect.brush, blendMode = colorEffect.blendMode)
          drawMasks(progressiveMask, mask)
        }
        translate(offset) {
          drawLayer(layer)
        }
      } else {
        // Translate rather than offset the rect, so the brush stays node-local like the masked branch.
        translate(offset) {
          drawRect(brush = colorEffect.brush, size = size, blendMode = colorEffect.blendMode)
        }
      }
    }
    is TintColorHazeColorEffect -> {
      if (masked) {
        // When we have a mask, combine the tint color with the mask
        val layer = checkNotNull(maskedLayer).acquire(context.requireGraphicsContext())
        layer.compositingStrategy = CompositingStrategy.Offscreen
        layer.record(size = size.toIntSize()) {
          drawRect(color = colorEffect.color, blendMode = colorEffect.blendMode)
          drawMasks(progressiveMask, mask)
        }
        translate(offset) {
          drawLayer(layer)
        }
      } else {
        drawRect(
          color = colorEffect.color,
          size = expandedSize,
          blendMode = colorEffect.blendMode,
        )
      }
    }
    is ColorFilterHazeColorEffect -> {
      if (masked) {
        val layer = checkNotNull(maskedLayer).acquire(context.requireGraphicsContext())
        layer.compositingStrategy = CompositingStrategy.Offscreen
        layer.record(size = size.toIntSize()) {
          drawRect(color = Color.White, colorFilter = colorEffect.colorFilter)
          drawMasks(progressiveMask, mask)
        }
        translate(offset) {
          val canvas = drawContext.canvas
          val paint = Paint().apply { blendMode = colorEffect.blendMode }
          val bounds = Rect(Offset.Zero, size)
          canvas.saveLayer(bounds, paint)
          drawLayer(layer)
          canvas.restore()
        }
      } else {
        drawRect(
          color = Color.White,
          size = expandedSize,
          colorFilter = colorEffect.colorFilter,
          blendMode = colorEffect.blendMode,
        )
      }
    }
  }
}

private fun DrawScope.drawMasks(first: Brush?, second: Brush?) {
  if (first != null) drawRect(brush = first, blendMode = BlendMode.DstIn)
  if (second != null) drawRect(brush = second, blendMode = BlendMode.DstIn)
}
