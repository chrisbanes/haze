// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.sample.components.GlassBottomTabs
import dev.chrisbanes.haze.sample.components.GlassButton
import dev.chrisbanes.haze.sample.components.GlassSlider
import dev.chrisbanes.haze.sample.components.GlassToggle
import kotlinx.coroutines.delay

@Composable
public fun GlassMusicPlayerSample(navController: NavHostController) {
  val player = remember { MusicPlayerState(MusicCatalog) }
  var tab by remember { mutableStateOf(MusicPlayerTab.NowPlaying) }
  SimulatedPlaybackTimer(player)
  GlassMusicPlayerSampleContent(
    tracks = MusicCatalog,
    currentTrackIndex = player.currentTrackIndex,
    positionMillis = player.positionMillis,
    isPlaying = player.isPlaying,
    shuffleEnabled = player.shuffleEnabled,
    tab = tab,
    onTabSelected = { tab = it },
    onPlayPause = player::togglePlaying,
    onPrevious = player::previous,
    onNext = player::next,
    onShuffleChanged = player::updateShuffleEnabled,
    onSeekStarted = player::beginSeeking,
    onSeek = player::seekTo,
    onSeekFinished = player::endSeeking,
    onTrackSelected = player::selectTrack,
    onBack = navController::navigateUp,
  )
}

@Composable
internal fun SimulatedPlaybackTimer(player: MusicPlayerState) {
  LaunchedEffect(player.isPlaying, player.isSeeking, player.currentTrackIndex) {
    while (player.isPlaying && !player.isSeeking) {
      delay(1_000)
      player.advanceBy(1_000)
    }
  }
}

/** The rendering seam for screenshot and compose tests; playback in this sample is simulated. */
@OptIn(ExperimentalHazeApi::class)
@Composable
public fun GlassMusicPlayerSampleContent(
  tracks: List<MusicTrack>,
  currentTrackIndex: Int,
  positionMillis: Long,
  isPlaying: Boolean,
  shuffleEnabled: Boolean,
  tab: MusicPlayerTab,
  onTabSelected: (MusicPlayerTab) -> Unit,
  onPlayPause: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onShuffleChanged: (Boolean) -> Unit,
  onSeekStarted: () -> Unit,
  onSeek: (Long) -> Unit,
  onSeekFinished: () -> Unit,
  onTrackSelected: (Int) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentTrack = tracks[currentTrackIndex]
  val hazeState = rememberHazeState()
  val input = HazeInput.Backdrop(hazeState)
  val foreground = MaterialTheme.colorScheme.onSurface
  BoxWithConstraints(modifier = modifier.fillMaxSize().testTag("glass_music_player")) {
    val wide = maxWidth > maxHeight
    val compactWide = wide && maxHeight < 500.dp
    val artworkHeight = when {
      compactWide -> 160.dp
      wide -> 300.dp
      maxHeight < 700.dp -> 160.dp
      else -> 260.dp
    }
    MusicArtwork(
      track = currentTrack,
      modifier = Modifier.fillMaxSize().hazeSource(hazeState),
    )
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.30f)))
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(WindowInsets.safeDrawing.asPaddingValues())
        .padding(20.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = foreground)
        }
        Text("Glass music", color = foreground, style = MaterialTheme.typography.titleLarge)
      }
      Box(Modifier.weight(1f)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
          if (tab == MusicPlayerTab.NowPlaying) {
            if (wide) {
              Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.fillMaxWidth()) {
                ArtworkCard(currentTrack, artworkHeight, Modifier.weight(1f).widthIn(max = 440.dp))
                PlayerControls(currentTrack, positionMillis, isPlaying, shuffleEnabled, input, foreground, onPlayPause, onPrevious, onNext, onShuffleChanged, onSeekStarted, onSeek, onSeekFinished, compact = compactWide, modifier = Modifier.weight(1f).padding(end = 12.dp))
              }
            } else {
              ArtworkCard(currentTrack, artworkHeight, Modifier.fillMaxWidth())
              Spacer(Modifier.height(18.dp))
              PlayerControls(currentTrack, positionMillis, isPlaying, shuffleEnabled, input, foreground, onPlayPause, onPrevious, onNext, onShuffleChanged, onSeekStarted, onSeek, onSeekFinished, modifier = Modifier.fillMaxWidth())
            }
          } else {
            Library(tracks, currentTrackIndex, input, foreground, onTrackSelected)
          }
        }
      }
      Spacer(Modifier.height(20.dp))
      GlassBottomTabs(
        selectedIndex = tab.ordinal,
        tabs = listOf("Now Playing", "Library"),
        onSelected = { onTabSelected(MusicPlayerTab.entries[it]) },
        input = input,
        modifier = Modifier.fillMaxWidth(),
        tabContent = { label, selected ->
          Text(label, color = foreground, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        },
      )
    }
  }
}

