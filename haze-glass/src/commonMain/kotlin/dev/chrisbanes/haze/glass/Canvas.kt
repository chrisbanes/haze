// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.roundToIntSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.InternalHazeApi
import kotlin.math.max

internal fun DrawScope.recordDepthMix(
  layer: GraphicsLayer,
  size: IntSize,
  source: GraphicsLayer,
  blurred: GraphicsLayer,
  depth: Float,
) {
  recordDepthMix(
    layer = layer,
    size = size,
    depth = depth,
    drawSource = { drawLayer(source) },
    drawBlurred = { drawLayer(blurred) },
  )
}

internal fun DrawScope.recordDepthMix(
  layer: GraphicsLayer,
  size: IntSize,
  depth: Float,
  drawSource: DrawScope.() -> Unit,
  drawBlurred: DrawScope.() -> Unit,
) {
  layer.compositingStrategy = CompositingStrategy.Offscreen
  layer.record(size) {
    withLayerPaint(alpha = 1f - depth, blendMode = BlendMode.SrcOver) {
      drawSource()
    }
    withLayerPaint(alpha = depth, blendMode = BlendMode.Plus) {
      drawBlurred()
    }
  }
}

private inline fun DrawScope.withLayerPaint(
  alpha: Float,
  blendMode: BlendMode,
  block: DrawScope.() -> Unit,
) {
  val canvas = drawContext.canvas
  val paint = Paint().apply {
    this.alpha = alpha
    this.blendMode = blendMode
  }
  canvas.saveLayer(Rect(Offset.Zero, size), paint)
  try {
    block()
  } finally {
    canvas.restore()
  }
}

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal fun DrawScope.drawInputWithAlpha(
  context: HazeEffectRuntimeDrawScope,
  alpha: Float,
) {
  withLayerPaint(alpha = alpha, blendMode = BlendMode.SrcOver) {
    with(context) { this@drawInputWithAlpha.drawInput() }
  }
}

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal fun DrawScope.createScaledContentLayer(
  context: HazeEffectRuntimeDrawScope,
  backgroundColor: Color,
  scaleFactor: Float,
  layerSize: Size,
  existingLayer: GraphicsLayer? = null,
): GraphicsLayer? {
  val scaledLayerSize = (layerSize * scaleFactor).roundToIntSize()

  if (scaledLayerSize.width <= 0 || scaledLayerSize.height <= 0) {
    return null
  }

  val graphicsContext = context.requireGraphicsContext()
  val layer = existingLayer?.takeUnless { it.isReleased }
    ?: graphicsContext.createGraphicsLayer()

  layer.record(size = scaledLayerSize) {
    if (backgroundColor.alpha > 0f) {
      drawRect(backgroundColor)
    }

    scale(scale = scaleFactor, pivot = Offset.Zero) {
      with(context) { this@record.drawInput() }
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
  withTransform({ if (clip) clipRect() }) {
    translate(offset) {
      scale(scale = scaleFactor, pivot = Offset.Zero) {
        block()
      }
    }
  }
}

internal inline fun DrawScope.recordAndDrawGlassGroupAlpha(
  layer: GraphicsLayer,
  alpha: Float,
  size: IntSize,
  crossinline block: DrawScope.() -> Unit,
) {
  layer.alpha = alpha
  layer.blendMode = BlendMode.SrcOver
  layer.compositingStrategy = CompositingStrategy.Offscreen
  layer.renderEffect = null
  layer.record(size = size) { block() }
  drawLayer(layer)
}

internal inline fun DrawScope.translate(
  offset: Offset,
  block: DrawScope.() -> Unit,
) {
  if (offset != Offset.Zero) {
    translate(offset.x, offset.y, block)
  } else {
    block()
  }
}
