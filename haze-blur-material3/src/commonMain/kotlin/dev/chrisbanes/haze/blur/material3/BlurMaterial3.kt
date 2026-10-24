// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.blur.material3

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeBlurStyleScope
import dev.chrisbanes.haze.blur.valueBackedHazeBlurStyle

/**
 * Creates a Blur Style with a Material 3 surface background.
 *
 * The default [containerColor] comes from the current [MaterialTheme]. The returned Style is
 * reused while that color is unchanged.
 */
@Composable
public fun HazeBlurStyle.Companion.Material3(
  containerColor: Color = MaterialTheme.colorScheme.surface,
): HazeBlurStyle = remember(containerColor) {
  HazeBlurStyle { backgroundColor(containerColor) }
}

/**
 * Creates a Blur Style with a Material 3 surface background and additional Style writes.
 *
 * The default [containerColor] comes from the current [MaterialTheme]. The supplied color is
 * recorded before [block], so the block can override it without changing unrelated inherited
 * Style writes.
 *
 * The returned Style compares by value: two Styles are equal when they have the same
 * [containerColor] and the same [block] instance. It is therefore equal across recompositions only
 * while the caller's [block] is the same instance, which strong skipping provides for a lambda that
 * captures stable values. A lambda that captures unstable values, or is recreated on every
 * composition, yields an unequal Style; `remember` the Style or hoist the lambda in that case.
 */
@Composable
@ReadOnlyComposable
public fun HazeBlurStyle.Companion.Material3(
  containerColor: Color = MaterialTheme.colorScheme.surface,
  block: HazeBlurStyleScope.() -> Unit,
): HazeBlurStyle = valueBackedHazeBlurStyle("Material3", containerColor, block) {
  backgroundColor(containerColor)
  block()
}
