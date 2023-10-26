// Copyright 2023, Christopher Banes and the project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.sample.ui.theme.SampleTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      Samples(appTitle = title.toString())
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Samples(appTitle: String) {
  SampleTheme {
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
        LazyVerticalGrid(
          columns = GridCells.Adaptive(128.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = contentPadding,
          modifier = Modifier
            .fillMaxSize()
            .haze(
              Rect(
                0f,
                0f,
                constraints.maxWidth.toFloat(),
                with(LocalDensity.current) { contentPadding.calculateTopPadding().toPx() },
              ),
              color = MaterialTheme.colorScheme.surface,
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

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
  Samples(appTitle = "Haze Sample")
}
