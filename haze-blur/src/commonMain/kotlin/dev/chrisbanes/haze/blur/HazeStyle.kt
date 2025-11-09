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
import dev.chrisbanes.haze.InternalHazeApi

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
 * Ideally this class would be a sealed class, but unfortunately that would require breaking the
 * API so we need to use this merged class for v1.x.
 */
@ExposedCopyVisibility
@Stable
public data class HazeTint internal constructor(
  public val color: Color,
  public val blendMode: BlendMode,
  public val brush: Brush?,
) {
  public constructor(color: Color, blendMode: BlendMode = DefaultBlendMode) : this(color = color, brush = null, blendMode = blendMode)

  public constructor(brush: Brush, blendMode: BlendMode = DefaultBlendMode) : this(color = Color.Unspecified, brush = brush, blendMode = blendMode)

  public companion object {
    public val Unspecified: HazeTint = HazeTint(Color.Unspecified, BlendMode.SrcOver, null)

    public val DefaultBlendMode: BlendMode = BlendMode.SrcOver
  }

  public val isSpecified: Boolean get() = color.isSpecified || brush != null
}

internal inline fun Float.takeOrElse(block: () -> Float): Float =
  if (this in 0f..1f) this else block()
