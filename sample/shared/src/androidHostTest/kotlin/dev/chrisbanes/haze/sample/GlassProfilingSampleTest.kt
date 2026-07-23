// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@Config(qualifiers = "w393dp-h698dp-440dpi")
class GlassProfilingSampleTest : ContextTest() {
  @Test
  fun androidSamples_registersTheProfilingDestination() {
    assertTrue(Samples.any { it.title == "Glass — Profiling" })
  }

  @Test
  fun noGlassScenario_exposesReadyStartAndCompleteProtocol() = runComposeUiTest {
    setContent {
      GlassProfilingSampleContent(
        state = remember { GlassProfilingState() },
        onBack = {},
      )
    }

    onNodeWithTag("glass_profiling_select_source_update_no_glass").performClick()
    onNodeWithTag("glass_profiling_selected_source_update_no_glass").assertIsDisplayed()
    onNodeWithTag("glass_profiling_phase_ready").assertIsDisplayed()
    onNodeWithTag("glass_profiling_start").performClick()
    onNodeWithTag("glass_profiling_phase_complete").assertIsDisplayed()
  }
}
