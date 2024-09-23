// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp

interface HazeChildScope {
  /**
   * The opacity that the overall effect will drawn with, in the range of 0..1.
   */
  var alpha: Float

  /**
   * Optional mask which allows effects, such as fading via a [Brush.verticalGradient] or similar.
   */
  var mask: Brush?

  /**
   * Color to draw behind the blurred content. Ideally should be opaque
   * so that the original content is not visible behind. Typically this would be
   * `MaterialTheme.colorScheme.surface` or similar.
   */
  var backgroundColor: Color

  /**
   * The [HazeTint]s to apply to the blurred content.
   */
  var tints: List<HazeTint>

  /**
   * Radius of the blur.
   */
  var blurRadius: Dp

  /**
   * Amount of noise applied to the content, in the range `0f` to `1f`.
   * Anything outside of that range will be clamped.
   */
  var noiseFactor: Float

  /**
   *  The [HazeTint] to use when Haze uses the fallback scrim functionality.
   */
  var fallbackTint: HazeTint?

  /**
   * Apply the given [HazeStyle] to this block.
   */
  fun applyStyle(style: HazeStyle)
}

/**
 * Mark this composable as being a Haze child composable.
 *
 * This will update the given [HazeState] whenever the layout is placed, enabling any layouts using
 * [Modifier.haze] to blur any content behind the host composable.
 *
 * @param shape The shape of the content. This will affect the the bounds and outline of
 * the content. Please be aware that using non-rectangular shapes has an effect on performance,
 * since we need to use path clipping.
 * @param style The [HazeStyle] to use on this content. Any specified values in the given
 * style will override that value from the default style, provided to [haze].
 */
@Deprecated(
  message = "Shape clipping is no longer necessary with Haze. You can use `Modifier.clip` or similar.",
  replaceWith = ReplaceWith("clip(shape).hazeChild(state) { applyStyle(style) }"),
)
fun Modifier.hazeChild(
  state: HazeState,
  shape: Shape,
  style: HazeStyle,
): Modifier = this.clip(shape).hazeChild(state, style)

/**
 * Mark this composable as being a Haze child composable.
 *
 * This will update the given [HazeState] whenever the layout is placed, enabling any layouts using
 * [Modifier.haze] to blur any content behind the host composable.
 *
 * @param style The [HazeStyle] to use on this content. Any specified values in the given
 * style will override that value from the default style, provided to [haze].
 */
fun Modifier.hazeChild(
  state: HazeState,
  style: HazeStyle,
): Modifier = hazeChild(state) { applyStyle(style) }

/**
 * Mark this composable as being a Haze child composable.
 *
 * This will update the given [HazeState] whenever the layout is placed, enabling any layouts using
 * [Modifier.haze] to blur any content behind the host composable.
 *
 * @param block block on HazeChildScope where you define the styling and visual properties.
 */
fun Modifier.hazeChild(
  state: HazeState,
  block: (HazeChildScope.() -> Unit),
): Modifier = this then HazeChildNodeElement(state, block)

private data class HazeChildNodeElement(
  val state: HazeState,
  val block: HazeChildScope.() -> Unit,
) : ModifierNodeElement<HazeChildNode>() {
  override fun create(): HazeChildNode = HazeChildNode(state, block)

  override fun update(node: HazeChildNode) {
    node.state = state
    node.block = block
    node.update()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "HazeChild"
  }
}
