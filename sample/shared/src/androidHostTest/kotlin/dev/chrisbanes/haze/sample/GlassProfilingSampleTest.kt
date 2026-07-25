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
import assertk.assertThat
import assertk.assertions.contains
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
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
}
