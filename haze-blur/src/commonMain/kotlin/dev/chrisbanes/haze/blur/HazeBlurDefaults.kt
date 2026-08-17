// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Default values for [hazeBlur].
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
   * Default edge treatment used by [hazeBlur].
   */
  public val blurredEdgeTreatment: BlurredEdgeTreatment = BlurredEdgeTreatment.Rectangle

  /**
   * Complete default Blur Style.
   *
   * This Style is replayed before composition-local and explicit Styles.
   */
  public val style: HazeBlurStyle = HazeBlurStyle {
    blurEnabled(isBlurEnabledByDefault())
    blurRadius(blurRadius)
    noiseFactor(noiseFactor)
    backgroundColor(Color.Transparent)
    colorEffects(emptyList())
    fallbackColorEffect(null)
    alpha(1f)
    mask(null)
    progressive(null)
    blurredEdgeTreatment(blurredEdgeTreatment)
  }

  /**
   * Default value for Blur enablement. This function only returns `true` on
   * platforms where we know blurring works reliably.
   *
   * This is not the same as everywhere where it technically works. Some platforms may
   * still need extra invalidation workarounds for reliable redraws.
   *
   * The devices excluded by this function may change in the future.
   */
  public fun isBlurEnabledByDefault(): Boolean = platformIsBlurEnabledByDefault()
}

internal expect fun platformIsBlurEnabledByDefault(): Boolean
