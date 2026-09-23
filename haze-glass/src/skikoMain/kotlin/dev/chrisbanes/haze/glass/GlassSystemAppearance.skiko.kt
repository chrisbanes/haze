// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)
@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze.glass

import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import dev.chrisbanes.haze.HazeEffectLifecycleScope

internal actual fun readPlatformGlassSystemAppearance(scope: HazeEffectLifecycleScope): GlassSystemAppearance =
  when (scope.currentValueOf(LocalSystemTheme)) {
    SystemTheme.Dark -> GlassSystemAppearance.Dark
    SystemTheme.Light, SystemTheme.Unknown -> GlassSystemAppearance.Light
  }
