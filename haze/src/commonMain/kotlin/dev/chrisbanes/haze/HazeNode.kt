// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.takeOrElse

internal class HazeNode(
  var state: HazeState,
) : Modifier.Node(),
  CompositionLocalConsumerModifierNode,
  LayoutAwareModifierNode,
  GlobalPositionAwareModifierNode,
  DrawModifierNode {

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) = onPlaced(coordinates)

  override fun onPlaced(coordinates: LayoutCoordinates) {
    state.positionOnScreen = coordinates.positionInWindow() + calculateWindowOffset()
  }

  /**
   * We manually invalidate when things have changed
   */
  override val shouldAutoInvalidate: Boolean = false

  override fun ContentDrawScope.draw() {
    log(TAG) { "start draw()" }

    if (!USE_GRAPHICS_LAYERS) {
      // If we're not using graphics layers, just call drawContent and return early
      drawContent()
      return
    }

    val graphicsContext = currentValueOf(LocalGraphicsContext)

    val contentLayer = state.contentLayer
      ?.takeUnless { it.isReleased }
      ?: graphicsContext.createGraphicsLayer().also { state.contentLayer = it }

    // First we draw the composable content into a graphics layer
    contentLayer.record {
      this@draw.drawContent()
    }

    // Now we draw `content` into the window canvas
    drawLayer(contentLayer)

    val tick = Snapshot.withoutReadObservation { state.invalidateTick }
    state.invalidateTick = tick + 1

    log(TAG) { "end draw()" }
  }

  override fun onDetach() {
    super.onDetach()

    state.contentLayer?.let { layer ->
      currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(layer)
    }
    state.contentLayer = null
  }

  private companion object {
    const val TAG = "HazeNode"
  }
}

internal expect val USE_GRAPHICS_LAYERS: Boolean

internal fun HazeTint.boostForFallback(blurRadius: Dp): HazeTint = when (this) {
  is HazeTint.Color -> {
    // For color, we can boost the alpha
    val boosted = color.boostAlphaForBlurRadius(blurRadius.takeOrElse { HazeDefaults.blurRadius })
    copy(color = boosted)
  }
  // For anything else we just use as-is
  else -> this
}

/**
 * In this implementation, the only tool we have is translucency.
 */
private fun Color.boostAlphaForBlurRadius(blurRadius: Dp): Color {
  // We treat a blur radius of 72.dp as near 'opaque', and linearly boost using that
  val factor = 1 + (blurRadius.value / 72)
  return copy(alpha = (alpha * factor).coerceAtMost(1f))
}
