// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass.material3

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.glass.GlassStyle

/**
 * Returns a Material 3 Glass Style with this Style's writes applied after the theme defaults.
 *
 * The default [containerColor] comes from the current [MaterialTheme]. A null [tint] omits the
 * theme tint write, preserving any inherited tint. This Style can override either value. Call it
 * on [GlassStyle] for the Material 3 values alone. The returned Style is reused while this Style,
 * [containerColor], and [tint] are unchanged, so `remember` or hoist this Style.
 */
@Composable
public fun GlassStyle.material3(
  containerColor: Color = MaterialTheme.colorScheme.surface,
  tint: Color? = null,
): GlassStyle = remember(this, containerColor, tint) {
  GlassStyle {
    backgroundColor(containerColor)
    if (tint != null) tint(tint)
  }.then(this)
}
