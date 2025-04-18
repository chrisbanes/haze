// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun BottomSheet(navController: NavHostController, blurEnabled: Boolean = HazeDefaults.blurEnabled()) {
  var imageIndex by remember { mutableIntStateOf(0) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = "Bottom Sheet") },
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
    val hazeState = rememberHazeState(blurEnabled = blurEnabled)

    var showBottomSheet by remember { mutableStateOf(false) }

    Box(
      modifier = Modifier
        .hazeSource(state = hazeState),
    ) {
      AsyncImage(
        model = rememberRandomSampleImageUrl(imageIndex),
        contentScale = ContentScale.Crop,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
      )

      Button(
        onClick = { showBottomSheet = true },
        modifier = Modifier.align(Alignment.Center),
      ) {
        Text("Show bottom sheet")
      }
    }

    if (showBottomSheet) {
      ModalBottomSheet(
        containerColor = Color.Transparent,
        onDismissRequest = { showBottomSheet = false },
        // We need to disable the drag handle, otherwise it pushes down our hazeEffect content
        // Instead, we add it below
        dragHandle = null,
      ) {
        Column(
          modifier = Modifier
            .hazeEffect(state = hazeState, style = HazeMaterials.thin())
            .height(400.dp)
            .fillMaxWidth(),
        ) {
          // If you want the drag handle, you add it to the content
          BottomSheetDefaults.DragHandle(
            modifier = Modifier
              .align(Alignment.CenterHorizontally),
          )

          Text(
            text = "Bottom Sheet content",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
              .padding(64.dp)
              .align(Alignment.CenterHorizontally),
          )
        }
      }
    }
  }
}
