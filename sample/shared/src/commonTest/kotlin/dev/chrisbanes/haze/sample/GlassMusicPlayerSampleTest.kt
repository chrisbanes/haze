// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlassMusicPlayerSampleTest {
  @Test
  fun librarySelection_updatesNowPlayingTrackAndPreservesPlayback() = runComposeUiTest {
    var trackIndex by mutableStateOf(0)
    var tab by mutableStateOf(MusicPlayerTab.Library)
    var playing by mutableStateOf(true)
    setContent {
      GlassMusicPlayerSampleContent(
        currentTrack = MusicCatalog[trackIndex], tracks = MusicCatalog, currentTrackIndex = trackIndex,
        positionMillis = 0, isPlaying = playing, shuffleEnabled = false, tab = tab,
        onTabSelected = { tab = it }, onPlayPause = { playing = !playing }, onPrevious = {}, onNext = {}, onShuffle = {}, onShuffleChanged = {},
        onSeekStarted = {}, onSeek = {}, onSeekFinished = {}, onTrackSelected = { trackIndex = it }, onBack = {},
      )
    }
    onNodeWithText("Loud Places").performClick()
    runOnIdle { assertThat(trackIndex).isEqualTo(1) }
    tab = MusicPlayerTab.NowPlaying
    waitForIdle()
    onNodeWithText("Loud Places").assertExists()
    runOnIdle { assertThat(playing).isEqualTo(true) }
  }
}
