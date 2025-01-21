// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.modifier.modifierLocalMapOf
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.toSize

@RequiresOptIn(message = "Experimental Haze API", level = RequiresOptIn.Level.WARNING)
annotation class ExperimentalHazeApi

/**
 * The [Modifier.Node] implementation used by [Modifier.hazeSource].
 *
 * This is public API in order to aid custom extensible modifiers, _but_ we reserve the right
 * to be able to change the API in the future, hence why it is marked as experimental forever.
 */
@ExperimentalHazeApi
class HazeSourceNode(
  state: HazeState,
  zIndex: Float = 0f,
  key: Any? = null,
) : Modifier.Node(),
  CompositionLocalConsumerModifierNode,
  GlobalPositionAwareModifierNode,
  LayoutAwareModifierNode,
  DrawModifierNode,
  ObserverModifierNode,
  ModifierLocalModifierNode {

  override val providedValues = modifierLocalMapOf(ModifierLocalCurrentHazeZIndex to zIndex)

  private val area = HazeArea()

  var zIndex: Float by mutableStateOf(zIndex)

  var state: HazeState = state
    set(value) {
      val attachedToState = area in field.areas
      if (attachedToState) {
        // Detach ourselves from the old HazeState
        field.removeArea(area)
      }
      field = value
      if (attachedToState) {
        // Finally re-attach ourselves to the new state
        value.addArea(area)
      }
    }

  var key: Any?
    get() = area.key
    set(value) {
      area.key = value
    }

  init {
    this.key = key
  }

  /**
   * We manually invalidate when things have changed
   */
  override val shouldAutoInvalidate: Boolean = false

  override fun onAttach() {
    log(TAG) { "onAttach. Adding HazeArea: $area" }
    state.addArea(area)
    onObservedReadsChanged()
  }

  override fun onObservedReadsChanged() {
    observeReads {
      updateCompoundZIndex()
    }
  }

  private fun updateCompoundZIndex() {
    val upstream = ModifierLocalCurrentHazeZIndex.current
    // We increment the compound zIndex at each layer by at least 1
    val compound = (upstream ?: 0f) + zIndex

    log(TAG) {
      "updateCompoundZIndex(). Upstream=$upstream, zIndex=$zIndex. Resulting compound=$compound"
    }

    provide(ModifierLocalCurrentHazeZIndex, compound)
    area.zIndex = compound
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    // If the positionOnScreen has not been placed yet, we use the value on onPlaced,
    // otherwise we ignore it. This primarily fixes screenshot tests which only run tests
    // up to the first draw. We need onGloballyPositioned which tends to happen after
    // the first pass
    Snapshot.withoutReadObservation {
      if (area.positionOnScreen.isUnspecified) {
        onPositioned(coordinates, "onPlaced")
      }
    }
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    onPositioned(coordinates, "onGloballyPositioned")
  }

  private fun onPositioned(coordinates: LayoutCoordinates, source: String) {
    area.positionOnScreen = coordinates.positionOnScreenCatching()
    area.size = coordinates.size.toSize()

    log(TAG) {
      "$source: positionOnScreen=${area.positionOnScreen}, " +
        "size=${area.size}, " +
        "positionOnScreens=${area.positionOnScreen}"
    }
  }

  override fun ContentDrawScope.draw() {
    log(TAG) { "start draw()" }

    area.contentDrawing = true

    if (canUseGraphicLayers()) {
      val graphicsContext = currentValueOf(LocalGraphicsContext)

      val contentLayer = area.contentLayer
        ?.takeUnless { it.isReleased }
        ?: graphicsContext.createGraphicsLayer().also { area.contentLayer = it }

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

    area.contentDrawing = false

    log(TAG) { "end draw()" }
  }

  override fun onDetach() {
    log(TAG) { "onDetach. Removing HazeArea: $area" }
    area.reset()
    state.removeArea(area)
  }

  override fun onReset() {
    log(TAG) { "onReset. Resetting HazeArea: $area" }
    area.reset()
  }

  private fun HazeArea.reset() {
    positionOnScreen = Offset.Unspecified
    size = Size.Unspecified
    contentDrawing = false
    contentLayer?.let { currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(it) }
    contentLayer = null
  }

  private companion object {
    const val TAG = "HazeSource"
  }
}

internal expect fun isBlurEnabledByDefault(): Boolean

internal expect fun DrawScope.canUseGraphicLayers(): Boolean

internal fun HazeTint.boostForFallback(blurRadius: Dp): HazeTint {
  if (brush != null) {
    // We can't boost brush tints
    return this
  }

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
