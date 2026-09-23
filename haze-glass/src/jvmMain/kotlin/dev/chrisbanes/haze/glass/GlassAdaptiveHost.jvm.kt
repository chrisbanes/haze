// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.LocalAwtWindow
import dev.chrisbanes.haze.HazeEffectLifecycleScope

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun platformGlassAdaptiveHost(scope: HazeEffectLifecycleScope): Any? = try {
  scope.currentValueOf(LocalAwtWindow)
} catch (_: IllegalStateException) {
  null
} catch (_: ClassCastException) {
  null
}
