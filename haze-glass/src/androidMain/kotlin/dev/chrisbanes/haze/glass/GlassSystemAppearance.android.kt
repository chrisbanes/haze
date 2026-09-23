// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import dev.chrisbanes.haze.HazeEffectLifecycleScope

internal actual fun readPlatformGlassSystemAppearance(scope: HazeEffectLifecycleScope): GlassSystemAppearance =
  when (scope.currentValueOf(LocalConfiguration).uiMode and Configuration.UI_MODE_NIGHT_MASK) {
    Configuration.UI_MODE_NIGHT_YES -> GlassSystemAppearance.Dark
    else -> GlassSystemAppearance.Light
  }
