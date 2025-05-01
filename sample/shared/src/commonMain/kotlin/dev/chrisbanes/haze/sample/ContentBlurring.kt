// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun ContentBlurring(
  navController: NavHostController,
  blurEnabled: Boolean = HazeDefaults.blurEnabled(),
) {
  var imageIndex by remember { mutableIntStateOf(0) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Content Blurring") },
        navigationIcon = {
          IconButton(
            onClick = navController::navigateUp,
            modifier = Modifier.testTag("back"),
          ) {
            Icon(Icons.AutoMirrored.Default.ArrowBack, null)
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
  ) {
    var clipEnabled by remember { mutableStateOf(true) }
    var drawContentBehind by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
      val context = LocalPlatformContext.current
      val url = rememberRandomSampleImageUrl(imageIndex)
      AsyncImage(
        model = remember(context, url) {
          ImageRequest.Builder(context)
            .data(url)
            .size(coil3.size.Size(128, 128))
            .build()
        },
        contentScale = ContentScale.Crop,
        contentDescription = null,
        modifier = Modifier
          .hazeEffect(HazeMaterials.ultraThin()) {
            backgroundColor = Color.Transparent
            this.blurEnabled = blurEnabled
            this.blurredEdgeTreatment = when {
              clipEnabled -> BlurredEdgeTreatment.Rectangle
              else -> BlurredEdgeTreatment.Unbounded
            }
            this.drawContentBehind = drawContentBehind
            this.blurRadius = 100.dp
          }
          .align(Alignment.Center)
          .size(300.dp),
      )

      Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
          .padding(bottom = 16.dp)
          .windowInsetsPadding(WindowInsets.navigationBars)
          .align(Alignment.BottomCenter),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Draw content behind:", modifier = Modifier.padding(end = 8.dp))
          Switch(checked = drawContentBehind, onCheckedChange = { drawContentBehind = it })
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Clipped:", modifier = Modifier.padding(end = 8.dp))
          Switch(checked = clipEnabled, onCheckedChange = { clipEnabled = it })
        }
      }
    }
  }
}
