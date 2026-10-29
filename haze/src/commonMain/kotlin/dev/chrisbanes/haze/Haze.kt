// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class, ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp

/** Creates and remembers a [HazeState] for coordinating Haze sources and effects. */
@Composable
public fun rememberHazeState(): HazeState = remember { HazeState() }

/** Shared state that associates [hazeSource] content with source-backed Haze effects. */
@Stable
public class HazeState {
  // Only tests set this, before the state is used, so it is not snapshot state and effects do not observe it.
  internal var positionStrategy: HazePositionStrategy = HazePositionStrategy.Auto

  private val _areas = mutableStateListOf<HazeArea>()
  internal val areas: List<HazeArea> get() = _areas

  private val sourceDemand = mutableStateSetOf<Any>()

  internal val hasSourceDemand: Boolean
    get() = sourceDemand.isNotEmpty()

  internal fun addSourceDemand(key: Any) {
    sourceDemand += key
  }

  internal fun removeSourceDemand(key: Any) {
    sourceDemand -= key
  }

  internal fun addArea(area: HazeArea) {
    _areas += area
  }

  internal fun removeArea(area: HazeArea) {
    _areas -= area
  }
}

internal fun resolvePositionStrategy(
  configured: HazePositionStrategy,
  areas: List<HazeArea>,
  windowId: Any?,
  hasDifferentRoot: Boolean = false,
): HazePositionStrategy = when (configured) {
  HazePositionStrategy.Auto -> {
    if (hasDifferentRoot || areas.any { it.windowId != null && it.windowId != windowId }) {
      HazePositionStrategy.Screen
    } else {
      HazePositionStrategy.Local
    }
  }
  else -> configured
}

@Stable
internal class HazeCoordinates(
  localPosition: Offset = Offset.Unspecified,
  screenPosition: Offset = Offset.Unspecified,
) {
  internal var localPosition: Offset by mutableStateOf(localPosition)
  internal var screenPosition: Offset by mutableStateOf(screenPosition)

  internal fun positionFor(strategy: HazePositionStrategy): Offset = when (strategy) {
    HazePositionStrategy.Local,
    HazePositionStrategy.Auto,
    -> localPosition
    HazePositionStrategy.Screen -> screenPosition
  }

  internal fun boundsFor(strategy: HazePositionStrategy, size: Size): Rect? {
    val position = positionFor(strategy)
    return if (position.isSpecified && size.isSpecified) Rect(position, size) else null
  }
}

/**
 * Returns `true` if either [HazeCoordinates.localPosition] or [HazeCoordinates.screenPosition]
 * is [Offset.Unspecified].
 */
internal val HazeCoordinates.isUnspecified: Boolean
  get() = localPosition.isUnspecified || screenPosition.isUnspecified

@Stable
internal class HazeArea {

  internal val coordinates: HazeCoordinates = HazeCoordinates()

  internal var layoutCoordinates: LayoutCoordinates? = null
    private set

  internal var rootCoordinates: LayoutCoordinates? = null
    private set

  private var coordinateTransformValues: FloatArray? = null
  private val coordinateTransformScratch = Matrix()

  internal var coordinateVersion: Int by mutableIntStateOf(0)
    private set

  internal fun updateLayoutCoordinates(coordinates: LayoutCoordinates) {
    layoutCoordinates = coordinates
    val root = coordinates.findRootCoordinates()
    coordinates.transformToScreenOrRoot(coordinateTransformScratch)
    val transform = coordinateTransformScratch.values
    if (rootCoordinates !== root || coordinateTransformValues?.contentEquals(transform) != true) {
      rootCoordinates = root
      coordinateTransformValues = transform.copyOf()
      coordinateVersion++
    }
  }

  internal fun clearLayoutCoordinates() {
    layoutCoordinates = null
    rootCoordinates = null
    coordinateTransformValues = null
  }

  internal var size: Size by mutableStateOf(Size.Unspecified)
  private var zIndexState by mutableFloatStateOf(0f)
  private var keyState by mutableStateOf<Any?>(null)

  /** Counts [key] and [zIndex] changes, which can change how effects select and order this area. */
  internal var selectionVersion: Int by mutableIntStateOf(0)
    private set

  internal var zIndex: Float
    get() = zIndexState
    set(value) {
      if (value != zIndexState) {
        zIndexState = value
        selectionVersion++
      }
    }

  internal var key: Any?
    get() = keyState
    set(value) {
      if (value != keyState) {
        keyState = value
        selectionVersion++
      }
    }

  internal var windowId: Any? = null

  internal val preDrawListeners = mutableStateSetOf<OnPreDrawListener>()

  private val captureConsumers = mutableStateSetOf<Any>()

  internal val hasCaptureDemand: Boolean
    get() = captureConsumers.isNotEmpty()

  internal val captureConsumerCount: Int
    get() = captureConsumers.size

  internal fun addCaptureConsumer(consumer: Any) {
    captureConsumers += consumer
  }

  internal fun removeCaptureConsumer(consumer: Any) {
    captureConsumers -= consumer
  }

  /**
   * Internal content [GraphicsLayer] used when capturing source content for effects.
   *
   * This is exposed for effect implementations and is not a stable public contract.
   */
  internal var contentLayer: GraphicsLayer? by mutableStateOf(null)
    internal set

  /**
   * The bounds of [contentLayer] that effects sample, in this area's local pixels, while input
   * sharing expands them with outsets. Null means the layout bounds, `(0, 0)` to [size].
   */
  internal var inputBounds: Rect? by mutableStateOf(null)
    internal set

