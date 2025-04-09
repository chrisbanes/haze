// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.HazeEffectNode.Companion.TAG

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

    if (node.alpha != 1f) {
      PaintPool.usePaint { paint ->
        paint.alpha = node.alpha
        drawContext.canvas.withSaveLayer(size.toRect(), paint) {
          drawScrim(node.mask, node.progressive, scrimTint)
        }
      }
    } else {
      drawScrim(node.mask, node.progressive, scrimTint)
    }
  }
}

internal fun DrawScope.drawScrim(
  mask: Brush?,
  progressive: HazeProgressive?,
  tint: HazeTint,
) {
  if (tint.brush != null) {
    val maskingShader = when {
      mask is ShaderBrush -> mask.createShader(size)
      progressive != null -> (progressive.asBrush() as? ShaderBrush)?.createShader(size)
      else -> null
    }

    if (maskingShader != null) {
      PaintPool.usePaint { outerPaint ->
        drawContext.canvas.withSaveLayer(size.toRect(), outerPaint) {
          drawRect(brush = tint.brush, blendMode = tint.blendMode)

          PaintPool.usePaint { maskPaint ->
            maskPaint.shader = maskingShader
            maskPaint.blendMode = BlendMode.DstIn
            drawContext.canvas.drawRect(size.toRect(), maskPaint)
          }
        }
      }
    } else {
      drawRect(brush = tint.brush, blendMode = tint.blendMode)
    }
  } else {
    if (mask != null) {
      drawRect(brush = mask, colorFilter = ColorFilter.tint(tint.color))
    } else if (progressive != null) {
      drawRect(brush = progressive.asBrush(), colorFilter = ColorFilter.tint(tint.color))
    } else {
      drawRect(color = tint.color, blendMode = tint.blendMode)
    }
  }
}

@OptIn(ExperimentalHazeApi::class)
internal class RenderEffectBlurEffect(
  private val node: HazeEffectNode,
) : BlurEffect {
  private var renderEffect: RenderEffect? = null

  override fun DrawScope.drawEffect() {
    drawScaledContentLayer(node) { layer ->
      val p = node.progressive
      if (p != null) {
        node.drawProgressiveEffect(
          drawScope = this,
          progressive = p,
          contentLayer = layer,
        )
      } else {
        // First make sure that the RenderEffect is updated (if necessary)
        updateRenderEffectIfDirty(node)

        layer.renderEffect = renderEffect
        layer.alpha = node.alpha

        // Since we included a border around the content, we need to translate so that
        // we don't see it (but it still affects the RenderEffect)
        drawLayer(layer)
      }
    }
  }

  private fun updateRenderEffectIfDirty(node: HazeEffectNode) {
    if (renderEffect == null || node.dirtyTracker.any(DirtyFields.RenderEffectAffectingFlags)) {
      renderEffect = node.getOrCreateRenderEffect()
    }
  }
}

internal fun DrawScope.drawScaledContentLayer(
  node: HazeEffectNode,
  block: DrawScope.(GraphicsLayer) -> Unit,
) {
  val scaleFactor = node.calculateInputScaleFactor()
  val inflatedSize = (node.layerSize * scaleFactor).roundToIntSize()
  // This is the topLeft in the inflated bounds where the real are should be at [0,0]
  val inflatedOffset = node.layerOffset

  if (inflatedSize.width <= 0 || inflatedSize.height <= 0) {
    // If we have a 0px dimension we can't do anything so just return
    return
  }

  val bg = node.resolveBackgroundColor()
  require(bg.isSpecified) { "backgroundColor not specified. Please provide a color." }

  // Now we need to draw `contentNode` into each of an 'effect' graphic layers.
  // The RenderEffect applied will provide the blurring effect.
  val graphicsContext = node.currentValueOf(LocalGraphicsContext)
  val layer = graphicsContext.createGraphicsLayer()

  layer.record(size = inflatedSize) {
    drawRect(bg)

    clipRect {
      scale(scale = scaleFactor, pivot = Offset.Zero) {
        translate(inflatedOffset - node.positionOnScreen) {
          for (area in node.areas) {
            require(!area.contentDrawing) {
              "Modifier.haze nodes can not draw Modifier.hazeChild nodes. " +
                "This should not happen if you are providing correct values for zIndex on Modifier.haze. " +
                "Alternatively you can use can `canDrawArea` to to filter out parent areas."
            }

            val effectNodeBounds = Rect(node.positionOnScreen, node.size)
            val areaBounds = Snapshot.withoutReadObservation { area.bounds }
            if (areaBounds == null || !effectNodeBounds.overlaps(areaBounds)) {
              HazeLogger.d(TAG) { "Area does not overlap us. Skipping... $area" }
              continue
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
  }

  clipRect {
    translate(-inflatedOffset) {
      scale(1f / scaleFactor, Offset.Zero) {
        block(layer)
      }
    }
  }

  graphicsContext.releaseGraphicsLayer(layer)
}
