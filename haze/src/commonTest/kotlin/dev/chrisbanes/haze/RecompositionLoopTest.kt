// Copyright 2026, Christopher Banes and the Haze project contributors
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
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalTestApi::class)
class RecompositionLoopTest : ContextTest() {

  companion object {
    private const val IDLE_TIMEOUT_MS = 1000L
  }

  @Test
  fun positionStrategyMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    waitForIdle()

    hazeState.positionStrategy = HazePositionStrategy.Screen

    try {
      withTimeout(IDLE_TIMEOUT_MS) {
        waitForIdle()
      }
    } catch (e: TimeoutCancellationException) {
      throw AssertionError(
        "Infinite recomposition loop detected after positionStrategy mutation. " +
          "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
        e,
      )
    }
  }

  @Test
  fun addingSourceNode_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(false)

    setContent {
      if (showSource.value) {
        Box(Modifier.hazeSource(hazeState).size(50.dp))
      }
      Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
    }
    waitForIdle()

    showSource.value = true

    try {
      withTimeout(IDLE_TIMEOUT_MS) {
        waitForIdle()
      }
    } catch (e: TimeoutCancellationException) {
      throw AssertionError(
        "Infinite recomposition loop detected after adding source node. " +
          "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
        e,
      )
    }
  }

  @Test
  fun removingSourceNode_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(true)

    setContent {
      if (showSource.value) {
        Box(Modifier.hazeSource(hazeState).size(50.dp))
      }
      Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
    }
    waitForIdle()

    showSource.value = false

    try {
      withTimeout(IDLE_TIMEOUT_MS) {
        waitForIdle()
      }
    } catch (e: TimeoutCancellationException) {
      throw AssertionError(
        "Infinite recomposition loop detected after removing source node. " +
          "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
        e,
      )
    }
  }

  @Test
  fun blurEffectBlockMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val drawBehind = mutableStateOf(false)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        Spacer(
          Modifier
            .hazeEffect(hazeState) {
              drawContentBehind = drawBehind.value
            }
            .size(100.dp),
        )
      }
    }
    waitForIdle()

    drawBehind.value = true

    try {
      withTimeout(IDLE_TIMEOUT_MS) {
        waitForIdle()
      }
    } catch (e: TimeoutCancellationException) {
      throw AssertionError(
        "Infinite recomposition loop detected after blur effect block mutation. " +
          "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
        e,
      )
    }
  }

  @Test
  fun rapidAlternatingMutations_doNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val flag = mutableStateOf(false)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        Spacer(
          Modifier
            .hazeEffect(hazeState) {
              drawContentBehind = flag.value
            }
            .size(100.dp),
        )
      }
    }
    waitForIdle()

    repeat(5) {
      flag.value = !flag.value
      try {
        withTimeout(IDLE_TIMEOUT_MS) {
          waitForIdle()
        }
      } catch (e: TimeoutCancellationException) {
        throw AssertionError(
          "Infinite recomposition loop detected on alternating mutation #$it. " +
            "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
          e,
        )
      }
    }
  }
}
