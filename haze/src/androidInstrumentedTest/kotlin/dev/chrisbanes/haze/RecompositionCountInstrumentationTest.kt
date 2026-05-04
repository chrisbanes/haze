// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isLessThanOrEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecompositionCountInstrumentationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  companion object {
    private const val RECOMPOSITION_THRESHOLD = 2
  }

  @Test
  fun positionStrategyChange_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val sourceCounter = mutableIntStateOf(0)

    composeTestRule.setContent {
      RecompositionCounter(sourceCounter) {
        Box(Modifier.hazeSource(hazeState).size(100.dp)) {
          RecompositionCounter(effectCounter) {
            Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
          }
        }
      }
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0
    sourceCounter.intValue = 0

    hazeState.positionStrategy = HazePositionStrategy.Screen
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
    assertThat(sourceCounter.intValue, "source recompositions")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun addingSourceNode_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val showSource = mutableStateOf(false)

    composeTestRule.setContent {
      if (showSource.value) {
        Box(Modifier.hazeSource(hazeState).size(50.dp))
      }
      RecompositionCounter(effectCounter) {
        Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    showSource.value = true
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after adding source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun removingSourceNode_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val showSource = mutableStateOf(true)

    composeTestRule.setContent {
      if (showSource.value) {
        Box(Modifier.hazeSource(hazeState).size(50.dp))
      }
      RecompositionCounter(effectCounter) {
        Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    showSource.value = false
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after removing source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun blurEnabledToggle_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val blurEnabled = mutableStateOf(true)

    composeTestRule.setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        RecompositionCounter(effectCounter) {
          Spacer(
            Modifier
              .hazeEffect(hazeState) {
                drawContentBehind = blurEnabled.value
              }
              .size(100.dp),
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    blurEnabled.value = false
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after blur toggle")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun multipleSimultaneousAreaChanges_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val showSources = mutableStateOf(false)

    composeTestRule.setContent {
      if (showSources.value) {
        Box(Modifier.hazeSource(hazeState).size(20.dp))
        Box(Modifier.hazeSource(hazeState).size(20.dp))
        Box(Modifier.hazeSource(hazeState).size(20.dp))
        Box(Modifier.hazeSource(hazeState).size(20.dp))
        Box(Modifier.hazeSource(hazeState).size(20.dp))
      }
      RecompositionCounter(effectCounter) {
        Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    showSources.value = true
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after adding 5 sources")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }
}