  /** Releases [contentLayer] through [graphicsContext], if there is one, and clears it. */
  internal fun releaseContentLayer(graphicsContext: GraphicsContext) {
    contentLayer?.let { layer ->
      HazeLogger.d("HazeArea") { "Releasing content layer: $layer" }
      graphicsContext.releaseGraphicsLayer(layer)
    }
    contentLayer = null
    inputBounds = null
  }

  /**
   * [contentLayer] when it can be drawn: not released and with a non-empty size.
   *
   * Also reads [inputBounds]: Skia applies a layer's outsets when a consumer records it, so every
   * consumer that reads the layer must redraw when they change.
   */
  internal val drawableContentLayer: GraphicsLayer?
    get() {
      inputBounds
      return contentLayer?.takeUnless { it.isReleased || it.size.width <= 0 || it.size.height <= 0 }
    }

  internal var contentDrawing: Boolean = false

  /**
   * Monotonically increasing version of successfully recorded content.
   *
   * This intentionally survives [reset] so an area reused by a modifier node cannot report a
   * stale version as newly recorded content.
   */
  internal var contentVersion: Long = 0

  internal val isContentDrawing: Boolean
    get() = contentDrawing

  override fun toString(): String = buildString {
    append("HazeArea(")
    append("localPosition=${coordinates.localPosition}, ")
    append("screenPosition=${coordinates.screenPosition}, ")
    append("size=$size, ")
    append("zIndex=$zIndex")
    append(")")
  }
}

internal fun HazeArea.reset() {
  clearLayoutCoordinates()
  coordinates.localPosition = Offset.Unspecified
  coordinates.screenPosition = Offset.Unspecified
  size = Size.Unspecified
  inputBounds = null
  windowId = null
  contentDrawing = false
}

internal class OnPreDrawListener(
  private val effectWindowId: () -> Any?,
  private val onPreDraw: (invalidateInputCapture: Boolean) -> Unit,
) {
  fun needsSnapshotApplyObservation(area: HazeArea): Boolean =
    area.windowId != effectWindowId() || observesSameWindowSourceSnapshots()

  operator fun invoke(invalidateInputCapture: Boolean) = onPreDraw(invalidateInputCapture)
}

internal fun HazeArea.notifyPreDrawListeners(
  regularPreDraw: Boolean,
  snapshotApplied: Boolean,
) {
  preDrawListeners.forEach { listener ->
    val invalidateInputCapture =
      snapshotApplied && listener.needsSnapshotApplyObservation(this)
    if (regularPreDraw || invalidateInputCapture) {
      listener(invalidateInputCapture)
    }
  }
}

/**
 * Captures background content for [hazeEffect] child nodes, which will be drawn with a blur
 * in a 'glassmorphism' style.
 *
 * When running on Android 12 devices (and newer), usage of this API renders the corresponding composable
 * into a separate graphics layer. On older Android platforms, a translucent scrim will be drawn
 * instead.
 */
@Stable
public fun Modifier.hazeSource(
  state: HazeState,
  zIndex: Float = 0f,
  key: Any? = null,
): Modifier = this then HazeSourceElement(state, zIndex, key)

/**
 * Captures background content for [hazeEffect] child nodes, like the [hazeSource] overload without
 * [inputSharing], which uses [HazeInputSharing.Never].
 *
 * While sharing is active, this source draws its content, both on screen and as effect input,
 * clipped to its layout bounds expanded by [inputSharingOutsets]. Anything drawn beyond that is not
 * shown. The outsets do not change this source's layout size or position, and they have no effect
 * while sharing is inactive, when the source draws as it would without sharing.
 *
 * @param inputSharing Whether this source renders once into an offscreen layer that it draws on
 * screen and that effects sampling it share. Only Android reuses a render. See [HazeInputSharing].
 * @param inputSharingOutsets How far beyond each edge of its layout bounds this source keeps drawing
 * while sharing is active. Each edge must be finite and non-negative. The edges are absolute and do
 * not flip in a right-to-left layout, so `LayerOutsets(right = ...)` expands the right edge
 * whatever the layout direction. Pass values adjusted for the layout direction.
 */
@ExperimentalHazeApi
@Stable
public fun Modifier.hazeSource(
  state: HazeState,
  zIndex: Float = 0f,
  key: Any? = null,
  inputSharing: HazeInputSharing,
  inputSharingOutsets: LayerOutsets = LayerOutsets.Zero,
): Modifier {
  with(inputSharingOutsets) {
    require(left.isValidOutset() && top.isValidOutset() && right.isValidOutset() && bottom.isValidOutset()) {
      "inputSharingOutsets edges must be finite and non-negative: $inputSharingOutsets"
    }
  }
  return this then HazeSourceElement(state, zIndex, key, inputSharing, inputSharingOutsets)
}

private fun Dp.isValidOutset(): Boolean = value.isFinite() && value >= 0f

@OptIn(ExperimentalHazeApi::class)
internal data class HazeSourceElement(
  val state: HazeState,
  val zIndex: Float = 0f,
  val key: Any? = null,
  val inputSharing: HazeInputSharing = HazeInputSharing.Never,
  val inputSharingOutsets: LayerOutsets = LayerOutsets.Zero,
) : ModifierNodeElement<HazeSourceNode>() {

  override fun create(): HazeSourceNode = HazeSourceNode(
    state = state,
    zIndex = zIndex,
    key = key,
    inputSharing = inputSharing,
    inputSharingOutsets = inputSharingOutsets,
  )

  override fun update(node: HazeSourceNode) {
    node.state = state
    node.zIndex = zIndex
    node.key = key
    node.inputSharing = inputSharing
    node.inputSharingOutsets = inputSharingOutsets
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "hazeSource"
    properties["zIndex"] = zIndex
    properties["key"] = key
    properties["inputSharing"] = inputSharing
    properties["inputSharingOutsets"] = inputSharingOutsets
  }
}
