// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import dev.chrisbanes.haze.HazeEffectScope

/**
 * Configures a [BlurVisualEffect] for this effect scope, allowing direct access to blur-specific
 * properties without casting.
 *
 * This extension function simplifies configuration when you know you're working with a blur effect.
 *
 * @param block Configuration block that receives the [BlurVisualEffect]
 */
public inline fun HazeEffectScope.blurEffect(block: BlurVisualEffect.() -> Unit) {
  val blurEffect = visualEffect as? BlurVisualEffect ?: BlurVisualEffect()
  visualEffect = blurEffect
  blurEffect.block()
}
