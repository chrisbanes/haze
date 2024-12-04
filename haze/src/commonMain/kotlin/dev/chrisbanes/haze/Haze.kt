// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
class HazeState {

  var positionOnScreen: Offset by mutableStateOf(Offset.Unspecified)
    internal set

  /**
   * The content [GraphicsLayer]. This is used by [hazeChild] draw nodes when drawing their
   * blurred areas.
   *
   * This is explicitly NOT snapshot or state backed, as doing so would cause draw loops.
   */
  var contentLayer: GraphicsLayer? = null
    internal set

  internal var contentDrawing = false
}

/**
 * Draw background content for [hazeChild] child nodes, which will be drawn with a blur
 * in a 'glassmorphism' style.
 *
 * When running on Android 12 devices (and newer), usage of this API renders the corresponding composable
 * into a separate graphics layer. On older Android platforms, a translucent scrim will be drawn
 * instead.
 */
@Stable
fun Modifier.haze(state: HazeState): Modifier = this then HazeNodeElement(state)

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
  const val tintAlpha: Float = 0.7f

  /**
   * Default builder for the 'tint' color. Transforms the provided [color].
   */
  fun tint(color: Color): HazeTint = when {
    color.isSpecified -> color.copy(alpha = color.alpha * tintAlpha)
    else -> color
  }.let(::HazeTint)

  @Deprecated(
    "Migrate to HazeTint for tint",
    ReplaceWith("HazeStyle(backgroundColor, HazeTint(tint), blurRadius, noiseFactor)"),
  )
  fun style(
    backgroundColor: Color = Color.Unspecified,
    tint: Color,
    blurRadius: Dp = this.blurRadius,
    noiseFactor: Float = this.noiseFactor,
  ): HazeStyle = HazeStyle(backgroundColor, tint(backgroundColor), blurRadius, noiseFactor)

  /**
   * Default [HazeStyle] for usage with [Modifier.haze].
   *
   * @param backgroundColor Color to draw behind the blurred content. Ideally should be opaque
   * so that the original content is not visible behind. Typically this would be
   * `MaterialTheme.colorScheme.surface` or similar.
   * @param tint Default color to tint the blurred content. Should be translucent, otherwise you
   * will not see the blurred content.
   * @param blurRadius Radius of the blur.
   * @param noiseFactor Amount of noise applied to the content, in the range `0f` to `1f`.
   * Anything outside of that range will be clamped.
   */
  fun style(
    backgroundColor: Color,
    tint: HazeTint = tint(backgroundColor),
    blurRadius: Dp = this.blurRadius,
    noiseFactor: Float = this.noiseFactor,
  ): HazeStyle = HazeStyle(backgroundColor, tint, blurRadius, noiseFactor)

  /**
   * Default values for [HazeChildScope.blurEnabled]. This function only returns `true` on
   * platforms where we know blurring works reliably.
   *
   * This is not the same as everywhere where it technically works. The key omission here
   * is Android SDK Level 31, which is known to have some issues with
   * RenderNode invalidation.
   *
   * The devices excluded by this function may change in the future.
   */
  fun blurEnabled(): Boolean = isBlurEnabledByDefault()
}

internal data class HazeNodeElement(
  val state: HazeState,
) : ModifierNodeElement<HazeNode>() {

  override fun create(): HazeNode = HazeNode(state)

  override fun update(node: HazeNode) {
    node.state = state
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "haze"
  }
}
