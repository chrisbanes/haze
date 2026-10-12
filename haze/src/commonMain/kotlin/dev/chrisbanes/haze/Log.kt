// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.snapshots.Snapshot

/** Debug logging controls for diagnosing Haze rendering behavior, including Backdrop selection. */
public object HazeLogger {
  /**
   * Whether to print debug log statements to the relevant system logger. Do not build release
   * artifacts with this enabled. When enabled, Backdrop selection and source-fallback messages are
   * included. Native backdrop work is marked separately by the `HazeBackdrop.draw` trace section.
   * This is purely for debugging purposes.
   */
  public var enabled: Boolean = false

  /** Logs the lazily produced [message] under [tag] when [enabled]. */
  @InternalHazeApi
  public fun d(tag: String, message: () -> String) {
    d(tag = tag, throwable = null, message = message)
  }

  /** Logs [throwable] and the lazily produced [message] under [tag] when [enabled]. */
  @InternalHazeApi
  public fun d(tag: String, throwable: Throwable?, message: () -> String) {
    if (enabled) {
      Snapshot.withoutReadObservation {
        platformLog(
          tag = tag,
          message = buildString {
            append(message())
            if (throwable != null) {
              append(". Throwable: ")
              append(throwable)
            }
          },
        )
      }
    }
  }
}

/**
 * Logs [message] under [tag] when [HazeLogger.enabled]. Unlike calling [HazeLogger.d] directly,
 * the guard is inlined, so a capturing [message] allocates nothing at the call site while logging
 * is disabled. Use it on per-draw and per-update paths.
 */
internal inline fun logDebug(tag: String, crossinline message: () -> String) {
  if (HazeLogger.enabled) HazeLogger.d(tag) { message() }
}

internal expect inline fun platformLog(tag: String, message: String)
