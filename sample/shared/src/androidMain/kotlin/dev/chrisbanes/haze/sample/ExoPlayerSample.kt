// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import android.view.LayoutInflater
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.sample.shared.R

@Composable
@OptIn(ExperimentalHazeApi::class)
fun ExoPlayerSample(
  effect: SampleEffect,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()

  val context = LocalContext.current

  val exoPlayer = remember(context) {
    ExoPlayer.Builder(context).build()
  }

  DisposableEffect(Unit) {
    exoPlayer.setMediaItem(MediaItem.fromUri(BIG_BUCK_BUNNY))
    exoPlayer.prepare()
    exoPlayer.play()

    onDispose { exoPlayer.release() }
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(16 / 9f),
  ) {
    AndroidView(
      factory = { ctx ->
        // For Haze to work with video players, they need to be configured to use a TextureView.
        // When using ExoPlayer's PlayerView, that needs to be done via a layout attribute.
        LayoutInflater.from(ctx).inflate(R.layout.exoplayer, null) as PlayerView
      },
      update = { playerView ->
        playerView.player = exoPlayer
      },
      modifier = Modifier
        .fillMaxSize()
        .hazeSource(hazeState),
    )

    val shape = RoundedCornerShape(16.dp)
    val glassBackgroundColor = MaterialTheme.colorScheme.surface
    val glassTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.14f)

    Spacer(
      Modifier
        .fillMaxSize(0.5f)
        .align(Alignment.Center)
        .then(
          when (effect) {
            SampleEffect.Blur ->
              Modifier
                .clip(shape)
                .hazeBlur(
                  input = HazeInput.Sources(hazeState),
                  style = HazeMaterials.ultraThin(),
                )

            SampleEffect.Glass ->
              Modifier
                .hazeGlass(
                  input = HazeInput.Sources(hazeState),
                  style = GlassStyle.regular.then {
                    backgroundColor(glassBackgroundColor)
                    tint(glassTint)
                    shape(shape)
                  },
                )
                .clip(shape)
          },
        ),
    )

    Surface(
      modifier = Modifier
        .align(Alignment.TopStart)
        .windowInsetsPadding(WindowInsets.statusBars)
        .padding(16.dp),
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
      shadowElevation = 6.dp,
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier.size(48.dp).testTag("back"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back",
        )
      }
    }
  }
}

private const val BIG_BUCK_BUNNY =
  "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
