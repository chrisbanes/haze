// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@RequiresOptIn(message = "Experimental Haze API", level = RequiresOptIn.Level.WARNING)
public annotation class ExperimentalHazeApi

internal enum class HazeTraversableNodeKeys {
  Effect,
  Source,
}

/**
 * The [Modifier.Node] implementation used by [Modifier.hazeSource].
 *
 * This is public API in order to aid custom extensible modifiers, _but_ we reserve the right
 * to be able to change the API in the future, hence why it is marked as experimental forever.
 */
@ExperimentalHazeApi
public class HazeSourceNode(
  state: HazeState,
  zIndex: Float = 0f,
  key: Any? = null,
) : Modifier.Node(),
  CompositionLocalConsumerModifierNode,
  GlobalPositionAwareModifierNode,
  LayoutAwareModifierNode,
  DrawModifierNode,
  TraversableNode,
  ObserverModifierNode {

  override val traverseKey: Any
    get() = HazeTraversableNodeKeys.Source

  internal val area = HazeArea()

  init {
    area.zIndex = zIndex
  }

  public var zIndex: Float = zIndex
    set(value) {
      field = value
      area.zIndex = value
    }

  public var state: HazeState = state
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

  public var key: Any?
    get() = area.key
    set(value) {
      area.key = value
    }

  init {
    this.key = key
  }

  private var preDrawJob: Job? = null

  /**
   * We manually invalidate when things have changed
   */
  override val shouldAutoInvalidate: Boolean = false

  override fun onAttach() {
    HazeLogger.d(TAG) { "onAttach. Adding HazeArea: $area" }
    state.addArea(area)
    clearHazeAreaLayerOnStop()

    onObservedReadsChanged()
  }

  override fun onObservedReadsChanged() {
    observeReads {
      if (area.preDrawListeners.isEmpty()) {
        disablePreDrawListener()
      } else {
        enablePreDrawListener()
      }
    }
  }

  private fun enablePreDrawListener() {
    if (preDrawJob?.isActive != true) {
      preDrawJob = launchPreDraw()
    }
  }

  private fun launchPreDraw(): Job = coroutineScope.launch {
    withFrameNanos {
      HazeLogger.d(TAG) { "onPreDraw" }
      area.preDrawListeners.forEach(OnPreDrawListener::invoke)
    }
  }

  private fun disablePreDrawListener() {
    preDrawJob?.cancel()
    preDrawJob = null
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
    if (!isAttached) {
      // This shouldn't happen, but it does...
      // https://github.com/chrisbanes/haze/issues/665
      return
    }

    area.positionOnScreen = coordinates.positionForHaze()
    area.size = coordinates.size.toSize()
    area.windowId = getWindowId()

    HazeLogger.d(TAG) {
      "$source: positionOnScreen=${area.positionOnScreen}, " +
        "size=${area.size}, " +
        "positionOnScreens=${area.positionOnScreen}"
    }
  }

  override fun ContentDrawScope.draw() {
    try {
      HazeLogger.d(TAG) { "start draw()" }
      area.contentDrawing = true

      if (!isAttached) {
        // This shouldn't happen, but it does...
        // https://github.com/chrisbanes/haze/issues/665
        return
      }

      if (size.minDimension.roundToInt() >= 1) {
        val graphicsContext = currentValueOf(LocalGraphicsContext)

        val contentLayer = area.contentLayer
          ?.takeUnless { it.isReleased }
          ?: graphicsContext.createGraphicsLayer().also {
            area.contentLayer = it
            HazeLogger.d(TAG) { "Updated contentLayer in HazeArea: $area" }
          }

        // First we draw the composable content into a graphics layer
        contentLayer.record {
          this@draw.drawContentSafely()
          HazeLogger.d(TAG) { "Drawn content into layer: $contentLayer" }
        }

        // Now we draw `content` into the window canvas
        drawLayer(contentLayer)
        HazeLogger.d(TAG) { "Drawn layer to canvas: $contentLayer" }
      } else {
        HazeLogger.d(TAG) { "Not using graphics layer, so drawing content direct to canvas" }
        // If we're not using graphics layers, just call drawContent and return early
        drawContentSafely()
      }
    } finally {
      area.contentDrawing = false
      HazeLogger.d(TAG) { "end draw()" }

      launchPreDraw()
    }
  }

  override fun onDetach() {
    HazeLogger.d(TAG) { "onDetach. Removing HazeArea: $area" }
    area.reset()
    area.releaseLayer()
    state.removeArea(area)
  }

  override fun onReset() {
    HazeLogger.d(TAG) { "onReset. Resetting HazeArea: $area" }
    area.reset()
  }

  internal fun HazeArea.releaseLayer() {
    contentLayer?.let { layer ->
      HazeLogger.d(TAG) { "Releasing content layer: $layer" }
      currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(layer)
    }
    contentLayer = null
  }

  private companion object {
    const val TAG = "HazeSource"
  }
}

internal expect fun HazeSourceNode.clearHazeAreaLayerOnStop()
