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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
      bottomBar = {
        var selectedIndex by remember { mutableIntStateOf(0) }

        NavigationBar(
          containerColor = Color.Transparent,
          modifier = Modifier.fillMaxWidth(),
        ) {
          for (i in (0 until 3)) {
            NavigationBarItem(
              selected = selectedIndex == i,
              onClick = { selectedIndex = i },
              icon = {
                Icon(
                  imageVector = when (i) {
                    0 -> Icons.Default.Call
                    1 -> Icons.Default.Lock
                    else -> Icons.Default.Search
                  },
                  contentDescription = null,
                )
              },
            )
          }
        }
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
        val bottomBarsBounds = with(LocalDensity.current) {
          Rect(
            Offset(0f, maxHeight.toPx() - contentPadding.calculateBottomPadding().toPx()),
            Offset(maxWidth.toPx(), maxHeight.toPx()),
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
              topBarBounds,
              bottomBarsBounds,
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
