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

  /**
   * Logs the lazily produced [message] under [tag] when [enabled]. The [enabled] check is inlined,
   * so a capturing [message] allocates nothing at the call site while logging is disabled.
   */
  @InternalHazeApi
  public inline fun d(tag: String, crossinline message: () -> String) {
    if (enabled) logEnabled(tag, null) { message() }
  }

  /**
   * Logs [throwable] and the lazily produced [message] under [tag] when [enabled]. The [enabled]
   * check is inlined, so a capturing [message] allocates nothing at the call site while logging is
   * disabled.
   */
  @InternalHazeApi
  public inline fun d(tag: String, throwable: Throwable?, crossinline message: () -> String) {
    if (enabled) logEnabled(tag, throwable) { message() }
  }

  @PublishedApi
  internal fun logEnabled(tag: String, throwable: Throwable?, message: () -> String) {
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

internal expect inline fun platformLog(tag: String, message: String)
