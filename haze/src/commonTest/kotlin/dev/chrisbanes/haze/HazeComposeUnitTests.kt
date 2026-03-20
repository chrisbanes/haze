// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
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
          Modifier.hazeEffect(selectedHazeState.value),
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

  @Test
  fun test_zeroSize() = runComposeUiTest {
    val hazeState = HazeState()

    setContent {
      Box {
        Spacer(
          Modifier
            .hazeSource(hazeState)
            .size(width = 0.dp, height = 30.dp),
        )

        Spacer(
          Modifier
            .hazeEffect(hazeState)
            .size(width = 30.dp, height = 0.dp),
        )
      }
    }

    waitForIdle()
  }

  @Test
  fun testDefaultPositionStrategyIsAuto() {
    val state = HazeState()
    assertThat(state.positionStrategy).isEqualTo(HazePositionStrategy.Auto)
    assertThat(state.resolvedStrategy).isEqualTo(HazePositionStrategy.Local)
  }

  @Test
  fun testExplicitLocalStrategy() = runComposeUiTest {
    val state = HazeState().apply {
      positionStrategy = HazePositionStrategy.Local
    }
    setContent {
      Box(Modifier.hazeSource(state)) {
        Spacer(Modifier.hazeEffect(state).size(30.dp))
      }
    }
    waitForIdle()
    assertThat(state.resolvedStrategy).isEqualTo(HazePositionStrategy.Local)
  }

  @Test
  fun testExplicitScreenStrategy() = runComposeUiTest {
    val state = HazeState().apply {
      positionStrategy = HazePositionStrategy.Screen
    }
    setContent {
      Box(Modifier.hazeSource(state)) {
        Spacer(Modifier.hazeEffect(state).size(30.dp))
      }
    }
    waitForIdle()
    assertThat(state.resolvedStrategy).isEqualTo(HazePositionStrategy.Screen)
  }

  @Test
  fun testAutoStrategyResolvesToLocalInSameWindow() = runComposeUiTest {
    val state = HazeState()
    setContent {
      Box(Modifier.hazeSource(state)) {
        Spacer(Modifier.hazeEffect(state).size(30.dp))
      }
    }
    waitForIdle()
    // Same window, so Auto should resolve to Local
    assertThat(state.resolvedStrategy).isEqualTo(HazePositionStrategy.Local)
  }

  @Test
  fun testResolvePositionStrategy_autoPromotesToScreenForCrossWindow() {
    val area = HazeArea().apply { windowId = "dialog-window" }

    val resolved = resolvePositionStrategy(
      configured = HazePositionStrategy.Auto,
      areas = listOf(area),
      windowId = "host-window",
    )

    assertThat(resolved).isEqualTo(HazePositionStrategy.Screen)
  }
}
