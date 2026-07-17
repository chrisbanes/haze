// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.chrisbanes.haze.ExperimentalHazeApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalHazeApi::class, ExperimentalTestApi::class)
class GlassLabSampleTest {
  @Test
  fun presetBackdropAdvancedAndResetEventsUpdatePlainState() = runComposeUiTest {
    var state by mutableStateOf(GlassLabState())
    setContent {
      GlassLabSampleContent(
        state = state,
        recordingMode = false,
        onStateChanged = { state = it },
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    onNodeWithText("Prism").performClick()
    assertEquals(GlassLabPresetId.Prism, state.preset)

    onNodeWithText("Grid").performClick()
    assertEquals(GlassGalleryBackdropId.Grid, state.backdrop)

    onNodeWithText("Advanced").performClick()
    onNodeWithText("Optics").assertIsDisplayed()

    onNodeWithContentDescription("Reset demo").performClick()
    assertEquals(GlassLabState(), state)
  }

  @Test
  fun recordingModeKeepsSpecimenAndHidesExplanatoryCopy() = runComposeUiTest {
    setContent {
      GlassLabSampleContent(
        state = GlassLabState(),
        recordingMode = true,
        onStateChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    onNodeWithContentDescription("Glass specimen").assertIsDisplayed()
    onNodeWithText("Choose a material preset").assertDoesNotExist()
  }

  @Test
  fun recordingModeSpecimenTapDoesNotRevealChrome() = runComposeUiTest {
    var recordingModeChanged = false
    setContent {
      GlassLabSampleContent(
        state = GlassLabState(),
        recordingMode = true,
        onStateChanged = {},
        onRecordingModeChanged = { recordingModeChanged = !it },
        onBack = {},
      )
    }

    onNodeWithContentDescription("Glass specimen").performTouchInput { click() }
    assertEquals(false, recordingModeChanged)
  }
}
