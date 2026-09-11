// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
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
  var tab by remember { mutableStateOf(MusicPlayerTab.Home) }
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
  val colors = MaterialTheme.colorScheme
  val hazeState = rememberHazeState()
  val input = HazeInput.Backdrop(hazeState)
  val expanded = tab == MusicPlayerTab.NowPlaying
  CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
    BoxWithConstraints(modifier.fillMaxSize().background(colors.surface).testTag("glass_music_player")) {
      val wide = maxWidth > maxHeight
      val artworkSize = if (wide) (maxHeight * 0.62f).coerceAtMost(360.dp) else (maxWidth - 64.dp).coerceAtMost(maxHeight * 0.36f).coerceAtMost(340.dp)
      if (expanded) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
          MusicArtwork(currentTrack, Modifier.fillMaxSize().blur(90.dp))
          Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.surface.copy(alpha = 0.68f), colors.surface.copy(alpha = 0.94f)))))
        }
        Column(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues()).verticalScroll(rememberScrollState()).padding(horizontal = 32.dp)) {
          Box(Modifier.fillMaxWidth().height(44.dp).clickable { onTabSelected(MusicPlayerTab.Home) }.semantics { contentDescription = "Close Now Playing" }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp, 5.dp).clip(CircleShape).background(colors.onSurface.copy(alpha = 0.25f)))
          }
          if (wide) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
              Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { AlbumCover(currentTrack, Modifier.size(artworkSize)) }
              Column(Modifier.weight(1f)) {
                NowPlayingControls(currentTrack, positionMillis, isPlaying, shuffleEnabled, input, onPlayPause, onPrevious, onNext, onShuffleChanged, onSeekStarted, onSeek, onSeekFinished, { onTabSelected(MusicPlayerTab.Library) }, compact = true)
              }
            }
          } else {
            Spacer(Modifier.height(18.dp))
            AlbumCover(currentTrack, Modifier.size(artworkSize).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(32.dp))
            NowPlayingControls(currentTrack, positionMillis, isPlaying, shuffleEnabled, input, onPlayPause, onPrevious, onNext, onShuffleChanged, onSeekStarted, onSeek, onSeekFinished, { onTabSelected(MusicPlayerTab.Library) })
            Spacer(Modifier.height(24.dp))
          }
        }
      } else {
        Column(Modifier.fillMaxSize().hazeSource(hazeState).verticalScroll(rememberScrollState()).padding(WindowInsets.safeDrawing.asPaddingValues()).padding(bottom = 180.dp)) {
          Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.onSurface) }
            Text(if (tab == MusicPlayerTab.Home) "Home" else "Library", modifier = Modifier.weight(1f), fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            Box(Modifier.size(40.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) { Icon(MusicNoteIcon, null, tint = colors.onPrimaryContainer) }
          }
          if (tab == MusicPlayerTab.Home) {
            Text("Top Picks for You", Modifier.padding(start = 24.dp, top = 28.dp, bottom = 14.dp), fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              tracks.forEachIndexed { index, track ->
                Column(
                  Modifier.width(240.dp).clip(RoundedCornerShape(14.dp)).clickable {
                    onTrackSelected(index)
                    onTabSelected(MusicPlayerTab.NowPlaying)
                  }.background(colors.surfaceContainer),
                ) {
                  MusicArtwork(track, Modifier.fillMaxWidth().aspectRatio(1f))
                  Column(Modifier.padding(14.dp)) {
                    Text(if (index == 0) "ON REPEAT" else "PICKED FOR YOU", color = colors.primary, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    Text(track.album, Modifier.padding(top = 5.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = colors.onSurfaceVariant, fontSize = 14.sp)
                  }
                }
              }
            }
            Text("Recently Played", Modifier.padding(start = 24.dp, top = 30.dp, bottom = 14.dp), fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
              tracks.reversed().forEach { track ->
                Column(
                  Modifier.width(154.dp).clickable {
                    onTrackSelected(tracks.indexOf(track))
                    onTabSelected(MusicPlayerTab.NowPlaying)
                  },
                ) {
                  MusicArtwork(track, Modifier.size(154.dp).clip(RoundedCornerShape(8.dp)))
                  Text(track.album, Modifier.padding(top = 8.dp), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(track.artist, color = colors.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
                }
              }
            }
          } else {
            Text("Albums", Modifier.padding(24.dp), color = colors.primary, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            tracks.forEachIndexed { index, track ->
              Row(Modifier.fillMaxWidth().clickable { onTrackSelected(index) }.padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MusicArtwork(track, Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.weight(1f)) {
                  Text(track.title, fontSize = 17.sp, fontWeight = if (index == currentTrackIndex) FontWeight.SemiBold else FontWeight.Normal)
                  Text(track.artist, color = colors.onSurfaceVariant, fontSize = 15.sp)
                }
                if (index == currentTrackIndex) Icon(MusicNoteIcon, "Current track", tint = colors.primary, modifier = Modifier.size(20.dp))
              }
            }
          }
        }
        Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 600.dp).fillMaxWidth().padding(WindowInsets.safeDrawing.asPaddingValues()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            Modifier.fillMaxWidth().hazeGlass(
              input = input,
              style = remember(colors) {
                GlassStyle.regular then GlassStyle {
                  shape(RoundedCornerShape(24.dp))
                  tint(colors.surface.copy(alpha = 0.7f))
                }
              },
            )
              .clip(RoundedCornerShape(24.dp)).clickable { onTabSelected(MusicPlayerTab.NowPlaying) }.testTag("music_mini_player").padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            MusicArtwork(currentTrack, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
              Text(currentTrack.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(currentTrack.artist, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onPlayPause) { Icon(if (isPlaying) PauseIcon else PlayIcon, if (isPlaying) "Pause" else "Play", tint = colors.onSurface) }
            IconButton(onClick = onNext) { Icon(NextTrackIcon, "Next track", tint = colors.onSurface) }
          }
          GlassBottomTabs(selectedIndex = if (tab == MusicPlayerTab.Home) 0 else 1, tabs = listOf("Home", "Library"), onSelected = { onTabSelected(if (it == 0) MusicPlayerTab.Home else MusicPlayerTab.Library) }, input = input, modifier = Modifier.fillMaxWidth()) { label, selected ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(if (label == "Home") HomeIcon else LibraryIcon, null, Modifier.size(21.dp), tint = if (selected) colors.primary else colors.onSurface)
              Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (selected) colors.primary else colors.onSurface)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AlbumCover(track: MusicTrack, modifier: Modifier) {
  MusicArtwork(track, modifier.shadow(18.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)))
}

@Composable
private fun NowPlayingControls(
  track: MusicTrack,
  positionMillis: Long,
  isPlaying: Boolean,
  shuffleEnabled: Boolean,
  input: HazeInput,
  onPlayPause: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onShuffleChanged: (Boolean) -> Unit,
  onSeekStarted: () -> Unit,
  onSeek: (Long) -> Unit,
  onSeekFinished: () -> Unit,
  onLibrary: () -> Unit,
  compact: Boolean = false,
) {
  val colors = MaterialTheme.colorScheme
  var volume by remember { mutableFloatStateOf(0.65f) }
  Column(Modifier.fillMaxWidth()) {
    Text(track.title, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(track.artist, fontSize = 20.sp, color = colors.onSurfaceVariant, maxLines = 1)
    Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
    GlassSlider(value = positionMillis.toFloat(), valueRange = 0f..track.durationMillis.toFloat(), onValueChange = { onSeek(it.toLong()) }, onValueChangeStarted = onSeekStarted, onValueChangeFinished = onSeekFinished, input = input, modifier = Modifier.testTag("music_progress"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(formatTime(positionMillis), fontSize = 12.sp, color = colors.onSurfaceVariant)
      Text("−${formatTime(track.durationMillis - positionMillis)}", fontSize = 12.sp, color = colors.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else 20.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) { Icon(PreviousTrackIcon, "Previous track", Modifier.size(36.dp)) }
      IconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp).testTag("music_play")) { Icon(if (isPlaying) PauseIcon else PlayIcon, if (isPlaying) "Pause" else "Play", Modifier.size(48.dp)) }
      IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) { Icon(NextTrackIcon, "Next track", Modifier.size(36.dp)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Icon(VolumeIcon, "Simulated volume", Modifier.size(18.dp), tint = colors.onSurfaceVariant)
      GlassSlider(value = volume, onValueChange = { volume = it }, input = input, modifier = Modifier.weight(1f).semantics { contentDescription = "Simulated volume" })
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Shuffle", fontSize = 13.sp, color = colors.onSurfaceVariant)
        GlassToggle(checked = shuffleEnabled, onCheckedChange = onShuffleChanged, input = input, modifier = Modifier.testTag("music_shuffle").semantics { contentDescription = "Shuffle" })
      }
      GlassButton(input = input, onClick = onLibrary) { Icon(LibraryIcon, "Library", Modifier.size(20.dp), tint = colors.onSurface) }
    }
  }
}

private fun formatTime(millis: Long): String = "${millis / 60_000}:${((millis / 1_000) % 60).toString().padStart(2, '0')}"

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

private val HomeIcon = musicIcon("Home") {
  moveTo(3f, 11f)
  lineTo(12f, 3f)
  lineTo(21f, 11f)
  lineTo(19f, 11f)
  lineTo(19f, 21f)
  lineTo(14f, 21f)
  lineTo(14f, 14f)
  lineTo(10f, 14f)
  lineTo(10f, 21f)
  lineTo(5f, 21f)
  lineTo(5f, 11f)
  close()
}
private val LibraryIcon = musicIcon("Library") {
  moveTo(3f, 4f)
  lineTo(6f, 4f)
  lineTo(6f, 21f)
  lineTo(3f, 21f)
  close()
  moveTo(9f, 4f)
  lineTo(12f, 4f)
  lineTo(12f, 21f)
  lineTo(9f, 21f)
  close()
  moveTo(15f, 3f)
  lineTo(18f, 2f)
  lineTo(23f, 20f)
  lineTo(20f, 21f)
  close()
}
private val MusicNoteIcon = musicIcon("Music") {
  moveTo(9f, 4f)
  lineTo(20f, 2f)
  lineTo(20f, 17f)
  lineTo(16f, 20f)
  lineTo(13f, 18f)
  lineTo(16f, 15f)
  lineTo(18f, 15f)
  lineTo(18f, 7f)
  lineTo(11f, 9f)
  lineTo(11f, 20f)
  lineTo(7f, 23f)
  lineTo(4f, 21f)
  lineTo(7f, 18f)
  lineTo(9f, 18f)
  close()
}
private val VolumeIcon = musicIcon("Volume") {
  moveTo(3f, 9f)
  lineTo(7f, 9f)
  lineTo(13f, 4f)
  lineTo(13f, 20f)
  lineTo(7f, 15f)
  lineTo(3f, 15f)
  close()
  moveTo(16f, 7f)
  lineTo(19f, 10f)
  lineTo(19f, 14f)
  lineTo(16f, 17f)
  lineTo(16f, 14f)
  lineTo(17f, 12f)
  lineTo(16f, 10f)
  close()
}
