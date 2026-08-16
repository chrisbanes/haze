// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class, ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
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

/** Marks Haze APIs that are experimental and may change without notice. */
@RequiresOptIn(message = "Experimental Haze API", level = RequiresOptIn.Level.WARNING)
public annotation class ExperimentalHazeApi

internal enum class HazeTraversableNodeKeys {
  Effect,
  Source,
}

internal class HazeSourceNode(
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

  internal var zIndex: Float = zIndex
    set(value) {
      field = value
      area.zIndex = value
    }

  internal var state: HazeState = state
    set(value) {
      if (value === field) return
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

  internal var key: Any?
    get() = area.key
    set(value) {
      area.key = value
    }

  init {
    this.key = key
  }

  private var lastCoordinates: LayoutCoordinates? = null

  private var preDrawJob: Job? = null
  private var snapshotApplyObserver: ObserverHandle? = null

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
      // Observe pre-draw listeners only. Position is now updated directly in onPositioned.
      if (area.preDrawListeners.isEmpty()) {
        disablePreDrawListener()
      } else {
        enablePreDrawListener()
      }
    }
  }

  private fun enablePreDrawListener() {
    if (area.preDrawListeners.any { it.needsSnapshotApplyObservation(area) }) {
      enableSnapshotApplyObserver()
    } else {
      disableSnapshotApplyObserver()
    }
    schedulePreDraw()
  }

  private fun enableSnapshotApplyObserver() {
    if (snapshotApplyObserver != null) return

    // Descendant layer-property changes may not redraw this node, but their snapshot writes
    // still need to refresh effects hosted in another window.
    snapshotApplyObserver = Snapshot.registerApplyObserver { _, _ ->
      coroutineScope.launch { schedulePreDraw() }
    }
  }

  private fun disableSnapshotApplyObserver() {
    snapshotApplyObserver?.dispose()
    snapshotApplyObserver = null
  }

  private fun schedulePreDraw() {
    if (area.preDrawListeners.isEmpty()) return
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
    disableSnapshotApplyObserver()
    preDrawJob?.cancel()
    preDrawJob = null
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    Snapshot.withoutReadObservation {
      // onPlaced is needed before first draw because onGloballyPositioned can arrive
      // after screenshot tests capture the first frame (#433).
      //
      // Lazy-list scroll can update local/root positions every placement while
      // onGloballyPositioned is too sparse for sticky haze headers (#994). Keep
      // screen coordinates guarded/authoritative via onGloballyPositioned, but
      // allow local coordinates to refresh from placement.
      onPositioned(
        coordinates = coordinates,
        source = "onPlaced",
        updateScreenPosition = area.coordinates.screenPosition.isUnspecified,
      )
    }
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    onPositioned(
      coordinates = coordinates,
      source = "onGloballyPositioned",
      updateScreenPosition = true,
    )
  }

  private fun onPositioned(
    coordinates: LayoutCoordinates,
    source: String,
    updateScreenPosition: Boolean,
  ) {
    if (!isAttached) {
      // This shouldn't happen, but it does...
      // https://github.com/chrisbanes/haze/issues/665
      return
    }

    lastCoordinates = coordinates
    // Write both local and screen positions so effects can use either coordinate space
    area.coordinates.localPosition = coordinates.positionInRoot()
    if (updateScreenPosition) {
      area.coordinates.screenPosition = coordinates.safePositionOnScreen()
    }
    area.size = coordinates.size.toSize()
    area.windowId = getWindowId()

    HazeLogger.d(TAG) {
      "$source: localPosition=${area.coordinates.localPosition}, " +
        "screenPosition=${area.coordinates.screenPosition}, size=${area.size}"
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
        area.contentVersion++

        // Now we draw `content` into the window canvas
        drawLayer(contentLayer)
        HazeLogger.d(TAG) { "Drawn layer to canvas: $contentLayer" }
      } else {
        HazeLogger.d(TAG) { "Not using graphics layer, so drawing content direct to canvas" }
        // A previously recorded layer must not remain available to effects after this source
        // becomes too small to capture. Effects may retain their own output separately.
        area.releaseLayer()
        // If we're not using graphics layers, just call drawContent and return early
        drawContentSafely()
      }
    } finally {
      area.contentDrawing = false
      HazeLogger.d(TAG) { "end draw()" }

      Snapshot.withoutReadObservation {
        if (area.preDrawListeners.isNotEmpty()) {
          enablePreDrawListener()
        }
      }
    }
  }

  override fun onDetach() {
    HazeLogger.d(TAG) { "onDetach. Removing HazeArea: $area" }
    disablePreDrawListener()
    area.reset()
    area.releaseLayer()
    state.removeArea(area)
  }

  override fun onReset() {
    HazeLogger.d(TAG) { "onReset. Resetting HazeArea: $area" }
    disablePreDrawListener()
    area.releaseLayer()
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
