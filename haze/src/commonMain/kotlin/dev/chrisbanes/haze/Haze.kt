// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse

@Stable
class HazeState {

  val contentArea: HazeArea by lazy { HazeArea() }

  /**
   * The content [GraphicsLayer]. This is used by [hazeChild] draw nodes when drawing their
   * blurred areas.
   *
   * This is explicitly NOT snapshot or state backed, as doing so would cause draw loops.
   */
  var contentLayer: GraphicsLayer? = null
    internal set
}

@Stable
class HazeArea {
  var size: Size by mutableStateOf(Size.Unspecified)
    internal set

  var positionOnScreen: Offset by mutableStateOf(Offset.Unspecified)
    internal set

  var style: () -> HazeStyle = { HazeStyle.Unspecified }
    internal set

  var mask: () -> Brush? = { null }
    internal set

  var alpha: () -> Float = { 1f }
    internal set

  val isValid: Boolean
    get() = size.isSpecified && positionOnScreen.isSpecified && !size.isEmpty()

  internal fun reset() {
    positionOnScreen = Offset.Unspecified
    size = Size.Unspecified
  }
}

/**
 * Draw content within the provided [HazeState.areas] blurred in a 'glassmorphism' style.
 *
 * When running on Android 12 devices (and newer), usage of this API renders the corresponding composable
 * into a separate graphics layer. On older Android platforms, a translucent scrim will be drawn
 * instead.
 *
 * @param style Default style to use for areas calculated from [hazeChild]s. Typically you want to
 * use [HazeDefaults.style] to define the default style. Can be overridden by each [hazeChild] via
 * its `style` parameter.
 */
fun Modifier.haze(
  state: HazeState,
  style: HazeStyle = HazeDefaults.style(),
): Modifier = this then HazeNodeElement(state, style)

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
  fun tint(color: Color): Color = when {
    color.isSpecified -> color.copy(alpha = color.alpha * tintAlpha)
    else -> color
  }

  @Deprecated(
    "Migrate to HazeTint for tint",
    ReplaceWith("HazeStyle(backgroundColor, HazeTint.Color(tint), blurRadius, noiseFactor)"),
  )
  fun style(
    backgroundColor: Color = Color.Unspecified,
    tint: Color,
    blurRadius: Dp = this.blurRadius,
    noiseFactor: Float = this.noiseFactor,
  ): HazeStyle = HazeStyle(backgroundColor, HazeTint.Color(tint), blurRadius, noiseFactor)

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
    backgroundColor: Color = Color.Unspecified,
    tint: HazeTint = HazeTint.Color(tint(backgroundColor)),
    blurRadius: Dp = this.blurRadius,
    noiseFactor: Float = this.noiseFactor,
  ): HazeStyle = HazeStyle(backgroundColor, tint, blurRadius, noiseFactor)
}

internal data class HazeNodeElement(
  val state: HazeState,
  val style: HazeStyle,
) : ModifierNodeElement<HazeNode>() {
  override fun create(): HazeNode {
    return HazeNode(state, style)
  }

  override fun update(node: HazeNode) {
    node.state = state
    node.defaultStyle = style

    node.update()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "haze"
    properties["style"] = style
  }
}

/**
 * A holder for the style properties used by Haze.
 *
 * Can be set via [Modifier.haze] and [Modifier.hazeChild].
 *
 * @property backgroundColor Color to draw behind the blurred content. Ideally should be opaque
 * so that the original content is not visible behind. Typically this would be
 * `MaterialTheme.colorScheme.surface` or similar.
 * @property tints The [HazeTint]s to apply to the blurred content.
 * @property blurRadius Radius of the blur.
 * @property noiseFactor Amount of noise applied to the content, in the range `0f` to `1f`.
 * Anything outside of that range will be clamped.
 * @property fallbackTint The [HazeTint] to use when Haze uses the fallback scrim functionality.
 * In this scenario, the tints provided in [tints] are ignored.
 */
@Immutable
data class HazeStyle(
  val backgroundColor: Color = Color.Unspecified,
  val tints: List<HazeTint> = emptyList(),
  val blurRadius: Dp = Dp.Unspecified,
  val noiseFactor: Float = -1f,
  val fallbackTint: HazeTint? = boostTintForFallback(tints.firstOrNull(), blurRadius),
) {
  constructor(
    backgroundColor: Color = Color.Unspecified,
    tint: HazeTint? = null,
    blurRadius: Dp = Dp.Unspecified,
    noiseFactor: Float = -1f,
    fallbackTint: HazeTint? = boostTintForFallback(tint, blurRadius),
  ) : this(backgroundColor, listOfNotNull(tint), blurRadius, noiseFactor, fallbackTint)

  companion object {
    val Unspecified: HazeStyle = HazeStyle(tints = emptyList())
  }
}

private fun boostTintForFallback(tint: HazeTint?, blurRadius: Dp): HazeTint? = when (tint) {
  is HazeTint.Color -> {
    // For color, we can boost the alpha
    val boosted = tint.color.boostAlphaForBlurRadius(blurRadius.takeOrElse { HazeDefaults.blurRadius })
    tint.copy(color = boosted)
  }
  // For anything else we just use as-is
  else -> tint
}

/**
 * In this implementation, the only tool we have is translucency.
 */
private fun Color.boostAlphaForBlurRadius(blurRadius: Dp): Color {
  // We treat a blur radius of 72.dp as near 'opaque', and linearly boost using that
  val factor = 1 + (blurRadius.value / 72)
  return copy(alpha = (alpha * factor).coerceAtMost(1f))
}

@Stable
interface HazeTint {
  data class Color(
    val color: androidx.compose.ui.graphics.Color,
    val blendMode: BlendMode = BlendMode.SrcOver,
  ) : HazeTint

  data class Brush(
    val brush: androidx.compose.ui.graphics.Brush,
    val blendMode: BlendMode = BlendMode.SrcOver,
  ) : HazeTint
}

/**
 * Resolves the style which should be used by renderers. The style returned from here
 * is guaranteed to contains specified values.
 */
internal fun resolveStyle(
  default: HazeStyle,
  child: HazeStyle,
): HazeStyle = HazeStyle(
  tints = child.tints.takeIf { it.isNotEmpty() } ?: default.tints,
  blurRadius = child.blurRadius.takeOrElse { default.blurRadius }.takeOrElse { 0.dp },
  noiseFactor = child.noiseFactor.takeOrElse { default.noiseFactor }.takeOrElse { 0f },
  backgroundColor = child.backgroundColor
    .takeOrElse { default.backgroundColor }
    .takeOrElse { Color.Unspecified },
  fallbackTint = child.fallbackTint ?: default.fallbackTint,
)

private inline fun Float.takeOrElse(block: () -> Float): Float =
  if (this in 0f..1f) this else block()
