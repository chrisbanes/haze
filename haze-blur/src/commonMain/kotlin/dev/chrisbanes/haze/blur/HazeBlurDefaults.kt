// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeBlurDefaults.tint

/**
 * Default values for the [BlurVisualEffect].
 */
@Suppress("ktlint:standard:property-naming")
public object HazeBlurDefaults {
  /**
   * Default blur radius. Larger values produce a stronger blur effect.
   */
  public val blurRadius: Dp = 20.dp

  /**
   * Noise factor.
   */
  public const val noiseFactor: Float = 0.15f

  /**
   * Default alpha used for the tint color. Used by the [tint] function.
   */
  public const val tintAlpha: Float = 0.7f

  /**
   * Default value for [BlurVisualEffect.blurredEdgeTreatment]
   */
  public val blurredEdgeTreatment: BlurredEdgeTreatment = BlurredEdgeTreatment.Rectangle

  /**
   * Default builder for the 'tint' color. Transforms the provided [color].
   */
  public fun tint(color: Color): HazeColorEffect = HazeColorEffect.tint(
    color = when {
      color.isSpecified -> color.copy(alpha = color.alpha * tintAlpha)
      else -> color
    },
  )

  /**
   * Default [dev.chrisbanes.haze.blur.HazeBlurStyle] for usage with [BlurVisualEffect].
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
  public fun style(
    backgroundColor: Color,
    tint: HazeColorEffect = tint(backgroundColor),
    blurRadius: Dp = this.blurRadius,
    noiseFactor: Float = this.noiseFactor,
  ): HazeBlurStyle = HazeBlurStyle(backgroundColor, tint, blurRadius, noiseFactor)

  /**
   * Default values for [BlurVisualEffect.blurEnabled]. This function only returns `true` on
   * platforms where we know blurring works reliably.
   *
   * This is not the same as everywhere where it technically works. The key omission here
   * is Android SDK Level 31, which is known to have some issues with
   * RenderNode invalidation.
   *
   * The devices excluded by this function may change in the future.
   */
  public fun blurEnabled(): Boolean = isBlurEnabledByDefault()
}

internal expect fun isBlurEnabledByDefault(): Boolean
