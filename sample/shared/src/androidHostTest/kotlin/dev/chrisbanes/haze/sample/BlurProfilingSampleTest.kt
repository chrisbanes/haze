// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import assertk.assertThat
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
class BlurProfilingSampleTest : ContextTest() {
  @Test
  fun stableScenario_exposesTheSettledStartProtocol() = runComposeUiTest {
    setContent {
      BlurProfilingSampleContent(
        state = remember { BlurProfilingState() },
        navController = rememberNavController(),
        onBack = {},
      )
    }

    onNodeWithTag("blur_profiling_select_stable_adaptive")
      .performScrollTo()
      .performClick()
    onNodeWithTag("blur_profiling_selected_stable_adaptive").assertIsDisplayed()
    onNodeWithTag("blur_profiling_phase_ready").assertIsDisplayed()
    onNodeWithTag("blur_profiling_start").performClick()
    onNodeWithTag("blur_profiling_phase_complete").assertIsDisplayed()
  }

  @Test
  fun progressiveBalancedScenario_exposesTheSettledStartProtocol() = runComposeUiTest {
    setContent {
      BlurProfilingSampleContent(
        state = remember { BlurProfilingState() },
        navController = rememberNavController(),
        onBack = {},
      )
    }

    onNodeWithTag("blur_profiling_select_progressive_balanced")
      .performScrollTo()
      .performClick()
    onNodeWithTag("blur_profiling_selected_progressive_balanced").assertIsDisplayed()
    onNodeWithTag("blur_profiling_phase_ready").assertIsDisplayed()
    onNodeWithTag("blur_profiling_start").performClick()
    onNodeWithTag("blur_profiling_phase_complete").assertIsDisplayed()
  }

  @Test
  fun backdropSourceUpdateScenario_exposesTheSettledStartProtocol() = runComposeUiTest {
    setContent {
      BlurProfilingSampleContent(
        state = remember { BlurProfilingState() },
        navController = rememberNavController(),
        onBack = {},
      )
    }

    onNodeWithTag("blur_profiling_select_backdrop_source_update_quality")
      .performScrollTo()
      .performClick()
    onNodeWithTag("blur_profiling_selected_backdrop_source_update_quality").assertIsDisplayed()
    onNodeWithTag("blur_profiling_phase_ready").assertIsDisplayed()
    onNodeWithTag("blur_profiling_start").performClick()
    onNodeWithTag("blur_profiling_phase_complete").assertIsDisplayed()
  }

  @Test
  fun sourceProfilingScenario_disablesPlatformBackdropEligibility() {
    val previous = HazeFeatureFlags.isPlatformBackdropEnabled
    try {
      runComposeUiTest {
        val sourceState = BlurProfilingState().apply {
          select(BlurProfilingScenario.SourceUpdateQuality)
        }
        setContent {
          BlurProfilingSampleContent(
            state = sourceState,
            navController = rememberNavController(),
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
        val state = BlurProfilingState().apply {
          select(BlurProfilingScenario.BackdropSourceUpdateQuality)
        }
        setContent {
          BlurProfilingSampleContent(
            state = state,
            navController = rememberNavController(),
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
