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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalHazeApi::class,
  ExperimentalFoundationApi::class,
)
@Composable
fun ListWithStickyHeaders(navController: NavHostController, effect: SampleEffect) {
  val hazeState = rememberHazeState()
  val listState = rememberLazyListState()

  val blurStyle = HazeMaterials.regular(MaterialTheme.colorScheme.surface)
  val glassBackgroundColor = MaterialTheme.colorScheme.surface
  val glassTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)
  val glassShape = RoundedCornerShape(0.dp)

  Scaffold(
    topBar = {
      TopAppBar(
        title = { },
        navigationIcon = {
          IconButton(onClick = navController::navigateUp, modifier = Modifier.testTag("back")) {
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
              .then(
                when (effect) {
                  SampleEffect.Blur -> Modifier.hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = blurStyle,
                    performanceMode = HazePerformanceMode.Adaptive,
                  )

                  SampleEffect.Glass -> Modifier.hazeGlass(
                    input = HazeInput.Sources(hazeState),
                    style = GlassStyle {
                      backgroundColor(glassBackgroundColor)
                      tint(glassTint)
                      shape(glassShape)
                      optics(GlassOptics.Adaptive)
                    },
                    performanceMode = HazePerformanceMode.Adaptive,
                  )
                },
              ),
          ) {
            Text("Header: $group", modifier = Modifier.padding(16.dp))
          }
        }

        items(groupSize) { index ->
          Box(
            modifier = Modifier
              .hazeSource(hazeState)
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
