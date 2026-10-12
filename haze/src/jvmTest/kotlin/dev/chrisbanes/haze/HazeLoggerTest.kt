// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class HazeLoggerTest {

  @Test
  fun d_disabledDoesNotInvokeMessage() {
    var calls = 0

    withLogging(enabled = false) {
      HazeLogger.d("Foo") {
        calls++
        error("message built while logging is disabled")
      }
    }

    assertThat(calls).isEqualTo(0)
  }

  @Test
  fun d_withThrowableDisabledDoesNotInvokeMessage() {
    var calls = 0

    withLogging(enabled = false) {
      HazeLogger.d("Foo", RuntimeException("x")) {
        calls++
        error("message built while logging is disabled")
      }
    }

    assertThat(calls).isEqualTo(0)
  }

  @Test
  fun d_enabledInvokesMessageOnce() {
    var calls = 0

    withLogging(enabled = true) {
      HazeLogger.d("Foo") {
        calls++
        "foo"
      }
    }

    assertThat(calls).isEqualTo(1)
  }

  @Test
  fun d_withThrowableEnabledInvokesMessageOnce() {
    var calls = 0

    withLogging(enabled = true) {
      HazeLogger.d("Foo", RuntimeException("x")) {
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
