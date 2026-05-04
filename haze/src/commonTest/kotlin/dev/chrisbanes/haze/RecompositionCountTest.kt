// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.test.ContextTest
import dev.chrisbanes.haze.test.RecompositionCounter
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RecompositionCountTest : ContextTest() {

  companion object {
    private const val RECOMPOSITION_THRESHOLD = 2
  }

  @Test
  fun positionStrategyChange_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val sourceCounter = mutableIntStateOf(0)

    setContent {
      RecompositionCounter(sourceCounter) {
        Box(Modifier.hazeSource(hazeState).size(100.dp)) {
          RecompositionCounter(effectCounter) {
            Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
          }
        }
      }
    }
    waitForIdle()

    effectCounter.intValue = 0
    sourceCounter.intValue = 0

    hazeState.positionStrategy = HazePositionStrategy.Screen
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
    assertThat(sourceCounter.intValue, "source recompositions")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun addingSourceNode_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val showSource = mutableStateOf(false)

    setContent {
      if (showSource.value) {
        Box(Modifier.hazeSource(hazeState).size(50.dp))
      }
      RecompositionCounter(effectCounter) {
        Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    showSource.value = true
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after adding source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun removingSourceNode_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val showSource = mutableStateOf(true)

    setContent {
      if (showSource.value) {
        Box(Modifier.hazeSource(hazeState).size(50.dp))
      }
      RecompositionCounter(effectCounter) {
        Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    showSource.value = false
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after removing source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun blurEnabledToggle_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val blurEnabled = mutableStateOf(true)

    setContent {
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
    waitForIdle()

    effectCounter.intValue = 0

    blurEnabled.value = false
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after blur toggle")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun multipleSimultaneousAreaChanges_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val showSources = mutableStateOf(false)

    setContent {
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
    waitForIdle()

    effectCounter.intValue = 0

    showSources.value = true
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after adding 5 sources")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }
}
