// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.snapshots.Snapshot

object HazeLogger {
  /**
   * Whether to print debug log statements to the relevant system logger. Do not build release
   * artifacts with this enabled. It's purely for debugging purposes.
   */
  var enabled: Boolean = false

  fun d(tag: String, message: () -> String) {
    if (enabled) {
      Snapshot.withoutReadObservation {
        platformLog(tag, message())
      }
    }
  }
}

internal expect inline fun platformLog(tag: String, message: String)