@Composable
private fun ArtworkCard(track: MusicTrack, height: androidx.compose.ui.unit.Dp, modifier: Modifier) {
  Box(modifier = modifier.height(height), contentAlignment = Alignment.Center) {
    MusicArtwork(
      track,
      Modifier
        .size(height)
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp)),
    )
  }
}

@Composable
private fun PlayerControls(
  track: MusicTrack,
  positionMillis: Long,
  isPlaying: Boolean,
  shuffleEnabled: Boolean,
  input: HazeInput,
  foreground: Color,
  onPlayPause: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onShuffleChanged: (Boolean) -> Unit,
  onSeekStarted: () -> Unit,
  onSeek: (Long) -> Unit,
  onSeekFinished: () -> Unit,
  compact: Boolean = false,
  modifier: Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 14.dp)) {
    Text(track.title, color = foreground, style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text("${track.artist} · ${track.album}", color = foreground.copy(alpha = 0.75f), style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    GlassSlider(
      value = positionMillis.toFloat(),
      valueRange = 0f..track.durationMillis.toFloat(),
      onValueChange = { onSeek(it.toLong()) },
      onValueChangeStarted = onSeekStarted,
      onValueChangeFinished = onSeekFinished,
      input = input,
      modifier = Modifier.fillMaxWidth().testTag("music_progress"),
    )
    Text("${formatTime(positionMillis)} / ${formatTime(track.durationMillis)}", color = foreground.copy(alpha = 0.72f))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onPrevious) { Icon(PreviousTrackIcon, "Previous track", tint = foreground) }
      GlassButton(input = input, onClick = onPlayPause, modifier = Modifier.size(if (compact) 48.dp else 64.dp).testTag("music_play")) {
        Icon(if (isPlaying) PauseIcon else PlayIcon, if (isPlaying) "Pause" else "Play", tint = foreground)
      }
      IconButton(onClick = onNext) { Icon(NextTrackIcon, "Next track", tint = foreground) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Shuffle", color = foreground.copy(alpha = 0.72f), modifier = Modifier.weight(1f))
      GlassToggle(
        checked = shuffleEnabled,
        onCheckedChange = onShuffleChanged,
        input = input,
        modifier = Modifier
          .testTag("music_shuffle")
          .semantics { contentDescription = "Shuffle" },
      )
    }
  }
}

@Composable
private fun Library(
  tracks: List<MusicTrack>,
  currentTrackIndex: Int,
  input: HazeInput,
  foreground: Color,
  onTrackSelected: (Int) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("Library", color = foreground, style = MaterialTheme.typography.headlineMedium)
    tracks.forEachIndexed { index, track ->
      GlassButton(input = input, onClick = { onTrackSelected(index) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
          Text(track.title, color = foreground, fontWeight = if (index == currentTrackIndex) FontWeight.Bold else FontWeight.Normal)
          Text(track.artist, color = foreground.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        }
      }
    }
  }
}

private fun formatTime(millis: Long): String {
  val minutes = millis / 60_000
  val seconds = (millis / 1_000) % 60
  return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private val PlayIcon = musicIcon("Play") {
  moveTo(8f, 5f)
  lineTo(19f, 12f)
  lineTo(8f, 19f)
  close()
}
private val PauseIcon = musicIcon("Pause") {
  moveTo(6f, 5f)
  lineTo(10f, 5f)
  lineTo(10f, 19f)
  lineTo(6f, 19f)
  close()
  moveTo(14f, 5f)
  lineTo(18f, 5f)
  lineTo(18f, 19f)
  lineTo(14f, 19f)
  close()
}
private val PreviousTrackIcon = musicIcon("Previous track") {
  moveTo(5f, 5f)
  lineTo(7f, 5f)
  lineTo(7f, 19f)
  lineTo(5f, 19f)
  close()
  moveTo(18f, 5f)
  lineTo(8f, 12f)
  lineTo(18f, 19f)
  close()
}
private val NextTrackIcon = musicIcon("Next track") {
  moveTo(17f, 5f)
  lineTo(19f, 5f)
  lineTo(19f, 19f)
  lineTo(17f, 19f)
  close()
  moveTo(6f, 5f)
  lineTo(16f, 12f)
  lineTo(6f, 19f)
  close()
}

private fun musicIcon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
  ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black), pathBuilder = block)
  }.build()
