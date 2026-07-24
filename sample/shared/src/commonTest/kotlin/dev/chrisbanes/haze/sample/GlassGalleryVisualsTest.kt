// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlassGalleryVisualsTest : ContextTest() {
  @Test
  fun demoChrome_showsAndForwardsConfiguredActions() = runComposeUiTest {
    var playPauseCount = 0
    var resetCount = 0
    var recordingMode = false
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
          onEnterRecordingMode = { recordingMode = true },
          onReset = { resetCount++ },
          isPlaying = true,
          onPlayPause = { playPauseCount++ },
        )
      }
    }

    onNodeWithContentDescription("Back").assertIsDisplayed()
    onNodeWithContentDescription("Enter recording mode").assertIsDisplayed()
    onNodeWithContentDescription("Pause animation").assertIsDisplayed()
    onNodeWithContentDescription("Reset demo").assertIsDisplayed()

    onNodeWithContentDescription("Pause animation")
      .performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
    onNodeWithContentDescription("Reset demo")
      .performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
    onNodeWithContentDescription("Enter recording mode")
      .performSemanticsAction(SemanticsActions.OnClick) { action -> action() }

    assertThat(playPauseCount).isEqualTo(1)
    assertThat(resetCount).isEqualTo(1)
    assertThat(recordingMode).isTrue()
  }
}
