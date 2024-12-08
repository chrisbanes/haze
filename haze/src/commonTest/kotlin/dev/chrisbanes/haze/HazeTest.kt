// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isFalse
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
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

  @Test
  fun testDescendantModifiers() {
    assertFailure {
      runComposeUiTest {
        setContent {
          val hazeState = remember { HazeState() }
          Box(Modifier.haze(hazeState)) {
            Spacer(
              Modifier.hazeChild(hazeState, HazeDefaults.style(Color.Blue)) {
                canDrawArea = { true }
              },
            )
          }
        }
      }
    }
  }
}
