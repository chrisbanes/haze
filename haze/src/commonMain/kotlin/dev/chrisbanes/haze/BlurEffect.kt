// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.toIntSize
import dev.chrisbanes.haze.HazeEffectNode.Companion.TAG
import kotlin.math.max

internal interface BlurEffect {
  fun DrawScope.drawEffect()
  fun cleanup() = Unit
}

@OptIn(ExperimentalHazeApi::class)
internal class ScrimBlurEffect(
  private val node: HazeEffectNode,
) : BlurEffect {
  override fun DrawScope.drawEffect() {
    val scrimTint = node.resolveFallbackTint().takeIf { it.isSpecified }
      ?: node.resolveTints().firstOrNull()
        ?.boostForFallback(node.resolveBlurRadius().takeOrElse { 0.dp })
      ?: return

    withAlpha(alpha = node.alpha, node = node) {
      drawScrim(tint = scrimTint, node = node, mask = node.mask ?: node.progressive?.asBrush())
    }
  }
}

internal fun DrawScope.drawScrim(
  tint: HazeTint,
  node: CompositionLocalConsumerModifierNode,
  offset: Offset = Offset.Zero,
  expandedSize: Size = this.size,
  mask: Brush? = null,
) {
  if (tint.brush != null) {
    if (mask != null) {
      node.withGraphicsLayer { layer ->
        layer.compositingStrategy = CompositingStrategy.Offscreen
        layer.record(size = size.toIntSize()) {
          drawRect(brush = tint.brush, blendMode = tint.blendMode)
          drawRect(brush = mask, blendMode = BlendMode.DstIn)
        }
        translate(offset) {
          drawLayer(layer)
        }
      }
    } else {
      drawRect(
        brush = tint.brush,
        topLeft = offset,
        size = size,
        blendMode = tint.blendMode,
      )
    }
  } else {
    if (mask != null) {
      drawRect(
        brush = mask,
        topLeft = offset,
        size = size,
        colorFilter = ColorFilter.tint(tint.color),
      )
    } else {
      drawRect(color = tint.color, size = expandedSize, blendMode = tint.blendMode)
    }
  }
}

internal fun DrawScope.createAndDrawScaledContentLayer(
  node: HazeEffectNode,
  scaleFactor: Float = node.calculateInputScaleFactor(),
  clip: Boolean = node.shouldClip(),
  releaseLayerOnExit: Boolean = true,
  block: DrawScope.(GraphicsLayer) -> Unit,
) {
  val graphicsContext = node.currentValueOf(LocalGraphicsContext)

  val layer = createScaledContentLayer(
    node = node,
    scaleFactor = scaleFactor,
    layerSize = node.layerSize,
    layerOffset = node.layerOffset,
  )

  if (layer != null) {
    layer.clip = clip

    drawScaledContent(
      offset = -node.layerOffset,
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
  node: HazeEffectNode,
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
  val graphicsContext = node.currentValueOf(LocalGraphicsContext)
  val layer = graphicsContext.createGraphicsLayer()

  layer.record(size = scaledLayerSize) {
    val bg = node.resolveBackgroundColor()
    if (bg.isSpecified) {
      drawRect(bg)
    }

    scale(scale = scaleFactor, pivot = Offset.Zero) {
      translate(layerOffset - node.positionOnScreen) {
        for (area in node.areas) {
          require(!area.contentDrawing) {
            "Modifier.haze nodes can not draw Modifier.hazeChild nodes. " +
              "This should not happen if you are providing correct values for zIndex on Modifier.haze. " +
              "Alternatively you can use can `canDrawArea` to to filter out parent areas."
          }

          val position = Snapshot.withoutReadObservation { area.positionOnScreen.orZero }
          translate(position) {
            // Draw the content into our effect layer. We do want to observe this via snapshot
            // state
            val areaLayer = area.contentLayer
              ?.takeUnless { it.isReleased }
              ?.takeUnless { it.size.width <= 0 || it.size.height <= 0 }

            if (areaLayer != null) {
              HazeLogger.d(TAG) { "Drawing HazeArea GraphicsLayer: $areaLayer" }
              drawLayer(areaLayer)
            } else {
              HazeLogger.d(TAG) { "HazeArea GraphicsLayer is not valid" }
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
