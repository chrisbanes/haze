// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
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

@RequiresOptIn(message = "Experimental Haze API", level = RequiresOptIn.Level.WARNING)
annotation class ExperimentalHazeApi

/**
 * The [Modifier.Node] implementation used by [Modifier.haze].
 *
 * This is public API in order to aid custom extensible modifiers, _but_ we reserve the right
 * to be able to change the API in the future, hence why it is marked as experimental forever.
 */
@ExperimentalHazeApi
class HazeNode(
  var state: HazeState,
) : Modifier.Node(),
  CompositionLocalConsumerModifierNode,
  GlobalPositionAwareModifierNode,
  LayoutAwareModifierNode,
  DrawModifierNode {

  override fun onPlaced(coordinates: LayoutCoordinates) {
    // If the positionOnScreen has not been placed yet, we use the value on onPlaced,
    // otherwise we ignore it. This primarily fixes screenshot tests which only run tests
    // up to the first draw. We need onGloballyPositioned which tends to happen after
    // the first pass
    Snapshot.withoutReadObservation {
      if (state.positionOnScreen.isUnspecified) {
        log(TAG) {
          "onPlaced: " +
            "positionInWindow=${coordinates.positionInWindow()}, " +
            "content positionOnScreens=${state.positionOnScreen}"
        }
        onPositioned(coordinates)
      }
    }
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    log(TAG) {
      "onGloballyPositioned: " +
        "positionInWindow=${coordinates.positionInWindow()}, " +
        "content positionOnScreens=${state.positionOnScreen}"
    }
    onPositioned(coordinates)
  }

  private fun onPositioned(coordinates: LayoutCoordinates) {
    state.positionOnScreen = coordinates.positionInWindow() + calculateWindowOffset()
  }

  /**
   * We manually invalidate when things have changed
   */
  override val shouldAutoInvalidate: Boolean = false

  override fun ContentDrawScope.draw() {
    state.contentDrawing = true
    log(TAG) { "start draw()" }

    if (canUseGraphicLayers()) {
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
    } else {
      // If we're not using graphics layers, just call drawContent and return early
      drawContent()
    }

    state.contentDrawing = false
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

internal expect fun isBlurEnabledByDefault(): Boolean

internal expect fun DrawScope.canUseGraphicLayers(): Boolean

internal fun HazeTint.boostForFallback(blurRadius: Dp): HazeTint {
  // For color, we can boost the alpha
  val resolved = blurRadius.takeOrElse { HazeDefaults.blurRadius }
  val boosted = color.boostAlphaForBlurRadius(resolved)
  return copy(color = boosted)
}

/**
 * In this implementation, the only tool we have is translucency.
 */
private fun Color.boostAlphaForBlurRadius(blurRadius: Dp): Color {
  // We treat a blur radius of 72.dp as near 'opaque', and linearly boost using that
  val factor = 1 + (blurRadius.value / 72)
  return copy(alpha = (alpha * factor).coerceAtMost(1f))
}
