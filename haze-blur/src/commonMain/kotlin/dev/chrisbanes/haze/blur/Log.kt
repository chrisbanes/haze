// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import dev.chrisbanes.haze.HazeLogger

/**
 * Logs [message] under [tag] when [HazeLogger.enabled]. Unlike calling [HazeLogger.d] directly,
 * the guard is inlined, so a capturing [message] allocates nothing at the call site while logging
 * is disabled. Use it on per-draw and per-update paths.
 */
internal inline fun logDebug(tag: String, crossinline message: () -> String) {
  if (HazeLogger.enabled) HazeLogger.d(tag) { message() }
}
