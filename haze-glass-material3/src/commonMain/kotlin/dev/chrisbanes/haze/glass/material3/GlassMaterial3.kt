// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass.material3

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassStyleScope

/**
 * Returns a Material 3 Glass Style with this Style's writes applied after the theme defaults.
 *
 * The default [containerColor] comes from the current [MaterialTheme]. A null [tint] omits the
 * theme tint write. This Style can override either value. The returned Style is reused while this
 * Style, [containerColor], and [tint] are unchanged.
 */
@Composable
@ExperimentalHazeApi
public fun GlassStyle.material3(
  containerColor: Color = MaterialTheme.colorScheme.surface,
  tint: Color? = null,
): GlassStyle {
  val material3Style = GlassStyle.Material3(containerColor, tint)
  return remember(this, material3Style) { material3Style.then(this) }
}

/**
 * Creates a Glass Style with a Material 3 surface background and optional tint.
 *
 * The default [containerColor] comes from the current [MaterialTheme]. A null [tint] omits the
 * tint write, preserving any inherited tint. The returned Style is reused while [containerColor]
 * and [tint] are unchanged.
 */
@Composable
@ExperimentalHazeApi
public fun GlassStyle.Companion.Material3(
  containerColor: Color = MaterialTheme.colorScheme.surface,
  tint: Color? = null,
): GlassStyle = remember(containerColor, tint) {
  GlassStyle {
    backgroundColor(containerColor)
    if (tint != null) tint(tint)
  }
}

/**
 * Creates a Glass Style with a Material 3 surface background, optional tint, and additional
 * Style writes.
 *
 * The default [containerColor] comes from the current [MaterialTheme]. A null [tint] omits the
 * tint write, preserving any inherited tint. The supplied values are recorded before [block], so
 * the block can override them without changing unrelated inherited Style writes.
 */
@Composable
@ReadOnlyComposable
@ExperimentalHazeApi
public fun GlassStyle.Companion.Material3(
  containerColor: Color = MaterialTheme.colorScheme.surface,
  tint: Color? = null,
  block: GlassStyleScope.() -> Unit,
): GlassStyle = GlassStyle {
  backgroundColor(containerColor)
  if (tint != null) tint(tint)
  block()
}
