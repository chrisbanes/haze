// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect

/**
 * A test-only composable that increments [counter] each time the calling scope recomposes,
 * including recompositions caused by state read inside [content].
 *
 * This is `inline`, so [content] and the counter share the caller's restart scope. Counters
 * placed in the same scope therefore count together; wrap a counter in its own composable
 * when it needs to be isolated. Do not read [counter] during composition, as that would
 * invalidate the scope it is counting.
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
inline fun RecompositionCounter(
  counter: MutableIntState,
  content: @Composable () -> Unit,
) {
  SideEffect { counter.intValue++ }
  content()
}
