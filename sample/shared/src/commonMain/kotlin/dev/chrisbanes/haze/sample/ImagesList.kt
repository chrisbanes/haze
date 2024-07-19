// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun ImagesList(navigator: Navigator) {
  MaterialTheme {
    Scaffold(
      topBar = {
        LargeTopAppBar(
          title = { Text(text = "Images") },
          navigationIcon = {
            IconButton(onClick = navigator::navigateUp) {
              @Suppress("DEPRECATION")
              Icon(Icons.Default.ArrowBack, null)
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
      },
      modifier = Modifier.fillMaxSize(),
    ) { contentPadding ->
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding,
        modifier = Modifier
          .testTag("lazy_column")
          .fillMaxSize(),
      ) {
        items(50) { index ->
          val hazeState = remember { HazeState() }

          Box(
            modifier = Modifier
              .fillParentMaxWidth()
              .height(160.dp),
          ) {
            AsyncImage(
              model = rememberRandomSampleImageUrl(width = 800),
              contentScale = ContentScale.Crop,
              contentDescription = null,
              modifier = Modifier
                .haze(state = hazeState, style = HazeMaterials.thin())
                .fillMaxSize(),
            )

            Box(
              modifier = Modifier
                .fillMaxSize(0.8f)
                .align(Alignment.Center)
                .hazeChild(
                  state = hazeState,
                  shape = RoundedCornerShape(4.dp),
                ),
            ) {
              Text(
                "Image $index",
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
