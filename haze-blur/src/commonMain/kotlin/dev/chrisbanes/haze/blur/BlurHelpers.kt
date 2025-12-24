// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class, ExperimentalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.toIntSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.withGraphicsLayer
import kotlin.math.max

@OptIn(InternalHazeApi::class)
/**
 * Draws a color effect with optional mask.
 *
 * Rendering order: content → color effect → mask → alpha → blendMode
 *
 * @param colorEffect The color effect to draw (tint color, tint brush, color filter, or unspecified)
 * @param context The visual effect context
 * @param offset Offset to translate the effect by
 * @param expandedSize Size for drawing (defaults to canvas size)
 * @param mask Optional brush mask to apply to the effect
 */
internal fun DrawScope.drawScrim(colorEffect: HazeColorEffect, context: VisualEffectContext, offset: Offset = Offset.Zero, expandedSize: Size = this.size, mask: Brush? = null) {
  when (colorEffect) {
    is HazeColorEffect.TintBrush -> {
      if (mask != null) {
        context.withGraphicsLayer { layer ->
          layer.compositingStrategy = CompositingStrategy.Offscreen
          layer.record(size = size.toIntSize()) {
            drawRect(brush = colorEffect.brush, blendMode = colorEffect.blendMode)
            drawRect(brush = mask, blendMode = BlendMode.DstIn)
          }
          translate(offset) {
            drawLayer(layer)
          }
        }
      } else {
        drawRect(
          brush = colorEffect.brush,
          topLeft = offset,
          size = size,
          blendMode = colorEffect.blendMode,
        )
      }
    }
    is HazeColorEffect.TintColor -> {
      if (mask != null) {
        // When we have a mask, combine the tint color with the mask
        context.withGraphicsLayer { layer ->
          layer.compositingStrategy = CompositingStrategy.Offscreen
          layer.record(size = size.toIntSize()) {
            drawRect(color = colorEffect.color, blendMode = colorEffect.blendMode)
            drawRect(brush = mask, blendMode = BlendMode.DstIn)
          }
          translate(offset) {
            drawLayer(layer)
          }
        }
      } else {
        drawRect(
          color = colorEffect.color,
          size = expandedSize,
          blendMode = colorEffect.blendMode,
        )
      }
    }
    is HazeColorEffect.ColorFilter -> {
      if (mask != null) {
        context.withGraphicsLayer { layer ->
          layer.compositingStrategy = CompositingStrategy.Offscreen
          layer.record(size = size.toIntSize()) {
            drawRect(color = Color.White, colorFilter = colorEffect.colorFilter)
            drawRect(brush = mask, blendMode = BlendMode.DstIn)
          }
          translate(offset) {
            val canvas = drawContext.canvas
            val paint = Paint().apply { blendMode = colorEffect.blendMode }
            val bounds = Rect(Offset.Zero, size)
            canvas.saveLayer(bounds, paint)
            drawLayer(layer)
            canvas.restore()
          }
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
    else -> {
      // Unspecified - do nothing
    }
  }
}

internal fun DrawScope.createAndDrawScaledContentLayer(
  context: VisualEffectContext,
  releaseLayerOnExit: Boolean = true,
  block: DrawScope.(GraphicsLayer) -> Unit,
) {
  val graphicsContext = context.requireGraphicsContext()

  val effect = context.visualEffect
  val scaleFactor = effect.calculateInputScaleFactor(context.inputScale)
  val clip = effect.shouldClip()

  val layer = createScaledContentLayer(
    context = context,
    scaleFactor = scaleFactor,
    layerSize = context.layerSize,
    layerOffset = context.layerOffset,
    backgroundColor = (effect as? BlurVisualEffect)?.backgroundColor ?: Color.Transparent,
  )

  if (layer != null) {
    layer.clip = clip

    drawScaledContent(
      offset = -context.layerOffset,
      scaledSize = size * scaleFactor,
      clip = clip,
    ) {
      block(layer)
    }

    if (releaseLayerOnExit) {
      graphicsContext.releaseGraphicsLayer(layer)
    }
  }
}

internal fun DrawScope.createScaledContentLayer(
  context: VisualEffectContext,
  backgroundColor: Color,
  scaleFactor: Float,
  layerSize: Size,
  layerOffset: Offset,
): GraphicsLayer? {
  val scaledLayerSize = (layerSize * scaleFactor).roundToIntSize()

  if (scaledLayerSize.width <= 0 || scaledLayerSize.height <= 0) {
    // If we have a 0px dimension we can't do anything so just return
    return null
  }

  // Now we need to draw `contentNode` into each of an 'effect' graphic layers.
  // The RenderEffect applied will provide the blurring effect.
  val graphicsContext = context.requireGraphicsContext()
  val layer = graphicsContext.createGraphicsLayer()

  layer.record(size = scaledLayerSize) {
    if (backgroundColor.isSpecified) {
      drawRect(backgroundColor)
    }

    scale(scale = scaleFactor, pivot = Offset.Zero) {
      translate(layerOffset - context.positionOnScreen) {
        for (area in context.areas) {
          val position = Snapshot.withoutReadObservation {
            area.positionOnScreen.takeOrElse { Offset.Zero }
          }
          translate(position) {
            // Draw the content into our effect layer. We do want to observe this via snapshot
            // state
            val areaLayer = area.contentLayer
              ?.takeUnless { it.isReleased }
              ?.takeUnless { it.size.width <= 0 || it.size.height <= 0 }

            if (areaLayer != null) {
              HazeLogger.d("Blur") { "Drawing HazeArea GraphicsLayer: $areaLayer" }
              drawLayer(areaLayer)
            } else {
              HazeLogger.d("Blur") { "HazeArea GraphicsLayer is not valid" }
            }
          }
        }
      }
    }
  }

  return layer
}

internal fun DrawScope.drawScaledContent(
  offset: Offset,
  scaledSize: Size,
  clip: Boolean = true,
  block: DrawScope.() -> Unit,
) {
  val scaleFactor = max(size.width / scaledSize.width, size.height / scaledSize.height)
  optionalClipRect(enabled = clip) {
    translate(offset) {
      scale(scale = scaleFactor, pivot = Offset.Zero) {
        block()
      }
    }
  }
}

private inline fun DrawScope.optionalClipRect(
  enabled: Boolean,
  left: Float = 0.0f,
  top: Float = 0.0f,
  right: Float = size.width,
  bottom: Float = size.height,
  clipOp: ClipOp = ClipOp.Intersect,
  block: DrawScope.() -> Unit,
) = withTransform(
  transformBlock = {
    if (enabled) {
      clipRect(left, top, right, bottom, clipOp)
    }
  },
  drawBlock = block,
)
