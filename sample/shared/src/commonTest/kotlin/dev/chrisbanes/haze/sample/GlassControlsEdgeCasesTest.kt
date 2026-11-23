// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.sample.components.GlassBottomTabs
import dev.chrisbanes.haze.sample.components.GlassSlider
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlassControlsEdgeCasesTest : ContextTest() {
  @Test
  fun bottomTabs_emptyListRendersAndDisplaysTabsWhenTheyAppear() = runComposeUiTest {
    var tabs by mutableStateOf(emptyList<String>())
    setContent {
      GlassBottomTabs(0, tabs, {}, HazeInput.Content, Modifier.testTag("tabs"))
    }
    onNodeWithTag("tabs").assertExists()

    tabs = listOf("One", "Two")
    waitForIdle()
    onNodeWithText("One").assertIsSelected()
  }

  @Test
  fun sliderResizeDuringDrag_finishesOnce() = runComposeUiTest {
    var value by mutableFloatStateOf(0f)
    var sliderWidth by mutableStateOf(200.dp)
    var starts by mutableIntStateOf(0)
    var finishes by mutableIntStateOf(0)
    setContent {
      GlassSlider(
        value,
        { value = it },
        HazeInput.Content,
        Modifier.width(sliderWidth).testTag("slider"),
        onValueChangeStarted = { starts++ },
        onValueChangeFinished = { finishes++ },
      )
    }
    onNodeWithTag("slider").performTouchInput {
      down(Offset(width * 0.2f, height / 2f))
      moveTo(Offset(width * 0.4f, height / 2f))
    }
    sliderWidth = 300.dp
    waitForIdle()
    onNodeWithTag("slider").performTouchInput { up() }
    runOnIdle {
      assertThat(starts).isEqualTo(1)
      assertThat(finishes).isEqualTo(1)
    }
  }

  @Test
  fun sliderDisabledDuringDrag_finishesOnce() = runComposeUiTest {
    var value by mutableFloatStateOf(0f)
    var enabled by mutableStateOf(true)
    var starts by mutableIntStateOf(0)
    var finishes by mutableIntStateOf(0)
    setContent {
      GlassSlider(
        value,
        { value = it },
        HazeInput.Content,
        Modifier.testTag("slider"),
        enabled = enabled,
        onValueChangeStarted = { starts++ },
        onValueChangeFinished = { finishes++ },
      )
    }
    onNodeWithTag("slider").performTouchInput {
      down(Offset(width * 0.2f, height / 2f))
      moveTo(Offset(width * 0.4f, height / 2f))
    }
    enabled = false
    waitForIdle()
    onNodeWithTag("slider").performTouchInput { up() }
    runOnIdle {
      assertThat(starts).isEqualTo(1)
      assertThat(finishes).isEqualTo(1)
    }
  }
}
