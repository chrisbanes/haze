// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import dev.chrisbanes.haze.ExperimentalHazeApi

/**
 * Semantic accessibility preferences that change how Glass separates itself from its content.
 *
 * Haze deliberately does not read platform accessibility services from common code. Hosts map
 * their platform settings to this value and provide it to the subtree that renders Glass.
 */
@ExperimentalHazeApi
@Immutable
public data class GlassAccessibilitySettings(
  val reduceTransparency: Boolean = false,
  val increaseContrast: Boolean = false,
  val showBorders: Boolean = false,
)

/** Accessibility preferences inherited by Glass nodes in this composition subtree. */
@ExperimentalHazeApi
public val LocalGlassAccessibilitySettings: ProvidableCompositionLocal<GlassAccessibilitySettings> =
  compositionLocalOf { GlassAccessibilitySettings() }
