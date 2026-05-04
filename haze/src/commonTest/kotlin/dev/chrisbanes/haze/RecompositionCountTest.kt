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
    // Threshold of 1 ensures we catch any extra unnecessary recomposition.
    // Some tests produce 2 recompositions (e.g. due to Compose scheduling
    // two frames for a single atomic change) - those use isBetween(1, 2)
    // with a comment explaining why.
    private const val RECOMPOSITION_THRESHOLD = 1
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

    // Reset after initial composition. Safe because SideEffect-based counting
    // does not read snapshot state during composition.
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
    val sourceCounter = mutableIntStateOf(0)
    val showSource = mutableStateOf(false)

    setContent {
      RecompositionCounter(sourceCounter) {
        if (showSource.value) {
          Box(Modifier.hazeSource(hazeState).size(50.dp))
        }
      }
      RecompositionCounter(effectCounter) {
        Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    waitForIdle()

    effectCounter.intValue = 0
    sourceCounter.intValue = 0

    showSource.value = true
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after adding source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
    assertThat(sourceCounter.intValue, "source recompositions after adding source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun removingSourceNode_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val sourceCounter = mutableIntStateOf(0)
    val showSource = mutableStateOf(true)

    setContent {
      RecompositionCounter(sourceCounter) {
        if (showSource.value) {
          Box(Modifier.hazeSource(hazeState).size(50.dp))
        }
      }
      RecompositionCounter(effectCounter) {
        Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    waitForIdle()

    effectCounter.intValue = 0
    sourceCounter.intValue = 0

    showSource.value = false
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after removing source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
    assertThat(sourceCounter.intValue, "source recompositions after removing source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun blurEnabledToggle_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val sourceCounter = mutableIntStateOf(0)
    val blurEnabled = mutableStateOf(true)

    setContent {
      RecompositionCounter(sourceCounter) {
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
    }
    waitForIdle()

    effectCounter.intValue = 0
    sourceCounter.intValue = 0

    blurEnabled.value = false
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after blur toggle")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
    assertThat(sourceCounter.intValue, "source recompositions after blur toggle")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun addingEffectNode_doesNotExcessRecomposeSource() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceCounter = mutableIntStateOf(0)
    val showEffect = mutableStateOf(false)

    setContent {
      RecompositionCounter(sourceCounter) {
        Box(Modifier.hazeSource(hazeState).size(100.dp)) {
          if (showEffect.value) {
            Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
          }
        }
      }
    }
    waitForIdle()

    sourceCounter.intValue = 0

    showEffect.value = true
    waitForIdle()

    assertThat(sourceCounter.intValue, "source recompositions after adding effect node")
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

  @Test
  fun lazyColumnScroll_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
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

        RecompositionCounter(effectCounter) {
          Spacer(
            Modifier
              .hazeEffect(hazeState)
              .fillMaxWidth()
              .height(56.dp),
          )
        }
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    listState.scrollToItem(25)
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after scroll")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun lazyColumnItemCountChange_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
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

        RecompositionCounter(effectCounter) {
          Spacer(
            Modifier
              .hazeEffect(hazeState)
              .fillMaxWidth()
              .height(56.dp),
          )
        }
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    itemCount.intValue = 50
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after item count change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }
}
