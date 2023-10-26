// Copyright 2023, Christopher Banes and the project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import dev.chrisbanes.haze.glassBlur
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
    var currentSample by remember { mutableStateOf<Sample?>(null) }

    Scaffold(
      topBar = {
        LargeTopAppBar(
          title = { Text(text = currentSample?.title ?: appTitle) },
          navigationIcon = {
            if (currentSample != null) {
              IconButton(onClick = { currentSample = null }) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = "Navigate back",
                )
              }
            }
          },
          colors = TopAppBarDefaults.largeTopAppBarColors(Color.Transparent),
          modifier = Modifier.fillMaxWidth(),
        )
      },
      modifier = Modifier.fillMaxSize(),
    ) { contentPadding ->
      BackHandler(enabled = currentSample != null) {
        currentSample = null
      }

      BoxWithConstraints {
        Crossfade(
          targetState = currentSample,
          label = "Samples Crossfade",
          modifier = Modifier
            .fillMaxSize()
            .glassBlur(
              listOf(
                Rect(
                  0f,
                  0f,
                  constraints.maxWidth.toFloat(),
                  with(LocalDensity.current) { contentPadding.calculateTopPadding().toPx() },
                )
              ),
              color = MaterialTheme.colorScheme.surface,
            ),
        ) { sample ->
          if (sample != null) {
            sample.content(contentPadding)
          } else {
            LazyColumn(
              contentPadding = contentPadding,
              modifier = Modifier.fillMaxSize()
            ) {
              items(Samples) { sample ->
                ListItem(
                  headlineContent = { Text(text = sample.title) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { currentSample = sample },
                )
              }
            }
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
