// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import dev.chrisbanes.haze.Poko

/**
 * Semantic accessibility preferences that change how Glass separates itself from its content.
 *
 * Haze deliberately does not read platform accessibility services from common code. Hosts map
 * their platform settings to this value and provide it to the subtree that renders Glass.
 */
@Immutable
@Poko
public class GlassAccessibilitySettings(
  /**
   * Makes Glass materials more opaque by fully diffusing a stronger blur, so content behind them
   * is less legible. Fallback rendering uses an opaque appearance-aware base instead.
   */
  public val reduceTransparency: Boolean = false,
  /** Enforces minimum tone contrast and edge separation. */
  public val increaseContrast: Boolean = false,
  /** Enforces a visible edge without changing tone contrast. */
  public val showBorders: Boolean = false,
  /** Opaque base for reduced-transparency fallback, or null for white/light and black/dark. */
  public val reducedTransparencyFallbackColor: Color? = null,
) {
  init {
    require(
      reducedTransparencyFallbackColor == null ||
        (reducedTransparencyFallbackColor.isSpecified && reducedTransparencyFallbackColor.alpha == 1f),
    ) {
      "reducedTransparencyFallbackColor must be specified and opaque"
    }
  }

  /** Returns a copy of these settings with the given values replaced. */
  public fun copy(
    reduceTransparency: Boolean = this.reduceTransparency,
    increaseContrast: Boolean = this.increaseContrast,
    showBorders: Boolean = this.showBorders,
    reducedTransparencyFallbackColor: Color? = this.reducedTransparencyFallbackColor,
  ): GlassAccessibilitySettings = GlassAccessibilitySettings(
    reduceTransparency = reduceTransparency,
    increaseContrast = increaseContrast,
    showBorders = showBorders,
    reducedTransparencyFallbackColor = reducedTransparencyFallbackColor,
  )
}

/** Accessibility preferences inherited by Glass nodes in this composition subtree. */
public val LocalGlassAccessibilitySettings: ProvidableCompositionLocal<GlassAccessibilitySettings> =
  compositionLocalOf { GlassAccessibilitySettings() }
