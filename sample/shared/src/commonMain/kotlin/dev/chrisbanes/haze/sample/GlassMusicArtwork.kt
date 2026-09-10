// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
internal fun MusicArtwork(track: MusicTrack, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.background(
      Brush.linearGradient(listOf(Color(0xff281c53), Color(0xffe28d86), Color(0xff29152d))),
    ),
  ) {
    Canvas(Modifier.fillMaxSize()) {
      drawCircle(Color(0xfff7d7a8).copy(alpha = 0.55f), size.minDimension * 0.26f, Offset(size.width * 0.28f, size.height * 0.3f))
      drawCircle(Color(0xff3f275c).copy(alpha = 0.62f), size.minDimension * 0.32f, Offset(size.width * 0.72f, size.height * 0.7f))
    }
    AsyncImage(
      model = track.artworkUrl,
      contentDescription = "${track.album} album artwork",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
  }
}
