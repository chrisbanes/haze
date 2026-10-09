// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeFeatureFlags
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class)
@Config(qualifiers = "w393dp-h698dp-440dpi")
class GlassProfilingSampleTest : ContextTest() {
  @Test
  fun androidSamples_registersTheProfilingDestination() {
    assertThat(Samples.map { it.title }).contains("Glass — Profiling")
  }

  @Test
  fun noGlassScenario_exposesReadyStartAndCompleteProtocol() = runComposeUiTest {
    setContent {
      GlassProfilingSampleContent(
        state = remember { GlassProfilingState() },
        onBack = {},
      )
    }

    onNodeWithTag("glass_profiling_select_source_update_no_glass")
      .performScrollTo()
      .performClick()
    onNodeWithTag("glass_profiling_selected_source_update_no_glass").assertIsDisplayed()
    onNodeWithTag("glass_profiling_phase_ready").assertIsDisplayed()
    onNodeWithTag("glass_profiling_start").performClick()
    onNodeWithTag("glass_profiling_phase_complete").assertIsDisplayed()
  }

  @Test
  fun backdropSourceUpdate9_exposesNineIndependentGlassEffects() = runComposeUiTest {
    setContent {
      GlassProfilingSampleContent(
        state = remember { GlassProfilingState() },
        onBack = {},
      )
    }

    onNodeWithTag("glass_profiling_select_backdrop_source_update_9")
      .performScrollTo()
      .performClick()
    onNodeWithTag("glass_profiling_selected_backdrop_source_update_9").assertIsDisplayed()
    onNodeWithTag("glass_profiling_phase_ready").assertIsDisplayed()
    repeat(9) { index ->
      onNodeWithTag("glass_profiling_surface_$index").assertIsDisplayed()
    }
  }

  @Test
  fun resizeQualityScenarios_changeGridBoundsWithoutMovingSourceOrReplacingEffects() {
    listOf(
      GlassProfilingScenario.ResizeQuality to 1,
      GlassProfilingScenario.ResizeQuality9 to 9,
    ).forEach { (scenario, effectCount) ->
      runComposeUiTest {
        mainClock.autoAdvance = false
        val state = GlassProfilingState().apply { select(scenario) }
        setContent {
          GlassProfilingSampleContent(
            state = state,
            onBack = {},
          )
        }

        repeat(GLASS_PROFILING_SETTLING_FRAMES + 1) {
          mainClock.advanceTimeByFrame()
        }
        waitForIdle()
        onNodeWithTag("glass_profiling_phase_ready").assertIsDisplayed()
        val effectNodeIds = List(effectCount) { index ->
          onNodeWithTag("glass_profiling_surface_$index").fetchSemanticsNode().id
        }
        onNodeWithTag("glass_profiling_start").performClick()

        val sourceBounds = onNodeWithTag("glass_profiling_source").fetchSemanticsNode().boundsInRoot
        val observedWidths = mutableListOf<androidx.compose.ui.unit.Dp>()
        repeat(6) {
          mainClock.advanceTimeBy(500L)
          val expectedSize = glassProfilingResizeSize(state.progress)
          observedWidths += expectedSize.width
          onNodeWithTag("glass_profiling_surface")
            .assertWidthIsEqualTo(expectedSize.width)
            .assertHeightIsEqualTo(expectedSize.height)
          assertThat(
            onNodeWithTag("glass_profiling_source").fetchSemanticsNode().boundsInRoot,
          ).isEqualTo(sourceBounds)
          val currentEffectNodeIds = List(effectCount) { index ->
            onNodeWithTag("glass_profiling_surface_$index").fetchSemanticsNode().id
          }
          assertThat(currentEffectNodeIds).isEqualTo(effectNodeIds)
        }
        assertThat(observedWidths.any { it >= 275.dp }).isTrue()
        assertThat(observedWidths.any { it <= 201.dp }).isTrue()

        mainClock.advanceTimeBy(500L)
        onNodeWithTag("glass_profiling_phase_complete").assertIsDisplayed()
        val expectedFinalSize = glassProfilingResizeSize(1f)
        onNodeWithTag("glass_profiling_surface")
          .assertWidthIsEqualTo(expectedFinalSize.width)
          .assertHeightIsEqualTo(expectedFinalSize.height)
        assertThat(
          List(effectCount) { index ->
            onNodeWithTag("glass_profiling_surface_$index").fetchSemanticsNode().id
          },
        ).isEqualTo(effectNodeIds)
      }
    }
  }

