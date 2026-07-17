// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.chrisbanes.haze.rememberHazeState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GlassGalleryVisualsTest {
  @Test
  fun horizontalOverscanCoversViewportAtBoundedTranslationExtremes() {
    val left = galleryBackdropHorizontalTransform(
      offsetFraction = -0.08f,
      overscanFraction = 0.08f,
    )
    val right = galleryBackdropHorizontalTransform(
      offsetFraction = 0.08f,
      overscanFraction = 0.08f,
    )

    assertEquals(1.16f, left.scaleX)
    assertEquals(-0.08f, left.translationFraction)
    assertEquals(0.08f, right.translationFraction)
  }

  @Test
  fun demoChrome_onlyShowsRequestedActions() = runComposeUiTest {
    setContent {
      val hazeState = rememberHazeState()
      Box(Modifier.fillMaxSize()) {
        GalleryBackdrop(
          hazeState = hazeState,
          artworkIndex = 0,
          backdrop = GlassGalleryBackdropId.Gallery,
          modifier = Modifier.fillMaxSize(),
        )
        DemoChrome(
          hazeState = hazeState,
          onBack = {},
          onEnterRecordingMode = {},
          isPlaying = true,
          onPlayPause = {},
          onReset = null,
        )
      }
    }

    onNodeWithContentDescription("Back").assertIsDisplayed()
    onNodeWithContentDescription("Enter recording mode").assertIsDisplayed()
    onNodeWithContentDescription("Pause animation").assertIsDisplayed()
    onNodeWithContentDescription("Reset demo").assertDoesNotExist()
  }
}
