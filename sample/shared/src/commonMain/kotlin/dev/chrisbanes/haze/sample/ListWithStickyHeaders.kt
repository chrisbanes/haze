// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalHazeMaterialsApi::class,
  ExperimentalHazeApi::class,
  ExperimentalFoundationApi::class,
)
@Composable
fun ListWithStickyHeaders(navigator: Navigator) {
  val hazeState = remember { HazeState() }
  val listState = rememberLazyListState()

  val style = HazeMaterials.regular(MaterialTheme.colorScheme.surface)

  Scaffold(
    topBar = {
      TopAppBar(
        title = { },
        navigationIcon = {
          IconButton(onClick = navigator::navigateUp, modifier = Modifier.testTag("back")) {
            Icon(Icons.AutoMirrored.Default.ArrowBack, null)
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )
    },
    modifier = Modifier.fillMaxSize(),
  ) { contentPadding ->
    LazyColumn(
      state = listState,
      modifier = Modifier
        .padding(contentPadding)
        .fillMaxSize()
        .testTag("lazy_list"),
    ) {
      val groupSize = 6
      repeat(5) { group ->
        stickyHeader {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .hazeChild(state = hazeState, style = style) {
                this.inputScale = HazeInputScale.Auto
              },
          ) {
            Text("Header: $group", modifier = Modifier.padding(16.dp))
          }
        }

        items(groupSize) { index ->
          Box(
            modifier = Modifier
              .haze(hazeState)
              .fillParentMaxWidth(),
          ) {
            AsyncImage(
              model = rememberRandomSampleImageUrl((group * groupSize) + index),
              contentScale = ContentScale.Crop,
              contentDescription = null,
              modifier = Modifier
                .height(128.dp)
                .fillMaxWidth(),
            )
          }
        }
      }
    }
  }
}
