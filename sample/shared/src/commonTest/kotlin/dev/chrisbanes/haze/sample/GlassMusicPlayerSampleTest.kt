// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlassMusicPlayerSampleTest : ContextTest() {
  @Test
  fun playbackTimer_advancesAndCancelsWithComposition() = runComposeUiTest {
    val player = MusicPlayerState(MusicCatalog)
    player.isPlaying = true
    var attached by mutableStateOf(true)
    mainClock.autoAdvance = false
    setContent { if (attached) SimulatedPlaybackTimer(player) }
    mainClock.advanceTimeBy(1_100)
    runOnIdle { assertThat(player.positionMillis).isEqualTo(1_000) }
    runOnIdle { player.togglePlaying() }
    mainClock.advanceTimeByFrame()
    waitForIdle()
    mainClock.advanceTimeBy(1_100)
    runOnIdle { assertThat(player.positionMillis).isEqualTo(1_000) }
    runOnIdle { player.togglePlaying() }
    mainClock.advanceTimeByFrame()
    waitForIdle()
    mainClock.advanceTimeBy(1_100)
    runOnIdle { assertThat(player.positionMillis).isEqualTo(2_000) }
    val positionBeforeRemoval = player.positionMillis
    runOnIdle { attached = false }
    mainClock.advanceTimeByFrame()
    waitForIdle()
    mainClock.advanceTimeBy(2_000)
    runOnIdle { assertThat(player.positionMillis).isEqualTo(positionBeforeRemoval) }
  }

  @Test
  fun librarySelection_updatesNowPlayingTrackAndPreservesPlayback() = runComposeUiTest {
    var trackIndex by mutableStateOf(0)
    var tab by mutableStateOf(MusicPlayerTab.Library)
    var playing by mutableStateOf(true)
    setContent {
      GlassMusicPlayerSampleContent(
        tracks = MusicCatalog, currentTrackIndex = trackIndex,
        positionMillis = 0, isPlaying = playing, shuffleEnabled = false, tab = tab,
        onTabSelected = { tab = it }, onPlayPause = { playing = !playing }, onPrevious = {}, onNext = {}, onShuffleChanged = {},
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

  @Test
  fun miniPlayer_dismissesToOriginatingTabWithoutLosingPlayback() = runComposeUiTest {
    var tab by mutableStateOf(MusicPlayerTab.Home)
    setContent {
      GlassMusicPlayerSampleContent(
        tracks = MusicCatalog, currentTrackIndex = 0, positionMillis = 90_000,
        isPlaying = true, shuffleEnabled = false, tab = tab,
        onTabSelected = { tab = it }, onPlayPause = {}, onPrevious = {}, onNext = {},
        onShuffleChanged = {}, onSeekStarted = {}, onSeek = {}, onSeekFinished = {},
        onTrackSelected = {}, onBack = {},
      )
    }
    onNodeWithText("Top Picks for You").assertExists()
    onNodeWithTag("music_mini_player").performClick()
    onNodeWithText("1:30").assertExists()
    onNodeWithContentDescription("Pause").assertExists()
    onNodeWithContentDescription("Close Now Playing").performClick()
    runOnIdle { assertThat(tab).isEqualTo(MusicPlayerTab.Home) }
    onNodeWithTag("music_mini_player").assertIsDisplayed()
    onNodeWithText("Library").performClick()
    onNodeWithTag("music_mini_player").performClick()
    onNodeWithText("1:30").assertExists()
    onNodeWithContentDescription("Pause").assertExists()
    onNodeWithContentDescription("Close Now Playing").performClick()
    runOnIdle { assertThat(tab).isEqualTo(MusicPlayerTab.Library) }
    onNodeWithText("Albums").assertExists()
  }

  @Test
  fun playerControls_updateHoistedStateAcrossTabChanges() = runComposeUiTest {
    val player = MusicPlayerState(MusicCatalog, randomTrackIndex = { 0 })
    var tab by mutableStateOf(MusicPlayerTab.NowPlaying)
    var isPlaying by mutableStateOf(false)
    setContent {
      GlassMusicPlayerSampleContent(
        tracks = MusicCatalog,
        currentTrackIndex = player.currentTrackIndex,
        positionMillis = player.positionMillis,
        isPlaying = isPlaying,
        shuffleEnabled = player.shuffleEnabled,
        tab = tab,
        onTabSelected = { tab = it },
        onPlayPause = { isPlaying = !isPlaying },
        onPrevious = player::previous,
        onNext = player::next,
        onShuffleChanged = player::updateShuffleEnabled,
        onSeekStarted = player::beginSeeking,
        onSeek = player::seekTo,
        onSeekFinished = player::endSeeking,
        onTrackSelected = player::selectTrack,
        onBack = {},
      )
    }
    onNodeWithTag("music_play").performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()
    waitForIdle()
    runOnIdle { assertThat(isPlaying).isEqualTo(true) }
    onNodeWithTag("music_shuffle").performScrollTo().assertIsDisplayed().performClick()
    waitForIdle()
    onNodeWithTag("music_progress").performScrollTo().assertIsDisplayed().performTouchInput {
      down(Offset(width * 0.2f, height / 2f))
      moveTo(Offset(width * 0.7f, height / 2f))
      up()
    }
    waitForIdle()
    onNodeWithContentDescription("Library").performScrollTo().performClick()
    waitForIdle()
    onNodeWithTag("music_mini_player").performClick()
    runOnIdle {
      assertThat(isPlaying).isEqualTo(true)
      assertThat(player.positionMillis).isGreaterThan(0L)
      assertThat(player.isSeeking).isEqualTo(false)
      assertThat(player.shuffleEnabled).isEqualTo(true)
      assertThat(tab).isEqualTo(MusicPlayerTab.NowPlaying)
    }
  }
}
