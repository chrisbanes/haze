// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

@Composable
public fun rememberHazeState(): HazeState = remember { HazeState() }

@Stable
public class HazeState {
  private val _areas = mutableStateListOf<HazeArea>()
  public val areas: List<HazeArea> get() = _areas

  internal fun addArea(area: HazeArea) {
    _areas += area
  }

  internal fun removeArea(area: HazeArea) {
    _areas -= area
  }

  @Deprecated("Inspect areas instead")
  public var positionOnScreen: Offset
    get() = areas.firstOrNull()?.positionOnScreen ?: Offset.Unspecified
    set(value) {
      areas.firstOrNull()?.apply {
        positionOnScreen = value
      }
    }

  @Deprecated("Inspect areas instead")
  public var contentLayer: GraphicsLayer?
    get() = areas.firstOrNull()?.contentLayer
    set(value) {
      areas.firstOrNull()?.apply {
        contentLayer = value
      }
    }
}

@Stable
public class HazeArea {

  public var positionOnScreen: Offset by mutableStateOf(Offset.Unspecified)
    internal set

  public var size: Size by mutableStateOf(Size.Unspecified)
    internal set

  public var zIndex: Float by mutableFloatStateOf(0f)
    internal set

  public var key: Any? = null
    internal set

  public var windowId: Any? = null
    internal set

  internal val preDrawListeners = mutableStateSetOf<OnPreDrawListener>()

  /**
   * The content [GraphicsLayer].
   */
  public var contentLayer: GraphicsLayer? by mutableStateOf(null)
    internal set

  internal val bounds: Rect?
    get() = when {
      size.isSpecified && positionOnScreen.isSpecified -> Rect(positionOnScreen, size)
      else -> null
    }

  internal var contentDrawing: Boolean = false

  @InternalHazeApi
  public val isContentDrawing: Boolean
    get() = contentDrawing

  public override fun toString(): String = buildString {
    append("HazeArea(")
    append("positionOnScreen=$positionOnScreen, ")
    append("size=$size, ")
    append("zIndex=$zIndex, ")
    append("contentLayer=$contentLayer, ")
    append("isContentDrawing=$isContentDrawing")
    append(")")
  }
}

internal fun HazeArea.reset() {
  positionOnScreen = Offset.Unspecified
  size = Size.Unspecified
  contentDrawing = false
}

internal fun interface OnPreDrawListener {
  operator fun invoke()
}

@Deprecated(
  message = "Renamed to Modifier.hazeSource()",
  replaceWith = ReplaceWith("hazeSource(state)", "dev.chrisbanes.haze.hazeSource"),
)
@Stable
public fun Modifier.haze(state: HazeState): Modifier = hazeSource(state)

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
