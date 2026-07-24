// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runComposeUiTest
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalHazeApi::class, ExperimentalTestApi::class)
class GlassProductSampleTest : ContextTest() {
  @Test
  fun productGlassStyle_alwaysUsesAdaptiveOptics() {
    assertThat(productGlassStyle(isDark = false).optics).isEqualTo(GlassOptics.Adaptive)
    assertThat(productGlassStyle(isDark = true).optics).isEqualTo(GlassOptics.Adaptive)
  }

  @Test
  fun normalMode_showsProductSceneTopBar() = runComposeUiTest {
    setContent {
      GlassProductSampleContent(
        selectedArtworkIndex = 0,
        favorite = false,
        recordingMode = false,
        onArtworkSelected = {},
        onFavoriteChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    onNodeWithContentDescription("Back").assertIsDisplayed()
    onNodeWithText("Glass Gallery").assertIsDisplayed()
    onNodeWithText("1 / ${GalleryArtworks.size}").assertIsDisplayed()
    onNodeWithContentDescription("Enter recording mode").assertIsDisplayed()
  }

  @Test
  fun recordingMode_keepsProductSceneTopBar() = runComposeUiTest {
    setContent {
      GlassProductSampleContent(
        selectedArtworkIndex = 0,
        favorite = false,
        recordingMode = true,
        onArtworkSelected = {},
        onFavoriteChanged = {},
        onRecordingModeChanged = {},
        onBack = {},
      )
    }

    onNodeWithContentDescription("Back").assertIsDisplayed()
    onNodeWithText("Glass Gallery").assertIsDisplayed()
    onNodeWithText("1 / ${GalleryArtworks.size}").assertIsDisplayed()
    onAllNodesWithContentDescription("Enter recording mode").assertCountEquals(0)
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
