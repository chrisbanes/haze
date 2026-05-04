// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecompositionLoopInstrumentationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  companion object {
    private const val IDLE_TIMEOUT_MS = 1000L
  }

  private fun assertIdleWithinTimeout(description: String) {
    val latch = CountDownLatch(1)
    val error = AtomicReference<Throwable?>(null)

    val thread = Thread {
      try {
        composeTestRule.waitForIdle()
      } catch (t: Throwable) {
        error.set(t)
      } finally {
        latch.countDown()
      }
    }
    thread.start()

    if (!latch.await(IDLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
      thread.interrupt()
      throw AssertionError(
        "Infinite recomposition loop detected $description. " +
          "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
      )
    }

    error.get()?.let { throw it }
  }

  @Test
  fun positionStrategyMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()

    composeTestRule.setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    composeTestRule.waitForIdle()

    hazeState.positionStrategy = HazePositionStrategy.Screen
    assertIdleWithinTimeout("after positionStrategy mutation")
  }

  @Test
  fun addingSourceNode_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val showSource = mutableStateOf(false)

    composeTestRule.setContent {
      if (showSource.value) {
        Box(Modifier.hazeSource(hazeState).size(50.dp))
      }
      Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
    }
    composeTestRule.waitForIdle()

    showSource.value = true
    assertIdleWithinTimeout("after adding source node")
  }

  @Test
  fun removingSourceNode_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val showSource = mutableStateOf(true)

    composeTestRule.setContent {
      if (showSource.value) {
        Box(Modifier.hazeSource(hazeState).size(50.dp))
      }
      Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
    }
    composeTestRule.waitForIdle()

    showSource.value = false
    assertIdleWithinTimeout("after removing source node")
  }

  @Test
  fun blurEffectBlockMutation_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val drawBehind = mutableStateOf(false)

    composeTestRule.setContent {
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
    composeTestRule.waitForIdle()

    drawBehind.value = true
    assertIdleWithinTimeout("after blur effect block mutation")
  }

  @Test
  fun rapidAlternatingMutations_doNotInfiniteLoop() {
    val hazeState = HazeState()
    val flag = mutableStateOf(false)

    composeTestRule.setContent {
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
    composeTestRule.waitForIdle()

    repeat(5) {
      flag.value = !flag.value
      assertIdleWithinTimeout("on alternating mutation #$it")
    }
  }
}
