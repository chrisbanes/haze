// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import kotlin.jvm.JvmInline

/**
 * Rendering-fidelity policies used by built-in Haze effects.
 *
 * [Default] uses [Balanced]. [Quality] requests full-resolution input, while [Fixed] lets you
 * choose a quality level between the [Performance] and [Quality] profiles.
 */
public sealed interface HazePerformanceMode {
  /** Library-defined performance defaults. */
  public companion object {
    /** Offers a middle ground between visual detail and rendering cost. */
    public val Balanced: HazePerformanceMode = Fixed(0.5f)

    /** Points to the library's current default performance profile. */
    public val Default: HazePerformanceMode = Balanced

    /** Requests the highest rendering fidelity. */
    public val Quality: HazePerformanceMode = Fixed(1f)

    /** Requests the lowest supported rendering fidelity. */
    public val Performance: HazePerformanceMode = Fixed(0f)
  }

  /**
   * Compatibility alias for the fixed [Balanced] profile.
   *
   * This object remains available so compiled callers continue to link. Built-in effects render it
   * the same as [Default].
   */
  @Deprecated(
    message = "Adaptive performance selection is no longer supported. Use HazePerformanceMode.Default.",
    replaceWith = ReplaceWith("HazePerformanceMode.Default"),
  )
  public data object Adaptive : HazePerformanceMode

  /**
   * Requests a quality level that stays fixed instead of adjusting automatically.
   *
   * Lower values prioritise rendering performance; higher values prioritise visual detail.
   * If an effect looks too pixelated, try a higher value and check that scrolling and animations
   * remain smooth on the devices you support. Appearance and cost depend on the effect, surface
   * size, and device.
   *
   * @param qualityFraction The quality level, from `0f` (lowest supported quality) to `1f`
   * (highest supported quality). Must be finite and within this range.
   */
  @JvmInline
  public value class Fixed(public val qualityFraction: Float) : HazePerformanceMode {
    init {
      require(qualityFraction.isFinite() && qualityFraction >= 0f && qualityFraction <= 1f) {
        "qualityFraction needs to be finite and in the range 0 <= x <= 1f"
      }
    }
  }
}

/**
 * Default rendering-fidelity policy for built-in Blur and Glass effects in this subtree.
 *
 * Effects observe changes at their modifier nodes. An explicit performance mode on an effect
 * takes precedence over this value. Without a provider, effects use [HazePerformanceMode.Default].
 */
public val LocalHazePerformanceMode: ProvidableCompositionLocal<HazePerformanceMode> = compositionLocalOf { HazePerformanceMode.Default }
