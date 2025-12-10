// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import dev.chrisbanes.haze.HazeEffectScope

/**
 * Configures a [LiquidGlassVisualEffect] for this effect scope.
 */
public inline fun HazeEffectScope.liquidGlassEffect(
  block: LiquidGlassVisualEffect.() -> Unit,
) {
  val effect = visualEffect as? LiquidGlassVisualEffect ?: LiquidGlassVisualEffect()
  visualEffect = effect
  effect.block()
}
