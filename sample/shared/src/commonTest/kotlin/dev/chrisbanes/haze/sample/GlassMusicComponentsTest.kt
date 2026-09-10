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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.sample.components.GlassBottomTabs
import dev.chrisbanes.haze.sample.components.GlassButton
import dev.chrisbanes.haze.sample.components.GlassSlider
import dev.chrisbanes.haze.sample.components.GlassToggle
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlassMusicComponentsTest {
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
