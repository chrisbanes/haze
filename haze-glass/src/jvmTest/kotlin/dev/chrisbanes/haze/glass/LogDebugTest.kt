// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazeLogger
import kotlin.test.Test

class LogDebugTest {

  @Test
  fun logDebug_disabledDoesNotInvokeMessage() {
    var calls = 0

    withLogging(enabled = false) {
      logDebug("Foo") {
        calls++
        error("message built while logging is disabled")
      }
    }

    assertThat(calls).isEqualTo(0)
  }

  @Test
  fun logDebug_enabledInvokesMessageOnce() {
    var calls = 0

    withLogging(enabled = true) {
      logDebug("Foo") {
        calls++
        "foo"
      }
    }

    assertThat(calls).isEqualTo(1)
  }

  private inline fun withLogging(enabled: Boolean, block: () -> Unit) {
    val previous = HazeLogger.enabled
    HazeLogger.enabled = enabled
    try {
      block()
    } finally {
      HazeLogger.enabled = previous
    }
  }
}
