// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
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

  private suspend fun ComposeUiTest.awaitIdleWithTimeout(description: String) {
    try {
      withTimeout(IDLE_TIMEOUT_MS) {
        waitForIdle()
      }
    } catch (e: TimeoutCancellationException) {
      throw AssertionError(
        "Infinite recomposition loop detected $description. " +
          "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
        e,
      )
    }
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

    awaitIdleWithTimeout("after positionStrategy mutation")
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

    awaitIdleWithTimeout("after adding source node")
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

    awaitIdleWithTimeout("after removing source node")
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

    awaitIdleWithTimeout("after blur effect block mutation")
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
      awaitIdleWithTimeout("on alternating mutation #$it")
    }
  }

  @Test
  fun lazyColumnScroll_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val listState = androidx.compose.foundation.lazy.LazyListState()

    setContent {
      Box(Modifier.fillMaxSize()) {
        LazyColumn(
          state = listState,
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
        ) {
          items(50) { index ->
            Spacer(
              Modifier
                .fillMaxWidth()
                .height(80.dp),
            )
          }
        }

        Spacer(
          Modifier
            .hazeEffect(hazeState)
            .fillMaxWidth()
            .height(56.dp),
        )
      }
    }
    waitForIdle()

    listState.scrollToItem(25)

    awaitIdleWithTimeout("after LazyColumn scroll")
  }

  @Test
  fun lazyColumnItemCountChange_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val itemCount = mutableIntStateOf(10)

    setContent {
      Box(Modifier.fillMaxSize()) {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
        ) {
          items(itemCount.intValue) { index ->
            Spacer(
              Modifier
                .fillMaxWidth()
                .height(80.dp),
            )
          }
        }

        Spacer(
          Modifier
            .hazeEffect(hazeState)
            .fillMaxWidth()
            .height(56.dp),
        )
      }
    }
    waitForIdle()

    itemCount.intValue = 50

    awaitIdleWithTimeout("after LazyColumn item count change")
  }
}
