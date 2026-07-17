// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalHazeApi::class, ExperimentalTestApi::class)
class GlassLabSampleTest {
  @Test
  fun narrowLabRevealsSelectedTrailingPreset() = runComposeUiTest {
    var state by mutableStateOf(GlassLabState())
    setContent {
      GlassLabSampleContent(
        state = state,
        recordingMode = false,
        onStateChanged = { state = it },
        onRecordingModeChanged = {},
        onBack = {},
        modifier = Modifier.width(320.dp),
      )
    }

    state = state.selectPreset(GlassLabPresetId.Prism)

    onNode(hasText("Prism") and hasClickAction()).assertIsDisplayed()
  }

  @Test
  fun narrowLabKeepsSelectorLabelsVisibleAsControls() = runComposeUiTest {
    setContent {
      GlassLabSampleContent(
        state = GlassLabState(),
        recordingMode = false,
        onStateChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
        modifier = Modifier.width(320.dp),
      )
    }

    (SelectableGlassLabPresets + GlassGalleryBackdropId.entries).forEach { option ->
      val textLayouts = mutableListOf<TextLayoutResult>()
      onNode(hasText(option.name) and hasClickAction())
        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
          action(textLayouts)
        }
      assertEquals(1, textLayouts.single().lineCount, option.name)
    }
  }

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

    onNode(hasText("Prism") and hasClickAction()).performClick()
    assertEquals(GlassLabPresetId.Prism, state.preset)

    onNode(hasText("Grid") and hasClickAction()).performClick()
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
