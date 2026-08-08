// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import kotlin.jvm.JvmInline

/**
 * Rendering-fidelity policies used by built-in Haze effects.
 */
public sealed interface HazePerformanceMode {
  /** Library-defined performance defaults. */
  public companion object {
    /** Points to the library's current default performance policy. */
    public val Default: HazePerformanceMode = Adaptive

    /** Requests the highest rendering fidelity. */
    public val Quality: HazePerformanceMode = Fixed(1f)

    /** Requests balanced rendering fidelity. */
    public val Balanced: HazePerformanceMode = Fixed(0.5f)

    /** Requests the lowest supported rendering fidelity. */
    public val Performance: HazePerformanceMode = Fixed(0f)
  }

  /**
   * Requests the built-in effect's adaptive performance policy.
   */
  public data object Adaptive : HazePerformanceMode

  /**
   * Requests a fixed rendering quality fraction.
   *
   * @param qualityFraction The overall rendering-fidelity fraction, in the range 0 <= x <= 1.
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
