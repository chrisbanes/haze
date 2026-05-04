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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecompositionLoopInstrumentationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

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
    composeTestRule.waitForIdle()
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
    composeTestRule.waitForIdle()
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
    composeTestRule.waitForIdle()
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
    composeTestRule.waitForIdle()
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
      composeTestRule.waitForIdle()
    }
  }

  @Test
  fun lazyColumnScroll_doesNotInfiniteLoop() {
    val hazeState = HazeState()

    composeTestRule.setContent {
      Box(Modifier.fillMaxSize()) {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxSize()
            .testTag("lazy_column")
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
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("lazy_column").performTouchInput {
      swipeUp()
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun lazyColumnItemCountChange_doesNotInfiniteLoop() {
    val hazeState = HazeState()
    val itemCount = mutableIntStateOf(10)

    composeTestRule.setContent {
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
    composeTestRule.waitForIdle()

    itemCount.intValue = 50
    composeTestRule.waitForIdle()
  }
}
