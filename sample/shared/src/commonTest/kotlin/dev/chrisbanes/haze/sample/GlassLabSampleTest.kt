// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalHazeApi::class, ExperimentalTestApi::class)
class GlassLabSampleTest : ContextTest() {
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

    val optionNames = buildList {
      addAll(SelectableGlassLabPresets.map { it.name })
      addAll(GlassGalleryBackdropId.entries.map { it.name })
      addAll(GlassLabInteractionMode.entries.map { it.name })
    }
    optionNames.forEach { optionName ->
      val textLayouts = mutableListOf<TextLayoutResult>()
      onNode(hasText(optionName) and hasClickAction())
        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
          action(textLayouts)
        }
      val textLayout = textLayouts.single()
      assertEquals(1, textLayout.lineCount, optionName)
      assertTrue(
        textLayout.multiParagraph.intrinsics.maxIntrinsicWidth <= textLayout.size.width + 1f,
        "$optionName: size=${textLayout.size}, intrinsicWidth=" +
          textLayout.multiParagraph.intrinsics.maxIntrinsicWidth,
      )
    }
  }

  @Test
  fun narrowLabSizesSelectorsToWholeVisibleItems() {
    assertEquals(288.dp, labSegmentedButtonWidth(288.dp, itemCount = 5))
    assertEquals(174.dp, labSegmentedButtonWidth(348.dp, itemCount = 5))
    assertEquals(160.dp, labSegmentedButtonWidth(800.dp, itemCount = 5))
  }

  @Test
  fun controlsForwardPresetBackdropInteractionAndAdvancedEvents() = runComposeUiTest {
    var state by mutableStateOf(GlassLabState())
    setContent {
      LabControls(
        state = state,
        recordingMode = false,
        onStateChanged = { state = it },
        modifier = Modifier.fillMaxSize(),
      )
    }

    onNode(hasText("Prism") and hasClickAction())
      .performScrollTo()
      .performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
    assertEquals(GlassLabPresetId.Prism, state.preset)

    onNode(hasText("Grid") and hasClickAction())
      .performScrollTo()
      .performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
    assertEquals(GlassGalleryBackdropId.Grid, state.backdrop)

    onNode(hasText("Off") and hasClickAction())
      .performScrollTo()
      .performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
    assertEquals(GlassLabInteractionMode.Off, state.interaction)

    onNodeWithText("Advanced")
      .performScrollTo()
      .performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
    assertTrue(state.advancedExpanded)
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
