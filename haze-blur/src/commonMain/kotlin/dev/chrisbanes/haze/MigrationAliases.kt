// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import dev.chrisbanes.haze.blur.blurEffect

/**
 * Migration typealias. Use [HazeBlurStyle] from `dev.chrisbanes.haze.blur` package instead.
 */
@Deprecated(
  "Moved to dev.chrisbanes.haze.blur package. Update your imports.",
  ReplaceWith("HazeBlurStyle", "dev.chrisbanes.haze.blur.HazeBlurStyle"),
)
public typealias HazeStyle = HazeBlurStyle

/**
 * Migration typealias. Use [HazeTint] from `dev.chrisbanes.haze.blur` package instead.
 */
@Deprecated(
  "Moved to dev.chrisbanes.haze.blur package. Update your imports.",
  ReplaceWith("HazeTint", "dev.chrisbanes.haze.blur.HazeTint"),
)
public typealias HazeTint = dev.chrisbanes.haze.blur.HazeTint

/**
 * Migration typealias. Use [HazeProgressive] from `dev.chrisbanes.haze.blur` package instead.
 */
@Deprecated(
  "Moved to dev.chrisbanes.haze.blur package. Update your imports.",
  ReplaceWith("HazeProgressive", "dev.chrisbanes.haze.blur.HazeProgressive"),
)
public typealias HazeProgressive = dev.chrisbanes.haze.blur.HazeProgressive

/**
 * Migration helper. Use [LocalHazeBlurStyle] from `dev.chrisbanes.haze.blur` package instead.
 */
@Deprecated(
  "Moved to dev.chrisbanes.haze.blur package. Update your imports.",
  ReplaceWith("LocalHazeBlurStyle", "dev.chrisbanes.haze.blur.LocalHazeBlurStyle"),
)
public val LocalHazeStyle: ProvidableCompositionLocal<HazeBlurStyle>
  get() = LocalHazeBlurStyle

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
    "hazeEffect(state) { blurEffect { style = style } }",
    "dev.chrisbanes.haze.hazeEffect",
    "dev.chrisbanes.haze.blur.blurEffect",
  ),
)
public inline fun Modifier.hazeEffect(
  state: HazeState,
  style: HazeBlurStyle,
  crossinline block: HazeEffectScope.() -> Unit = {},
): Modifier = hazeEffect(state) {
  blurEffect {
    this.style = style
  }
  block()
}
