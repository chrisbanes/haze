// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.withGraphicsLayer
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalHazeApi::class)
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
    backgroundColor = Color.Transparent,
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

@OptIn(ExperimentalHazeApi::class)
internal fun DrawScope.createScaledContentLayer(
  context: VisualEffectContext,
  backgroundColor: Color,
  scaleFactor: Float,
  layerSize: Size,
  layerOffset: Offset,
): GraphicsLayer? {
  val scaledLayerSize = (layerSize * scaleFactor).roundToIntSize()

  if (scaledLayerSize.width <= 0 || scaledLayerSize.height <= 0) {
    return null
  }

  val graphicsContext = context.requireGraphicsContext()
  val layer = graphicsContext.createGraphicsLayer()

  layer.record(size = scaledLayerSize) {
    if (backgroundColor.alpha > 0f) {
      drawRect(backgroundColor)
    }

    scale(scale = scaleFactor, pivot = Offset.Zero) {
      translate(layerOffset - context.positionOnScreen) {
        for (area in context.areas) {
          val position = Snapshot.withoutReadObservation { area.positionOnScreen }
          val resolvedPosition = if (position.isSpecified) position else Offset.Zero
          translate(resolvedPosition) {
            val areaLayer = area.contentLayer
              ?.takeUnless { it.isReleased }
              ?.takeUnless { it.size.width <= 0 || it.size.height <= 0 }

            if (areaLayer != null) {
              drawLayer(areaLayer)
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

internal inline fun DrawScope.withAlpha(
  alpha: Float,
  context: VisualEffectContext,
  crossinline block: DrawScope.() -> Unit,
) {
  if (alpha < 1f) {
    context.withGraphicsLayer { layer ->
      layer.alpha = alpha
      layer.record { block() }
      drawLayer(layer)
    }
  } else {
    block()
  }
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

private fun Size.roundToIntSize(): androidx.compose.ui.unit.IntSize {
  return androidx.compose.ui.unit.IntSize(width.roundToInt(), height.roundToInt())
}
