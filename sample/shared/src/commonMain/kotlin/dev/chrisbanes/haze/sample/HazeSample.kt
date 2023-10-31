// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.haze

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HazeSample(appTitle: String) {
  MaterialTheme {
    Scaffold(
      topBar = {
        LargeTopAppBar(
          title = { Text(text = appTitle) },
          colors = TopAppBarDefaults.largeTopAppBarColors(Color.Transparent),
          modifier = Modifier.fillMaxWidth(),
        )
      },
      modifier = Modifier.fillMaxSize(),
    ) { contentPadding ->
      BoxWithConstraints {
        val topBarBounds = with(LocalDensity.current) {
          Rect(
            Offset(0f, 0f),
            Offset(maxWidth.toPx(), contentPadding.calculateTopPadding().toPx()),
          )
        }

        LazyVerticalGrid(
          columns = GridCells.Adaptive(128.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = contentPadding,
          modifier = Modifier
            .fillMaxSize()
            .haze(
              RoundRect(
                topBarBounds,
                topLeft = CornerRadius.Zero,
                topRight = CornerRadius.Zero,
                bottomLeft = CornerRadius(40f),
                bottomRight = CornerRadius(40f),
              ),
              backgroundColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
          items(50) { index ->
            ImageItem(
              text = "${index + 1}",
              modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3 / 4f),
            )
          }
        }
      }
    }
  }
}