  @Test
  fun effectAttach_settlesWithoutGlassBeforeExposingStart() = runComposeUiTest {
    mainClock.autoAdvance = false
    setContent {
      GlassProfilingSampleContent(
        state = remember { GlassProfilingState() },
        onBack = {},
      )
    }

    onNodeWithTag("glass_profiling_select_effect_attach").performClick()
    mainClock.advanceTimeByFrame()
    onNodeWithTag("glass_profiling_phase_settling").assertIsDisplayed()
    onNodeWithTag("glass_profiling_surface").assertDoesNotExist()
    onNodeWithTag("glass_profiling_start").assertDoesNotExist()

    repeat(GLASS_PROFILING_SETTLING_FRAMES + 1) {
      mainClock.advanceTimeByFrame()
    }
    waitForIdle()

    onNodeWithTag("glass_profiling_phase_ready").assertIsDisplayed()
    onNodeWithTag("glass_profiling_surface").assertDoesNotExist()
    onNodeWithTag("glass_profiling_start").performClick()
    mainClock.advanceTimeByFrame()
    onNodeWithTag("glass_profiling_surface").assertIsDisplayed()
  }

  @Test
  fun effectAttach9_attachesNineIndependentGlassEffects() = runComposeUiTest {
    mainClock.autoAdvance = false
    setContent {
      GlassProfilingSampleContent(
        state = remember { GlassProfilingState() },
        onBack = {},
      )
    }

    onNodeWithTag("glass_profiling_select_effect_attach_9").performClick()
    repeat(GLASS_PROFILING_SETTLING_FRAMES + 2) {
      mainClock.advanceTimeByFrame()
    }
    waitForIdle()

    onNodeWithTag("glass_profiling_surface").assertDoesNotExist()
    onNodeWithTag("glass_profiling_start").performClick()
    mainClock.advanceTimeByFrame()
    onNodeWithTag("glass_profiling_surface").assertIsDisplayed()
    repeat(9) { index ->
      onNodeWithTag("glass_profiling_surface_$index").assertIsDisplayed()
    }
  }

  @Test
  fun sourceProfilingScenario_disablesPlatformBackdropEligibility() {
    val previous = HazeFeatureFlags.isPlatformBackdropEnabled
    try {
      runComposeUiTest {
        val sourceState = GlassProfilingState().apply {
          select(GlassProfilingScenario.SourceUpdateQuality)
        }
        setContent {
          GlassProfilingSampleContent(
            state = sourceState,
            onBack = {},
          )
        }
        assertThat(HazeFeatureFlags.isPlatformBackdropEnabled).isFalse()
      }
      assertThat(HazeFeatureFlags.isPlatformBackdropEnabled).isEqualTo(previous)
    } finally {
      HazeFeatureFlags.isPlatformBackdropEnabled = previous
    }
  }

  @Test
  fun backdropProfilingScenario_enablesPlatformBackdropEligibility() {
    val previous = HazeFeatureFlags.isPlatformBackdropEnabled
    try {
      runComposeUiTest {
        val state = GlassProfilingState().apply {
          select(GlassProfilingScenario.BackdropSourceUpdateQuality)
        }
        setContent {
          GlassProfilingSampleContent(
            state = state,
            onBack = {},
          )
        }
        assertThat(HazeFeatureFlags.isPlatformBackdropEnabled).isTrue()
      }
      assertThat(HazeFeatureFlags.isPlatformBackdropEnabled).isEqualTo(previous)
    } finally {
      HazeFeatureFlags.isPlatformBackdropEnabled = previous
    }
  }
}
