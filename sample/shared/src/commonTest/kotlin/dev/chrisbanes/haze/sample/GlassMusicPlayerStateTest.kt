// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class GlassMusicPlayerStateTest {
  @Test
  fun advance_onlyMovesWhilePlayingAndNotSeeking() {
    val state = MusicPlayerState(MusicCatalog, randomTrackIndex = { 0 })
    state.advanceBy(1_000)
    assertThat(state.positionMillis).isEqualTo(0)
    state.isPlaying = true
    state.beginSeeking()
    state.advanceBy(1_000)
    assertThat(state.positionMillis).isEqualTo(0)
    state.endSeeking()
    state.advanceBy(1_000)
    assertThat(state.positionMillis).isEqualTo(1_000)
  }

  @Test
  fun shuffleMode_canBeDisabledForOrderedNextTrack() {
    val state = MusicPlayerState(MusicCatalog, randomTrackIndex = { 1 })
    state.updateShuffleEnabled(true)
    state.updateShuffleEnabled(false)
    state.next()
    assertThat(state.shuffleEnabled).isEqualTo(false)
    assertThat(state.currentTrackIndex).isEqualTo(0)
  }

  @Test
  fun seeking_clampsAndRestoresPlayback() {
    val state = MusicPlayerState(MusicCatalog, randomTrackIndex = { 1 })
    state.isPlaying = true

    state.beginSeeking()
    state.seekTo(state.currentTrack.durationMillis + 1)
    state.endSeeking()

    assertThat(state.positionMillis).isEqualTo(state.currentTrack.durationMillis)
    assertThat(state.isPlaying).isEqualTo(true)
  }

  @Test
  fun completedTrack_wrapsAndShuffleChoosesAnotherTrack() {
    val state = MusicPlayerState(MusicCatalog, randomTrackIndex = { 1 })
    state.selectTrack(MusicCatalog.lastIndex)
    state.isPlaying = true

    state.advanceBy(state.currentTrack.durationMillis)
    assertThat(state.currentTrackIndex).isEqualTo(0)

    state.shuffle()
    assertThat(state.currentTrackIndex).isEqualTo(2)
  }
}
