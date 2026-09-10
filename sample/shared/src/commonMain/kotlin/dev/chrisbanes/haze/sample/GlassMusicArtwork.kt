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
      drawCircle(Color(0xffffd166).copy(alpha = 0.52f), size.minDimension * 0.26f, Offset(size.width * 0.28f, size.height * 0.3f))
      drawCircle(Color(0xffe76f51).copy(alpha = 0.52f), size.minDimension * 0.32f, Offset(size.width * 0.72f, size.height * 0.7f))
      repeat(6) { index ->
        val x = size.width * (index + 1) / 7f
        drawLine(Color.White.copy(alpha = 0.26f), Offset(x, 0f), Offset(x - size.width * 0.35f, size.height), size.minDimension * 0.035f)
      }
    }
    AsyncImage(
      model = track.artworkUrl,
      contentDescription = "${track.album} album artwork",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
  }
}
