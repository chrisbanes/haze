// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.uikit.LocalUIView
import dev.chrisbanes.haze.HazeEffectLifecycleScope

internal actual fun platformGlassAdaptiveHost(scope: HazeEffectLifecycleScope): Any? = try {
  scope.currentValueOf(LocalUIView)
} catch (_: IllegalStateException) {
  null
} catch (_: ClassCastException) {
  null
}
