// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HazeComposeUnitTests : ContextTest() {
  @Test
  fun testChangingHazeState() = runComposeUiTest {
    val hazeState1 = HazeState()
    val hazeState2 = HazeState()

    val selectedHazeState = mutableStateOf(hazeState1)

    setContent {
      Box(Modifier.hazeSource(selectedHazeState.value)) {
        Spacer(
          Modifier.hazeEffect(selectedHazeState.value, HazeDefaults.style(Color.Blue)),
        )
      }
    }

    // Assert that the HazeArea is in hazeState1
    assertThat(hazeState1.areas).hasSize(1)
    assertThat(hazeState2.areas).isEmpty()

    // Update the selected HazeState and wait for idle
    selectedHazeState.value = hazeState2
    waitForIdle()

    // Assert that the HazeArea moved to hazeState2
    assertThat(hazeState1.areas).isEmpty()
    assertThat(hazeState2.areas).hasSize(1)
  }
}
