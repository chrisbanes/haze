// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isFalse
import kotlin.test.Test

class HazeTest {

  @Test
  fun assertNoLogging() {
    var blockCalled = false

    log("Foo") {
      blockCalled = true
      "foo"
    }

    assertThat(blockCalled).isFalse()
  }
}
