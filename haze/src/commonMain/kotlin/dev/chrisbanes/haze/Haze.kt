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
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/** Creates and remembers a [HazeState] for coordinating Haze sources and effects. */
@Composable
public fun rememberHazeState(): HazeState = remember { HazeState() }

/** Shared state that associates [hazeSource] content with source-backed Haze effects. */
@Stable
public class HazeState {
  internal var positionStrategy: HazePositionStrategy by mutableStateOf(HazePositionStrategy.Auto)

  private val _areas = mutableStateListOf<HazeArea>()
  internal val areas: List<HazeArea> get() = _areas

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
): HazePositionStrategy = when (configured) {
  HazePositionStrategy.Auto -> {
    if (areas.any { it.windowId != null && it.windowId != windowId }) {
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
    internal set

  internal var screenPosition: Offset by mutableStateOf(screenPosition)
    internal set

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

  private var transformToRootValues: FloatArray? = null

  internal var coordinateVersion: Int by mutableIntStateOf(0)
    private set

  internal fun updateLayoutCoordinates(coordinates: LayoutCoordinates) {
    layoutCoordinates = coordinates
    val transform = coordinates.transformToRoot()
    if (transformToRootValues?.contentEquals(transform.values) != true) {
      transformToRootValues = transform.values.copyOf()
      coordinateVersion++
    }
  }

  internal fun clearLayoutCoordinates() {
    layoutCoordinates = null
    transformToRootValues = null
  }

  internal var size: Size by mutableStateOf(Size.Unspecified)
    internal set

  internal var zIndex: Float by mutableFloatStateOf(0f)
    internal set

  internal var key: Any? by mutableStateOf(null)
    internal set

  internal var windowId: Any? = null
    internal set

  internal val preDrawListeners = mutableStateSetOf<OnPreDrawListener>()

  /**
   * Internal content [GraphicsLayer] used when capturing source content for effects.
   *
   * This is exposed for effect implementations and is not a stable public contract.
   */
  internal var contentLayer: GraphicsLayer? by mutableStateOf(null)
    internal set

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
  windowId = null
  contentDrawing = false
}

internal class OnPreDrawListener(
  private val effectWindowId: () -> Any?,
  private val onPreDraw: (invalidateInputCapture: Boolean) -> Unit,
) {
  fun needsSnapshotApplyObservation(area: HazeArea): Boolean = area.windowId != effectWindowId()

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

internal data class HazeSourceElement(
  val state: HazeState,
  val zIndex: Float = 0f,
  val key: Any? = null,
) : ModifierNodeElement<HazeSourceNode>() {

  override fun create(): HazeSourceNode = HazeSourceNode(state = state, zIndex = zIndex, key = key)

  override fun update(node: HazeSourceNode) {
    node.state = state
    node.zIndex = zIndex
    node.key = key
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "hazeSource"
    properties["zIndex"] = zIndex
    properties["key"] = key
  }
}
