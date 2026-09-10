// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.test.ContextTest
import dev.chrisbanes.haze.sample.components.GlassBottomTabs
import dev.chrisbanes.haze.sample.components.GlassButton
import dev.chrisbanes.haze.sample.components.GlassSlider
import dev.chrisbanes.haze.sample.components.GlassToggle
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlassMusicComponentsTest : ContextTest() {
  @Test
  fun sliderTapAndDrag_updateValueAndCompleteGesture() = runComposeUiTest {
    var value by mutableFloatStateOf(0f)
    var starts by mutableIntStateOf(0)
    var finishes by mutableIntStateOf(0)
    setContent {
      GlassSlider(value, { value = it }, HazeInput.Content, Modifier.testTag("slider"), onValueChangeStarted = { starts++ }, onValueChangeFinished = { finishes++ })
    }
    onNodeWithTag("slider").performTouchInput { click(Offset(width * 0.75f, height / 2f)) }
    onNodeWithTag("slider").performTouchInput { down(Offset(width * 0.2f, height / 2f)); moveTo(Offset(width * 0.8f, height / 2f)); up() }
    runOnIdle {
      assertThat(value > 0.7f).isEqualTo(true)
      assertThat(starts).isEqualTo(2)
      assertThat(finishes).isEqualTo(2)
    }
  }

  @Test
  fun disabledSlider_ignoresSemanticProgress() = runComposeUiTest {
    var value by mutableFloatStateOf(0f)
    setContent { GlassSlider(value, { value = it }, HazeInput.Content, Modifier.testTag("slider"), enabled = false) }
    onNodeWithTag("slider").performTouchInput { click() }
    onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0f, 0f..1f, 0)))
      .performSemanticsAction(SemanticsActions.SetProgress) { action -> action(0.8f) }
    runOnIdle { assertThat(value).isEqualTo(0f) }
  }

  @Test
  fun sliderKeyboard_adjustsFocusedValue() = runComposeUiTest {
    var value by mutableFloatStateOf(0.5f)
    setContent {
      GlassSlider(value, { value = it }, HazeInput.Content, Modifier.testTag("slider"))
    }
    onNodeWithTag("slider").performSemanticsAction(SemanticsActions.RequestFocus) { action -> action() }
    onNodeWithTag("slider").assertIsFocused()
    onNodeWithTag("slider").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
    runOnIdle { assertThat(value > 0.5f).isEqualTo(true) }
  }

  @Test
  fun toggleDrag_updatesControlledValue() = runComposeUiTest {
    var checked by mutableStateOf(false)
    setContent { GlassToggle(checked, { checked = it }, HazeInput.Content, Modifier.testTag("toggle")) }
    onNodeWithTag("toggle").performTouchInput { down(Offset(width * 0.2f, height / 2f)); moveTo(Offset(width * 0.9f, height / 2f)); up() }
    runOnIdle { assertThat(checked).isEqualTo(true) }
  }

  @Test
  fun disabledButton_doesNotDeliverClick() = runComposeUiTest {
    var clicks by mutableIntStateOf(0)
    setContent {
      GlassButton(input = HazeInput.Content, enabled = false, onClick = { clicks++ }, modifier = Modifier.testTag("disabled")) { Text("Disabled") }
    }
    onNodeWithTag("disabled").performClick()
    runOnIdle { assertThat(clicks).isEqualTo(0) }
  }

  @Test
  fun buttonAndToggle_deliverControlledChanges() = runComposeUiTest {
    var clicks by mutableIntStateOf(0)
    var checked by mutableStateOf(false)
    setContent {
      Column {
        GlassButton(input = HazeInput.Content, onClick = { clicks++ }, modifier = Modifier.testTag("play")) { Text("Play") }
        GlassToggle(checked = checked, onCheckedChange = { checked = it }, input = HazeInput.Content)
      }
    }
    onNodeWithTag("play").performClick()
    onNodeWithContentDescription("Glass toggle").performClick()
    runOnIdle {
      assertThat(clicks).isEqualTo(1)
      assertThat(checked).isEqualTo(true)
    }
  }

  @Test
  fun sliderSemanticsAndTabs_updateCallerState() = runComposeUiTest {
    var progress by mutableFloatStateOf(0f)
    var tab by mutableIntStateOf(0)
    setContent {
      GlassSlider(value = progress, onValueChange = { progress = it }, input = HazeInput.Content)
      GlassBottomTabs(selectedIndex = tab, tabs = listOf("Now Playing", "Library"), onSelected = { tab = it }, input = HazeInput.Content)
    }
    onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0f, 0f..1f, 0)))
      .performSemanticsAction(SemanticsActions.SetProgress) { action -> action(0.6f) }
    onNodeWithText("Library").performClick()
    runOnIdle {
      assertThat(progress).isEqualTo(0.6f)
      assertThat(tab).isEqualTo(1)
    }
    onNodeWithText("Library").assertIsSelected()
  }
}
