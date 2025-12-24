// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp

/**
 * A [ProvidableCompositionLocal] which provides the default [HazeStyle] for all [dev.chrisbanes.haze.hazeEffect]
 * layout nodes placed within this composition local's content.
 *
 * There are precedence rules to how each styling property is applied. The order of precedence
 * for each property are as follows:
 *
 *  - Value set in [dev.chrisbanes.haze.HazeEffectScope], if specified.
 *  - Value set in style provided to [dev.chrisbanes.haze.hazeEffect] (or [dev.chrisbanes.haze.HazeEffectScope.style]), if specified.
 *  - Value set in this composition local.
 */
public val LocalHazeStyle: ProvidableCompositionLocal<HazeStyle> =
  compositionLocalOf { HazeBlurDefaults.style(Color.Unspecified) }

/**
 * A holder for the style properties used by Haze.
 *
 * Can be set via [dev.chrisbanes.haze.hazeSource] and [dev.chrisbanes.haze.hazeEffect].
 *
 * @property backgroundColor Color to draw behind the blurred content. Ideally should be opaque
 * so that the original content is not visible behind. Typically this would be
 * `MaterialTheme.colorScheme.surface` or similar.
 * @property tints The [HazeTint]s to apply to the blurred content.
 * @property blurRadius Radius of the blur.
 * @property noiseFactor Amount of noise applied to the content, in the range `0f` to `1f`.
 * Anything outside of that range will be clamped.
 * @property fallbackTint The [HazeTint] to use when Haze uses the fallback scrim functionality.
 * The scrim used whenever blurring is disabled, either because the host platform does not
 * support blurring, or it has been manually disabled.
 * When the fallback tint is used, the tints provided in [tints] are ignored.
 */
@Immutable
public data class HazeStyle(
  public val backgroundColor: Color = Color.Unspecified,
  public val tints: List<HazeTint> = emptyList(),
  public val blurRadius: Dp = Dp.Unspecified,
  public val noiseFactor: Float = -1f,
  public val fallbackTint: HazeTint = HazeTint.Unspecified,
) {
  public constructor(
    backgroundColor: Color = Color.Unspecified,
    tint: HazeTint? = null,
    blurRadius: Dp = Dp.Unspecified,
    noiseFactor: Float = -1f,
    fallbackTint: HazeTint = HazeTint.Unspecified,
  ) : this(
    backgroundColor = backgroundColor,
    tints = listOfNotNull(tint),
    blurRadius = blurRadius,
    noiseFactor = noiseFactor,
    fallbackTint = fallbackTint,
  )

  public companion object {
    public val Unspecified: HazeStyle = HazeStyle(tints = emptyList())
  }
}

/**
 * Describes a 'tint' drawn by the haze effect.
 *
 * This is a sealed interface with concrete implementations for color-based and brush-based tints.
 */
@Stable
public sealed interface HazeTint {
  /**
   * The blend mode to use when drawing the tint.
   */
  public val blendMode: BlendMode

  /**
   * Optional color filter to apply to the tint.
   */
  public val colorFilter: ColorFilter?

  /**
   * Whether this tint is specified (not [Unspecified]).
   */
  public val isSpecified: Boolean

  /**
   * A color-based tint.
   */
  @Immutable
  public data class Color(
    public val color: androidx.compose.ui.graphics.Color,
    override val blendMode: BlendMode = DefaultBlendMode,
    override val colorFilter: ColorFilter? = null,
  ) : HazeTint {
    override val isSpecified: Boolean get() = color.isSpecified
  }

  /**
   * A brush-based tint.
   */
  @Immutable
  public data class Brush(
    public val brush: androidx.compose.ui.graphics.Brush,
    override val blendMode: BlendMode = DefaultBlendMode,
    override val colorFilter: ColorFilter? = null,
  ) : HazeTint {
    override val isSpecified: Boolean get() = true
  }

  public companion object {
    /**
     * An unspecified tint. When used, no tint will be applied.
     */
    public val Unspecified: HazeTint = object : HazeTint {
      override val blendMode: BlendMode = BlendMode.SrcOver
      override val colorFilter: ColorFilter? = null
      override val isSpecified: Boolean = false
    }

    /**
     * Default blend mode for tints.
     */
    public val DefaultBlendMode: BlendMode = BlendMode.SrcOver
  }
}

/**
 * Creates a color-based [HazeTint].
 *
 * @param color The color to tint with.
 * @param blendMode The blend mode to use. Defaults to [HazeTint.DefaultBlendMode].
 * @param colorFilter Optional color filter to apply.
 */
@Deprecated(
  message = "HazeTint has been renamed to HazeColorEffect. Use HazeColorEffect(color, blendMode, colorFilter) instead.",
  replaceWith = ReplaceWith(
    expression = "HazeColorEffect(color, blendMode, colorFilter)",
    imports = ["dev.chrisbanes.haze.blur.HazeColorEffect"],
  ),
)
public fun HazeTint(
  color: androidx.compose.ui.graphics.Color,
  blendMode: BlendMode = HazeTint.DefaultBlendMode,
  colorFilter: ColorFilter? = null,
): HazeTint = HazeTint.Color(color, blendMode, colorFilter)

/**
 * Creates a brush-based [HazeTint].
 *
 * @param brush The brush to tint with.
 * @param blendMode The blend mode to use. Defaults to [HazeTint.DefaultBlendMode].
 * @param colorFilter Optional color filter to apply.
 */
@Deprecated(
  message = "HazeTint has been renamed to HazeColorEffect. Use HazeColorEffect(brush, blendMode, colorFilter) instead.",
  replaceWith = ReplaceWith(
    expression = "HazeColorEffect(brush, blendMode, colorFilter)",
    imports = ["dev.chrisbanes.haze.blur.HazeColorEffect"],
  ),
)
public fun HazeTint(
  brush: androidx.compose.ui.graphics.Brush,
  blendMode: BlendMode = HazeTint.DefaultBlendMode,
  colorFilter: ColorFilter? = null,
): HazeTint = HazeTint.Brush(brush, blendMode, colorFilter)

internal inline fun Float.takeOrElse(block: () -> Float): Float =
  if (this in 0f..1f) this else block()
