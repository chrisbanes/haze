// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.InternalHazeApi
import kotlin.math.ceil
import kotlin.math.floor

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
  recordGlassLayer(layer, size) {
    withLayerPaint(alpha = 1f - depth, blendMode = BlendMode.SrcOver) {
      drawSource()
    }
    withLayerPaint(alpha = depth, blendMode = BlendMode.Plus) {
      drawBlurred()
    }
  }
}

internal inline fun DrawScope.withLayerPaint(
  alpha: Float,
  blendMode: BlendMode,
  bounds: Rect = Rect(Offset.Zero, size),
  block: DrawScope.() -> Unit,
) {
  val canvas = drawContext.canvas
  glassLayerBoundsForTest?.invoke(bounds)
  val paint = LayerPaints.acquire(alpha, blendMode)
  try {
    canvas.saveLayer(bounds, paint)
    try {
      block()
    } finally {
      canvas.restore()
    }
  } finally {
    LayerPaints.release()
  }
}

/** Receives every [withLayerPaint] bound before its `saveLayer`, so tests can check layer sizes. */
internal var glassLayerBoundsForTest: ((Rect) -> Unit)? = null

/**
 * The part of this rect within [visible], rounded out to whole pixels, or [Rect.Zero] when they
 * do not overlap. A null [visible] returns this rect unchanged.
 */
internal fun Rect.visiblePart(visible: Rect?): Rect {
  if (visible == null) return this
  val part = intersect(visible)
  if (part.isEmpty) return Rect.Zero
  return Rect(floor(part.left), floor(part.top), ceil(part.right), ceil(part.bottom))
}

/**
 * Paints for [withLayerPaint], one per nesting depth. A layer keeps its paint for the whole
 * block, so an inner layer takes the next paint instead of changing an enclosing layer's alpha or
 * blend mode. Drawing is single-threaded, so this is not thread-safe, like haze-blur's `PaintPool`.
 */
internal object LayerPaints {
  private val paints = ArrayList<Paint>()
  private var depth = 0

  fun acquire(alpha: Float, blendMode: BlendMode): Paint {
    if (depth == paints.size) paints += Paint()
    return paints[depth++].also {
      it.alpha = alpha
      it.blendMode = blendMode
    }
  }

  fun release() {
    depth--
  }
}

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal fun DrawScope.drawInputWithAlpha(
  context: HazeEffectRuntimeDrawScope,
  alpha: Float,
  bounds: Rect,
) {
  withLayerPaint(alpha = alpha, blendMode = BlendMode.SrcOver, bounds = bounds) {
    // drawInput() draws in expanded-layer space, but this draw is in effect space.
    translate(-context.layerOffset.x, -context.layerOffset.y) {
      with(context) { this@translate.drawInput() }
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
  recordGlassLayer(layer, size) { block() }
  drawLayer(layer)
}

// GraphicsLayer caches its outline independently of its recording size. Refresh it for retained
// stages in their own recording space, including local patches and output-space alpha groups.
internal inline fun DrawScope.recordGlassLayer(
  layer: GraphicsLayer,
  size: IntSize,
  crossinline block: DrawScope.() -> Unit,
) {
  layer.record(size = size) { block() }
  layer.setRectOutline(size = size.toSize())
}
