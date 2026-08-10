// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur.material3

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeBlurStyleScope

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
 */
@Composable
@ReadOnlyComposable
public fun HazeBlurStyle.Companion.Material3(
  containerColor: Color = MaterialTheme.colorScheme.surface,
  block: HazeBlurStyleScope.() -> Unit,
): HazeBlurStyle = HazeBlurStyle {
  backgroundColor(containerColor)
  block()
}
