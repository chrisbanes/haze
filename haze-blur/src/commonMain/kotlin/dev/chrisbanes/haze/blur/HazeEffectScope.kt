// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import dev.chrisbanes.haze.HazeEffectScope
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

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

/**
 * Migration helper overload that accepts a style parameter directly.
 *
 * In v1, you could write:
 * ```kotlin
 * Modifier.hazeEffect(state, style = HazeMaterials.thin())
 * ```
 *
 * In v2, this becomes:
 * ```kotlin
 * Modifier.hazeEffect(state) {
 *   blurEffect {
 *     style = HazeMaterials.thin()
 *   }
 * }
 * ```
 *
 * This overload supports the v1 pattern during migration.
 */
@Deprecated(
  "Style parameter moved to blurEffect {} block. See migration guide.",
  ReplaceWith(
    "Modifier.hazeEffect(state) { blurEffect { this.style = style } }",
    "dev.chrisbanes.haze.blur.blurEffect"
  )
)
public inline fun Modifier.hazeEffect(
  state: HazeState,
  style: HazeBlurStyle,
  crossinline block: HazeEffectScope.() -> Unit = {}
): Modifier = hazeEffect(state) {
  blurEffect {
    this.style = style
  }
  block()
}
