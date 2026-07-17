// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassOptics
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalHazeApi::class, ExperimentalTestApi::class)
class GlassProductSampleTest {
  @Test
  fun productGlassStyle_alwaysUsesAdaptiveOptics() {
    assertEquals(GlassOptics.Adaptive, productGlassStyle(isDark = false).optics)
    assertEquals(GlassOptics.Adaptive, productGlassStyle(isDark = true).optics)
  }

  @Test
  fun nextAndFavoriteActions_updatePlainUiState() = runComposeUiTest {
    var selectedIndex by mutableIntStateOf(0)
    var favorite by mutableStateOf(false)
    setContent {
      GlassProductSampleContent(
        selectedArtworkIndex = selectedIndex,
        favorite = favorite,
        recordingMode = false,
        onArtworkSelected = { selectedIndex = it },
        onFavoriteChanged = { favorite = it },
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    onNodeWithContentDescription("Next artwork").performClick()
    onNodeWithText("Signal Garden").assertIsDisplayed()
    onNodeWithContentDescription("Favorite artwork").performClick()
    onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
    onNodeWithContentDescription("Artwork information").performClick()
    onNodeWithText("An emerald and ultraviolet poster with vertical signal bars").assertIsDisplayed()
  }

  @Test
  fun horizontalSwipe_selectsTheNextArtwork() = runComposeUiTest {
    var selectedIndex by mutableIntStateOf(0)
    setContent {
      GlassProductSampleContent(
        selectedArtworkIndex = selectedIndex,
        favorite = false,
        recordingMode = false,
        onArtworkSelected = { selectedIndex = it },
        onFavoriteChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    onNodeWithTag("glass_product_pager").performTouchInput { swipeLeft() }
    waitForIdle()
    onNodeWithText("Signal Garden").assertIsDisplayed()
  }
}
