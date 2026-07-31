// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import dev.chrisbanes.haze.blur.HazeBlurDefaults.tint

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
   * Default alpha used for the tint color. Used by the [tint] function.
   */
  public const val tintAlpha: Float = 0.7f

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
    blurEnabled(blurEnabled())
    blurRadius(blurRadius)
    noiseFactor(noiseFactor)
    backgroundColor(Color.Transparent)
    colorEffects(emptyList())
    fallbackColorEffect(HazeColorEffect.Unspecified)
    alpha(1f)
    mask(null)
    progressive(null)
    blurredEdgeTreatment(blurredEdgeTreatment)
  }

  /**
   * Temporary source adapter for the former default Blur Style builder.
   *
   * The canonical [style] is replayed first, then specified legacy arguments are appended as
   * overrides. This source-only shim will be removed before Haze 2.0 stable.
   *
   * @param backgroundColor color drawn behind the blurred content.
   * @param tint color effect applied to the blurred content.
   * @param blurRadius radius of the blur.
   * @param noiseFactor amount of noise applied to the content.
   */
  @Deprecated(
    message =
    "Use HazeBlurDefaults.style.then { ... }. " +
      "This source-only shim will be removed before Haze 2.0 stable.",
    replaceWith = ReplaceWith(
      expression = """HazeBlurDefaults.style.then {
        if (backgroundColor.isSpecified) backgroundColor(backgroundColor)
        if (tint.isSpecified) colorEffects(listOf(tint))
        if (blurRadius.isSpecified) blurRadius(blurRadius)
        if (!(noiseFactor < 0f)) noiseFactor(noiseFactor)
      }""",
      "androidx.compose.ui.graphics.isSpecified",
      "androidx.compose.ui.unit.isSpecified",
    ),
    level = DeprecationLevel.WARNING,
  )
  public fun style(
    backgroundColor: Color,
    tint: HazeColorEffect = tint(backgroundColor),
    blurRadius: Dp = this.blurRadius,
    noiseFactor: Float = this.noiseFactor,
  ): HazeBlurStyle = style.then {
    if (backgroundColor.isSpecified) backgroundColor(backgroundColor)
    if (tint.isSpecified) colorEffects(listOf(tint))
    if (blurRadius.isSpecified) blurRadius(blurRadius)
    if (!(noiseFactor < 0f)) noiseFactor(noiseFactor)
  }

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
   * Default value for Blur enablement. This function only returns `true` on
   * platforms where we know blurring works reliably.
   *
   * This is not the same as everywhere where it technically works. Some platforms may
   * still need extra invalidation workarounds for reliable redraws.
   *
   * The devices excluded by this function may change in the future.
   */
  public fun blurEnabled(): Boolean = isBlurEnabledByDefault()
}

internal expect fun isBlurEnabledByDefault(): Boolean
