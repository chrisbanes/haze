// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

internal enum class HazeBackdropBackendSelection {
  Undecided,
  Native,
  FallbackUnavailable,
  FallbackFailed,
}

internal class HazeBackdropBackendState {
  var selection: HazeBackdropBackendSelection = HazeBackdropBackendSelection.Undecided
    private set

  val usesFallback: Boolean
    get() = selection == HazeBackdropBackendSelection.FallbackUnavailable ||
      selection == HazeBackdropBackendSelection.FallbackFailed

  fun attach(platformBackdropEnabled: Boolean) {
    selection = if (platformBackdropEnabled) {
      HazeBackdropBackendSelection.Undecided
    } else {
      HazeBackdropBackendSelection.FallbackUnavailable
    }
  }

  fun resolve(nativeAvailable: Boolean) {
    if (!usesFallback) {
      selection = if (nativeAvailable) {
        HazeBackdropBackendSelection.Native
      } else {
        HazeBackdropBackendSelection.FallbackUnavailable
      }
    }
  }

  fun fail() {
    if (!usesFallback) {
      selection = HazeBackdropBackendSelection.FallbackFailed
    }
  }

  fun reset() {
    selection = HazeBackdropBackendSelection.Undecided
  }
}
