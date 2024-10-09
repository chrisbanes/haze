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
import androidx.compose.ui.graphics.BlendMode
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

  var positionOnScreen: Offset by mutableStateOf(Offset.Unspecified)
    internal set

  internal var block: HazeChildScope.() -> Unit = {}

  /**
   * The content [GraphicsLayer]. This is used by [hazeChild] draw nodes when drawing their
   * blurred areas.
   *
   * This is explicitly NOT snapshot or state backed, as doing so would cause draw loops.
   */
  var contentLayer: GraphicsLayer? = null
    internal set
}

/**
 * Draw content within the provided [HazeState.areas] blurred in a 'glassmorphism' style.
 *
 * When running on Android 12 devices (and newer), usage of this API renders the corresponding composable
 * into a separate graphics layer. On older Android platforms, a translucent scrim will be drawn
 * instead.
 */
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
  val fallbackTint: HazeTint? = tints.firstOrNull()?.boostForFallback(blurRadius),
) {
  constructor(
    backgroundColor: Color = Color.Unspecified,
    tint: HazeTint? = null,
    blurRadius: Dp = Dp.Unspecified,
    noiseFactor: Float = -1f,
    fallbackTint: HazeTint? = tint?.boostForFallback(blurRadius),
  ) : this(backgroundColor, listOfNotNull(tint), blurRadius, noiseFactor, fallbackTint)

  companion object {
    val Unspecified: HazeStyle = HazeStyle(tints = emptyList())
  }
}

@Stable
data class HazeTint(
  val color: Color,
  val blendMode: BlendMode = BlendMode.SrcOver,
)

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
