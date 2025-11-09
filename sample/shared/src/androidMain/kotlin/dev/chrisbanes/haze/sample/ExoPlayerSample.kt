// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import android.view.LayoutInflater
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.sample.shared.R

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ExoPlayerSample(blurEnabled: Boolean) {
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
    modifier = Modifier
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

    val style = HazeMaterials.ultraThin()

    Spacer(
      Modifier
        .fillMaxSize(0.5f)
        .align(Alignment.Center)
        .clip(MaterialTheme.shapes.large)
        .hazeEffect(hazeState) {
          blurEffect {
            this.blurEnabled = blurEnabled
            this.style = style
          }
        },
    )
  }
}

private const val BIG_BUCK_BUNNY =
  "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
