// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.toSize

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
 * @param mask An optional mask which allows effects such as fading via [Brush.verticalGradient] or similar.
 */
@Deprecated(
  message = "Shape clipping is no longer necessary with Haze. You can use `Modifier.clip` or similar.",
  replaceWith = ReplaceWith("clip(shape).hazeChild(state, style, mask)"),
)
fun Modifier.hazeChild(
  state: HazeState,
  shape: Shape = RectangleShape,
  style: HazeStyle = HazeStyle.Unspecified,
  mask: Brush? = null,
): Modifier = clip(shape).hazeChild(state, style, mask)

/**
 * Mark this composable as being a Haze child composable.
 *
 * This will update the given [HazeState] whenever the layout is placed, enabling any layouts using
 * [Modifier.haze] to blur any content behind the host composable.
 *
 * This version of the method is more efficient for providing [HazeStyle]s and masks which change
 * via an animation or similar.
 *
 * @param style The [HazeStyle] to use on this content. Any specified values in the given
 * style will override that value from the default style, provided to [haze].
 * @param mask Block to provide a mask which allows effects such as fading via
 * [Brush.verticalGradient] or similar.
 */
fun Modifier.hazeChild(
  state: HazeState,
  style: HazeStyle = HazeStyle.Unspecified,
  mask: Brush? = null,
): Modifier = hazeChild(state = state, style = { style }, mask = { mask })

/**
 * Mark this composable as being a Haze child composable.
 *
 * This will update the given [HazeState] whenever the layout is placed, enabling any layouts using
 * [Modifier.haze] to blur any content behind the host composable.
 *
 * @param style The [HazeStyle] to use on this content. Any specified values in the given
 * style will override that value from the default style, provided to [haze].
 * @param mask An optional mask which allows effects such as fading via [Brush.verticalGradient] or similar.
 */
fun Modifier.hazeChild(
  state: HazeState,
  mask: () -> Brush? = { null },
  style: () -> HazeStyle,
): Modifier = this then HazeChildNodeElement(
  state = state,
  style = style,
  mask = mask,
)

private data class HazeChildNodeElement(
  val state: HazeState,
  val style: () -> HazeStyle,
  val mask: () -> Brush?,
) : ModifierNodeElement<HazeChildNode>() {
  override fun create(): HazeChildNode = HazeChildNode(state, style, mask)

  override fun update(node: HazeChildNode) {
    node.state = state
    node.style = style
    node.mask = mask
    node.update()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "HazeChild"
    properties["style"] = style
    properties["mask"] = mask
  }
}

private class HazeChildNode(
  override var state: HazeState,
  var style: () -> HazeStyle,
  var mask: () -> Brush?,
) : HazeEffectNode() {

  private val area: HazeArea by lazy {
    HazeArea().apply {
      this.style = style
      this.mask = mask
    }
  }

  override fun update() {
    area.style = style
    area.mask = mask

    super.update()
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    super.onPlaced(coordinates)

    // After we've been placed, update the state with our new bounds (in 'screen' coordinates)
    area.positionOnScreen = positionOnScreen
    area.size = coordinates.size.toSize()
  }

  override fun onReset() {
    area.reset()
  }

  override fun ContentDrawScope.draw() {
    log(TAG) { "-> HazeChild. start draw()" }

    if (effects.isEmpty()) {
      // If we don't have any effects, just call drawContent and return early
      drawContent()
      log(TAG) { "-> HazeChild. end draw()" }
      return
    }

    // First we need to make sure that the effects are updated (if necessary)
    for (effect in effects) {
      effect.onPreDraw(drawContext.density)
    }

    if (USE_GRAPHICS_LAYERS) {
      val contentLayer = state.contentLayer
      if (contentLayer != null) {
        drawEffectsWithGraphicsLayer(contentLayer)
      }
    } else {
      drawEffectsWithScrim()
    }

    // Finally we draw the content
    drawContent()

    log(TAG) { "-> HazeChild. end draw()" }
  }

  override fun calculateHazeAreas(): Sequence<HazeArea> = sequenceOf(area)

  private companion object {
    const val TAG = "HazeChild"
  }
}
