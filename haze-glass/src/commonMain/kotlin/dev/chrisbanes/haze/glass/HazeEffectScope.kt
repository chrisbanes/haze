// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectScope

/**
 * Configures a [GlassVisualEffect] for this effect scope.
 */
@ExperimentalHazeApi
public inline fun HazeEffectScope.glassEffect(
  block: GlassVisualEffect.() -> Unit = {},
) {
  val effect = visualEffect as? GlassVisualEffect ?: GlassVisualEffect()
  visualEffect = effect
  effect.block()
}
