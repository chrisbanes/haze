// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect

/**
 * A test-only composable that increments [counter] on every recomposition.
 *
 * Usage:
 * ```
 * val count = mutableIntStateOf(0)
 * RecompositionCounter(count) {
 *     Spacer(Modifier.hazeEffect(hazeState))
 * }
 * ```
 */
@Composable
fun RecompositionCounter(
  counter: MutableIntState,
  content: @Composable () -> Unit,
) {
  SideEffect { counter.intValue++ }
  content()
}
