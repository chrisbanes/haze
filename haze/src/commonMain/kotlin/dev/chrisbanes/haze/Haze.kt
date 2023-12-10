// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Stable
class HazeState {
  /**
   * The areas which are blurred by any [Modifier.haze] instances which use this state.
   */
  private val _areas = mutableStateMapOf<Any, HazeArea>()

  val areas: Set<HazeArea> get() = _areas.values.toSet()

  fun updateAreaPosition(key: Any, positionInRoot: Offset) {
    _areas.getOrPut(key, ::HazeArea).apply {
      this.positionInRoot = positionInRoot
    }
  }

  fun updateAreaSize(key: Any, size: Size) {
    _areas.getOrPut(key, ::HazeArea).apply {
      this.size = size
    }
  }

  fun updateAreaShape(key: Any, shape: Shape) {
    _areas.getOrPut(key, ::HazeArea).apply {
      this.shape = shape
    }
  }

  fun clearArea(key: Any) {
    _areas.remove(key)
  }
}

internal fun HazeState.addAreasToPath(
  path: Path,
  layoutDirection: LayoutDirection,
  density: Density,
) {
  areas.asSequence()
    .filterNot { it.isEmpty }
    .forEach { area ->
      path.addOutline(
        outline = area.shape.createOutline(area.size, layoutDirection, density),
        offset = area.positionInRoot,
      )
    }
}

internal fun Path.addOutline(outline: Outline, offset: Offset) = when (outline) {
  is Outline.Rectangle -> addRect(outline.rect.translate(offset))
  is Outline.Rounded -> addRoundRect(outline.roundRect.translate(offset))
  is Outline.Generic -> addPath(outline.path, offset)
}

@Stable
class HazeArea {
  var size: Size by mutableStateOf(Size.Unspecified)
    internal set

  var positionInRoot: Offset by mutableStateOf(Offset.Unspecified)
    internal set

  var shape: Shape by mutableStateOf(RectangleShape)
    internal set

  val isEmpty: Boolean get() = size.isEmpty()
}

internal fun HazeArea.boundsInLocal(hazePositionInRoot: Offset): Rect? {
  if (size.isUnspecified) return null
  if (hazePositionInRoot.isUnspecified) return null
  if (positionInRoot.isUnspecified) return null

  return size.toRect().translate(positionInRoot - hazePositionInRoot)
}

/**
 * Draw content within the provided [HazeState.areas] blurred in a 'glassmorphism' style.
 *
 * When running on Android 12 devicees (and newer), usage of this API renders the corresponding composable
 * into a separate graphics layer. On older Android platforms, a translucent scrim will be drawn
 * instead.
 *
 * @param backgroundColor Background color of the content. Typically you would provide
 * `MaterialTheme.colorScheme.surface` or similar.
 * @param tint Color to tint the blurred content. Should be translucent, otherwise you will not see
 * the blurred content.
 * @param blurRadius Radius of the blur.
 * @param noiseFactor Amount of noise applied to the content, in the range `0f` to `1f`.
 */
fun Modifier.haze(
  state: HazeState,
  backgroundColor: Color,
  tint: Color = HazeDefaults.tint(backgroundColor),
  blurRadius: Dp = HazeDefaults.blurRadius,
  noiseFactor: Float = HazeDefaults.noiseFactor,
): Modifier = this then HazeNodeElement(
  state = state,
  tint = tint,
  backgroundColor = backgroundColor,
  blurRadius = blurRadius,
  noiseFactor = noiseFactor,
)

/**
 * Default values for the [haze] modifiers.
 */
@Suppress("ktlint:standard:property-naming")
object HazeDefaults {
  /**
   * Default blur radius. Larger values produce a stronger blur effect.
   */
  val blurRadius: Dp = 20.dp

  /**
   * Noise factor.
   */
  const val noiseFactor = 0.15f

  /**
   * Default alpha used for the tint color. Used by the [tint] function.
   */
  val tintAlpha: Float = 0.7f

  /**
   * Default builder for the 'tint' color. Transforms the provided [color].
   */
  fun tint(color: Color): Color = color.copy(alpha = tintAlpha)
}

internal data class HazeNodeElement(
  val state: HazeState,
  val backgroundColor: Color,
  val tint: Color,
  val blurRadius: Dp,
  val noiseFactor: Float,
) : ModifierNodeElement<HazeNode>() {
  override fun create(): HazeNode = createHazeNode(
    state = state,
    backgroundColor = backgroundColor,
    tint = tint,
    blurRadius = blurRadius,
    noiseFactor = noiseFactor,
  )

  override fun update(node: HazeNode) {
    node.state = state
    node.backgroundColor = backgroundColor
    node.tint = tint
    node.blurRadius = blurRadius
    node.noiseFactor = noiseFactor
    node.onUpdate()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "haze"
    properties["backgroundColor"] = backgroundColor
    properties["tint"] = tint
    properties["blurRadius"] = blurRadius
    properties["noiseFactor"] = noiseFactor
  }
}

internal abstract class HazeNode(
  var state: HazeState,
  var backgroundColor: Color,
  var tint: Color,
  var blurRadius: Dp,
  var noiseFactor: Float,
) : Modifier.Node() {
  open fun onUpdate() {}
}
