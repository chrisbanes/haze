// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect

// Duplicated from commonTest because instrumentation tests cannot depend on
// the commonTest source set. Keep in sync with haze/src/commonTest/kotlin/.../RecompositionCounter.kt
@Composable
fun RecompositionCounter(
  counter: MutableIntState,
  content: @Composable () -> Unit,
) {
  SideEffect { counter.intValue++ }
  content()
}
