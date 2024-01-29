// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse

@Stable
class HazeState {
  /**
   * The areas which are blurred by any [Modifier.haze] instances which use this state.
   */
  private val _areas = mutableStateListOf<HazeArea>()

  val areas: List<HazeArea> get() = _areas.toList()

  fun registerArea(area: HazeArea) {
    _areas.add(area)
  }

  fun unregisterArea(area: HazeArea) {
    _areas.remove(area)
  }
}

internal fun Path.addOutline(outline: Outline, offset: Offset) = when (outline) {
  is Outline.Rectangle -> addRect(outline.rect.translate(offset))
  is Outline.Rounded -> addRoundRect(outline.roundRect.translate(offset))
  is Outline.Generic -> addPath(outline.path, offset)
}

@Stable
class HazeArea(
  size: Size = Size.Unspecified,
  positionOnScreen: Offset = Offset.Unspecified,
  style: HazeStyle = HazeStyle.Unspecified,
) {
  var size: Size by mutableStateOf(size)
    internal set

  var positionOnScreen: Offset by mutableStateOf(positionOnScreen)
    internal set

  var style: HazeStyle by mutableStateOf(style)
    internal set

  val isValid: Boolean
    get() = size.isSpecified && positionOnScreen.isSpecified && !size.isEmpty()
}

internal fun HazeArea.boundsInLocal(position: Offset): Rect? {
  if (!isValid) return null
  if (position.isUnspecified) return null

  return size.toRect().translate(positionOnScreen - position)
}

@Deprecated(
  "Deprecated. Replaced with new HazeStyle object",
  ReplaceWith("haze(state, backgroundColor, HazeStyle(tint, blurRadius, noiseFactor))"),
)
fun Modifier.haze(
  state: HazeState,
  backgroundColor: Color,
  tint: Color = HazeDefaults.tint(backgroundColor),
  blurRadius: Dp = HazeDefaults.blurRadius,
  noiseFactor: Float = HazeDefaults.noiseFactor,
): Modifier = haze(state, backgroundColor, HazeStyle(tint, blurRadius, noiseFactor))

/**
 * Draw content within the provided [HazeState.areas] blurred in a 'glassmorphism' style.
 *
 * When running on Android 12 devices (and newer), usage of this API renders the corresponding composable
 * into a separate graphics layer. On older Android platforms, a translucent scrim will be drawn
 * instead.
 *
 * @param backgroundColor Background color of the content. Typically you would provide
 * `MaterialTheme.colorScheme.surface` or similar.
 * @param style Default style to use for areas calculated from [hazeChild]s. Can be overridden
 * by each [hazeChild] via its `style` parameter.
 */
fun Modifier.haze(
  state: HazeState,
  backgroundColor: Color,
  style: HazeStyle = HazeDefaults.defaultStyle(backgroundColor),
): Modifier = this then HazeNodeElement(
  state = state,
  backgroundColor = backgroundColor,
  style = style,
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

  /**
   * Default [HazeStyle] for the given background color.
   */
  fun defaultStyle(backgroundColor: Color): HazeStyle = HazeStyle(
    tint = tint(backgroundColor),
    blurRadius = blurRadius,
    noiseFactor = noiseFactor,
  )
}

internal data class HazeNodeElement(
  val state: HazeState,
  val backgroundColor: Color,
  val style: HazeStyle,
) : ModifierNodeElement<HazeNode>() {
  override fun create(): HazeNode = createHazeNode(
    state = state,
    backgroundColor = backgroundColor,
    style = style,
  )

  override fun update(node: HazeNode) {
    node.state = state
    node.backgroundColor = backgroundColor
    node.style = style
    node.onUpdate()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "haze"
    properties["backgroundColor"] = backgroundColor
    properties["style"] = style
  }
}

internal abstract class HazeNode(
  var state: HazeState,
  var backgroundColor: Color,
  var style: HazeStyle,
) : Modifier.Node() {
  open fun onUpdate() {}
}

/**
 * A holder for the style properties used by Haze.
 *
 * Can be set via [Modifier.haze] and [Modifier.hazeChild].
 *
 * @property tint Default color to tint the blurred content. Should be translucent, otherwise you will not see
 * the blurred content.
 * @property blurRadius Radius of the blur.
 * @property noiseFactor Amount of noise applied to the content, in the range `0f` to `1f`.
 */
@Immutable
data class HazeStyle(
  val tint: Color = Color.Unspecified,
  val blurRadius: Dp = Dp.Unspecified,
  val noiseFactor: Float = Float.MIN_VALUE,
  val shape: Shape? = null,
) {
  companion object {
    val Unspecified: HazeStyle = HazeStyle()
  }
}

/**
 * Resolves the style which should be used by renderers. The style returned from here
 * is guaranteed to contains specified values.
 */
internal fun resolveStyle(default: HazeStyle, child: HazeStyle): HazeStyle = HazeStyle(
  tint = child.tint.takeOrElse { default.tint }.takeOrElse { Color.Transparent },
  blurRadius = child.blurRadius.takeOrElse { default.blurRadius }.takeOrElse { 0.dp },
  noiseFactor = child.noiseFactor.takeOrElse { default.noiseFactor }.takeOrElse { 0f },
  shape = child.shape ?: default.shape,
)

private inline fun Float.takeOrElse(block: () -> Float): Float = if (this in 0f..1f) this else block()
