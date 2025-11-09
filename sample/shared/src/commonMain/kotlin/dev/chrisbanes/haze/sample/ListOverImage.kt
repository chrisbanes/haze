// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun ListOverImage(navController: NavHostController, blurEnabled: Boolean) {
  var imageIndex by remember { mutableIntStateOf(0) }

  MaterialTheme {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = "List over Image") },
          navigationIcon = {
            IconButton(onClick = navController::navigateUp) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            }
          },
          actions = {
            IconButton(onClick = { imageIndex++ }) {
              Icon(Icons.Default.Refresh, "Refresh background button")
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
      },
      modifier = Modifier.fillMaxSize(),
    ) { contentPadding ->
      val hazeState = rememberHazeState()

      Box {
        AsyncImage(
          model = rememberRandomSampleImageUrl(imageIndex),
          contentScale = ContentScale.Crop,
          contentDescription = null,
          modifier = Modifier
            .hazeSource(state = hazeState)
            .fillMaxSize(),
        )

        LazyColumn(
          contentPadding = contentPadding,
          modifier = Modifier
            .testTag("lazy_column")
            .fillMaxSize(),
        ) {
          item {
            Spacer(Modifier.height(400.dp))
          }

          items(50) { index ->
            Box(
              modifier = Modifier
                .fillParentMaxWidth()
                .height(160.dp),
            ) {
              val style = HazeMaterials.thin()
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(horizontal = 24.dp)
                  .hazeEffect(state = hazeState) {
                    blurEffect {
                      this.blurEnabled = blurEnabled
                      this.style = style
                    }
                  },
              ) {
                Text(
                  "Item $index",
                  style = MaterialTheme.typography.titleLarge,
                  modifier = Modifier.align(Alignment.Center),
                )
              }
            }
          }
        }
      }
    }
  }
}
