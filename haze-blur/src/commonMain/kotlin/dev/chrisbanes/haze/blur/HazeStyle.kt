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
 * @property colorEffects The [HazeTint]s to apply to the blurred content.
 * @property blurRadius Radius of the blur.
 * @property noiseFactor Amount of noise applied to the content, in the range `0f` to `1f`.
 * Anything outside of that range will be clamped.
 * @property fallbackColorEffect The [HazeTint] to use when Haze uses the fallback scrim functionality.
 * The scrim used whenever blurring is disabled, either because the host platform does not
 * support blurring, or it has been manually disabled.
 * When the fallback tint is used, the tints provided in [colorEffects] are ignored.
 */
@Immutable
public data class HazeStyle(
  public val backgroundColor: Color = Color.Unspecified,
  public val colorEffects: List<HazeColorEffect> = emptyList(),
  public val blurRadius: Dp = Dp.Unspecified,
  public val noiseFactor: Float = -1f,
  public val fallbackColorEffect: HazeColorEffect = HazeColorEffect.Unspecified,
) {
  public constructor(
    backgroundColor: Color = Color.Unspecified,
    colorEffect: HazeColorEffect? = null,
    blurRadius: Dp = Dp.Unspecified,
    noiseFactor: Float = -1f,
    fallbackColorEffect: HazeColorEffect = HazeColorEffect.Unspecified,
  ) : this(
    backgroundColor = backgroundColor,
    colorEffects = listOfNotNull(colorEffect),
    blurRadius = blurRadius,
    noiseFactor = noiseFactor,
    fallbackColorEffect = fallbackColorEffect,
  )

  public companion object {
    public val Unspecified: HazeStyle = HazeStyle(colorEffects = emptyList())
  }
}

/**
 * Describes a color effect applied by the haze effect.
 *
 * This is a sealed interface with concrete implementations for color filters and tints.
 * Follows the Compose UI model where ColorFilter is a top-level effect.
 */
@Stable
public sealed interface HazeColorEffect {
  /**
   * The blend mode to use when applying the effect.
   */
  public val blendMode: BlendMode

  /**
   * Whether this effect is specified (not [Unspecified]).
   */
  public val isSpecified: Boolean

  /**
   * A color filter effect.
   */
  @Immutable
  public data class ColorFilter(
    public val colorFilter: androidx.compose.ui.graphics.ColorFilter,
    override val blendMode: BlendMode = DefaultBlendMode,
  ) : HazeColorEffect {
    override val isSpecified: Boolean get() = true
  }

  /**
   * A color-based tint effect.
   */
  @Immutable
  public data class TintColor(
    public val color: Color,
    override val blendMode: BlendMode = DefaultBlendMode,
  ) : HazeColorEffect {
    override val isSpecified: Boolean get() = color.isSpecified
  }

  /**
   * A brush-based tint effect.
   */
  @Immutable
  public data class TintBrush(
    public val brush: Brush,
    override val blendMode: BlendMode = DefaultBlendMode,
  ) : HazeColorEffect {
    override val isSpecified: Boolean = true
  }

  /**
   * An unspecified color effect. When used, no effect will be applied.
   */
  public object Unspecified : HazeColorEffect {
    override val blendMode: BlendMode = BlendMode.SrcOver
    override val isSpecified: Boolean = false
  }

  @Suppress("NOTHING_TO_INLINE")
  public companion object {
    /**
     * Default blend mode for effects.
     */
    public val DefaultBlendMode: BlendMode = BlendMode.SrcOver

    /**
     * Creates a color filter effect.
     */
    public inline fun colorFilter(
      colorFilter: androidx.compose.ui.graphics.ColorFilter,
      blendMode: BlendMode = DefaultBlendMode,
    ): HazeColorEffect = ColorFilter(colorFilter, blendMode)

    /**
     * Creates a color-based tint effect.
     */
    public inline fun tint(
      color: Color,
      blendMode: BlendMode = DefaultBlendMode,
    ): HazeColorEffect = TintColor(color, blendMode)

    /**
     * Creates a brush-based tint effect.
     */
    public inline fun tint(
      brush: Brush,
      blendMode: BlendMode = DefaultBlendMode,
    ): HazeColorEffect = TintBrush(brush, blendMode)
  }
}

// Type alias for backward compatibility
@Deprecated(
  message = "HazeTint has been renamed to HazeColorEffect",
  replaceWith = ReplaceWith(
    expression = "HazeColorEffect",
    imports = ["dev.chrisbanes.haze.blur.HazeColorEffect"],
  ),
)
public typealias HazeTint = HazeColorEffect

/**
 * Creates a color-based tint effect.
 *
 * @param color The color to tint with.
 * @param blendMode The blend mode to use. Defaults to [HazeColorEffect.DefaultBlendMode].
 */
@Suppress("FunctionName")
@Deprecated(
  message = "Use HazeColorEffect.tint(color, blendMode) instead",
  replaceWith = ReplaceWith(
    expression = "HazeColorEffect.tint(color, blendMode)",
    imports = ["dev.chrisbanes.haze.blur.HazeColorEffect"],
  ),
)
public fun HazeTint(
  color: Color,
  blendMode: BlendMode = HazeColorEffect.DefaultBlendMode,
): HazeColorEffect = HazeColorEffect.tint(color, blendMode)

/**
 * Creates a brush-based tint effect.
 *
 * @param brush The brush to tint with.
 * @param blendMode The blend mode to use. Defaults to [HazeColorEffect.DefaultBlendMode].
 */
@Suppress("FunctionName")
@Deprecated(
  message = "Use HazeColorEffect.tint(brush, blendMode) instead",
  replaceWith = ReplaceWith(
    expression = "HazeColorEffect.tint(brush, blendMode)",
    imports = ["dev.chrisbanes.haze.blur.HazeColorEffect"],
  ),
)
public fun HazeTint(
  brush: Brush,
  blendMode: BlendMode = HazeColorEffect.DefaultBlendMode,
): HazeColorEffect = HazeColorEffect.tint(brush, blendMode)

internal inline fun Float.takeOrElse(block: () -> Float): Float =
  if (this in 0f..1f) this else block()
