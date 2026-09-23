// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.platform.LocalView
import dev.chrisbanes.haze.HazeEffectLifecycleScope

internal actual fun platformGlassAdaptiveHost(scope: HazeEffectLifecycleScope): Any? = try {
  scope.currentValueOf(LocalView).windowToken
} catch (_: IllegalStateException) {
  null
} catch (_: ClassCastException) {
  null
}
