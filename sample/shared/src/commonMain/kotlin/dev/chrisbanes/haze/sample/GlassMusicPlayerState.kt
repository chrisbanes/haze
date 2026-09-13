// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
public class MusicTrack(
  val title: String,
  val artist: String,
  val album: String,
  val durationMillis: Long,
  val artworkUrl: String,
  val sourceUrl: String,
)

internal val MusicCatalog = listOf(
  MusicTrack(
    title = "Give Life Back to Music",
    artist = "Daft Punk",
    album = "Random Access Memories",
    durationMillis = 4 * 60_000L + 34_000L,
    artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/e8/43/5f/e8435ffa-b6b9-b171-40ab-4ff3959ab661/886443919266.jpg/600x600bb.jpg",
    sourceUrl = "https://music.apple.com/us/album/random-access-memories/617154241",
  ),
  MusicTrack(
    title = "Loud Places",
    artist = "Jamie xx",
    album = "In Colour",
    durationMillis = 4 * 60_000L + 43_000L,
    artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/b9/dc/ce/b9dcce9c-8cf8-4105-0fab-fb048702bce2/cover.jpg/600x600bb.jpg",
    sourceUrl = "https://music.apple.com/us/album/in-colour/1525506447",
  ),
  MusicTrack(
    title = "Look at the Sky",
    artist = "Porter Robinson",
    album = "Nurture",
    durationMillis = 5 * 60_000L + 10_000L,
    artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/f7/b0/67/f7b067e8-6759-c676-6b6d-d4f5b049d23f/858275061918_Cover.jpg/600x600bb.jpg",
    sourceUrl = "https://music.apple.com/us/album/nurture/1894533111",
  ),
)

public enum class MusicPlayerTab { NowPlaying, Library, Home }

/** A deliberately small, composition-owned simulation. It never starts audio playback. */
internal class MusicPlayerState(
  private val tracks: List<MusicTrack>,
  private val randomTrackIndex: (Int) -> Int = { kotlin.random.Random.nextInt(it) },
) {
  var currentTrackIndex by mutableIntStateOf(0)
  var positionMillis by mutableLongStateOf(0)
  var isPlaying by mutableStateOf(false)
  var isSeeking by mutableStateOf(false)
    private set
  var shuffleEnabled by mutableStateOf(false)
    private set

  val currentTrack: MusicTrack get() = tracks[currentTrackIndex]

  fun togglePlaying() {
    isPlaying = !isPlaying
  }

  fun advanceBy(millis: Long) {
    if (!isPlaying || isSeeking) return
    val next = positionMillis + millis
    if (next >= currentTrack.durationMillis) {
      next()
    } else {
      positionMillis = next
    }
  }

  fun beginSeeking() {
    isSeeking = true
  }

  fun seekTo(millis: Long) {
    positionMillis = millis.coerceIn(0, currentTrack.durationMillis)
  }

  fun endSeeking() {
    isSeeking = false
  }

  fun selectTrack(index: Int) {
    currentTrackIndex = index.mod(tracks.size)
    positionMillis = 0
  }

  fun previous() = selectTrack(currentTrackIndex - 1)

  fun next() = selectTrack(if (shuffleEnabled) differentTrackIndex() else currentTrackIndex + 1)

  fun shuffle() {
    shuffleEnabled = true
    selectTrack(differentTrackIndex())
  }

  fun updateShuffleEnabled(enabled: Boolean) {
    if (enabled && !shuffleEnabled) shuffle() else shuffleEnabled = enabled
  }

  private fun differentTrackIndex(): Int {
    if (tracks.size < 2) return currentTrackIndex
    val candidate = randomTrackIndex(tracks.size - 1).mod(tracks.size - 1)
    return if (candidate >= currentTrackIndex) candidate + 1 else candidate
  }
}
