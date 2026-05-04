// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.test.RecompositionCounter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecompositionCountInstrumentationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  companion object {
    // Threshold of 1 ensures we catch any extra unnecessary recomposition.
    // Some tests produce 2 recompositions (e.g. due to Compose scheduling
    // two frames for a single atomic change) - those use isBetween(1, 2)
    // with a comment explaining why.
    private const val RECOMPOSITION_THRESHOLD = 1
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
            GradientBox(Modifier.hazeEffect(hazeState).size(100.dp))
          }
        }
      }
    }
    composeTestRule.waitForIdle()

    // Reset after initial composition. Safe because SideEffect-based counting
    // does not read snapshot state during composition.
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
    val sourceCounter = mutableIntStateOf(0)
    val showSource = mutableStateOf(false)

    composeTestRule.setContent {
      RecompositionCounter(sourceCounter) {
        if (showSource.value) {
          GradientBox(Modifier.hazeSource(hazeState).size(50.dp))
        }
      }
      RecompositionCounter(effectCounter) {
        GradientBox(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0
    sourceCounter.intValue = 0

    showSource.value = true
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after adding source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
    assertThat(sourceCounter.intValue, "source recompositions after adding source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun removingSourceNode_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val sourceCounter = mutableIntStateOf(0)
    val showSource = mutableStateOf(true)

    composeTestRule.setContent {
      RecompositionCounter(sourceCounter) {
        if (showSource.value) {
          GradientBox(Modifier.hazeSource(hazeState).size(50.dp))
        }
      }
      RecompositionCounter(effectCounter) {
        GradientBox(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0
    sourceCounter.intValue = 0

    showSource.value = false
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after removing source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
    assertThat(sourceCounter.intValue, "source recompositions after removing source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun blurEnabledToggle_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val sourceCounter = mutableIntStateOf(0)
    val blurEnabled = mutableStateOf(true)

    composeTestRule.setContent {
      RecompositionCounter(sourceCounter) {
        Box(Modifier.hazeSource(hazeState).size(100.dp)) {
          RecompositionCounter(effectCounter) {
            GradientBox(
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
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0
    sourceCounter.intValue = 0

    blurEnabled.value = false
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after blur toggle")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
    assertThat(sourceCounter.intValue, "source recompositions after blur toggle")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun addingEffectNode_doesNotExcessRecomposeSource() {
    val hazeState = HazeState()
    val sourceCounter = mutableIntStateOf(0)
    val showEffect = mutableStateOf(false)

    composeTestRule.setContent {
      RecompositionCounter(sourceCounter) {
        Box(Modifier.hazeSource(hazeState).size(100.dp)) {
          if (showEffect.value) {
            GradientBox(Modifier.hazeEffect(hazeState).size(100.dp))
          }
        }
      }
    }
    composeTestRule.waitForIdle()

    sourceCounter.intValue = 0

    showEffect.value = true
    composeTestRule.waitForIdle()

    assertThat(sourceCounter.intValue, "source recompositions after adding effect node")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun multipleSimultaneousAreaChanges_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val showSources = mutableStateOf(false)

    composeTestRule.setContent {
      if (showSources.value) {
        GradientBox(Modifier.hazeSource(hazeState).size(20.dp))
        GradientBox(Modifier.hazeSource(hazeState).size(20.dp))
        GradientBox(Modifier.hazeSource(hazeState).size(20.dp))
        GradientBox(Modifier.hazeSource(hazeState).size(20.dp))
        GradientBox(Modifier.hazeSource(hazeState).size(20.dp))
      }
      RecompositionCounter(effectCounter) {
        GradientBox(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    showSources.value = true
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after adding 5 sources")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun lazyColumnScroll_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)

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
            GradientBox(
              Modifier
                .fillMaxWidth()
                .height(80.dp),
            )
          }
        }

        RecompositionCounter(effectCounter) {
          GradientBox(
            Modifier
              .hazeEffect(hazeState)
              .fillMaxWidth()
              .height(56.dp),
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    composeTestRule.onNodeWithTag("lazy_column").performTouchInput {
      swipeUp()
    }
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after scroll")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }

  @Test
  fun lazyColumnItemCountChange_causesBoundedRecompositions() {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
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
            GradientBox(
              Modifier
                .fillMaxWidth()
                .height(80.dp),
            )
          }
        }

        RecompositionCounter(effectCounter) {
          GradientBox(
            Modifier
              .hazeEffect(hazeState)
              .fillMaxWidth()
              .height(56.dp),
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    effectCounter.intValue = 0

    itemCount.intValue = 50
    composeTestRule.waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after item count change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }
}

@Composable
private fun GradientBox(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.background(
      Brush.linearGradient(
        colors = listOf(
          Color(0xFF6B73FF),
          Color(0xFF9B59B6),
          Color(0xFFE74C3C),
          Color(0xFFF39C12),
        ),
      ),
    ),
  )
}
