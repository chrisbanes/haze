// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale

@OptIn(ExperimentalHazeApi::class)
val Samples = listOf(
  Sample("Scaffold") { ScaffoldSample(it) },
  Sample("Scaffold (input scaled)") { ScaffoldSample(it, inputScale = HazeInputScale.Auto) },
  Sample("Scaffold (progressive blur)") { ScaffoldSample(it, ScaffoldSampleMode.Progressive) },
  Sample("Scaffold (progressive blur, input scaled)") { ScaffoldSample(it, ScaffoldSampleMode.Progressive, HazeInputScale.Auto) },
  Sample("Scaffold (masked)") { ScaffoldSample(it, ScaffoldSampleMode.Mask) },
  Sample("Scaffold (masked, input scaled)") { ScaffoldSample(it, ScaffoldSampleMode.Mask, HazeInputScale.Auto) },
  Sample("Credit Card") { CreditCardSample(it) },
  Sample("Images List") { ImagesList(it) },
  Sample("List over Image") { ListOverImage(it) },
  Sample("Dialog") { DialogSample(it) },
  Sample("Materials") { MaterialsSample(it) },
  Sample("List with Sticky Headers") { ListWithStickyHeaders(it) },
)

data class Sample(
  val title: String,
  val content: @Composable (Navigator) -> Unit,
)

fun interface Navigator {
  fun navigateUp()
}

@Composable
fun SamplesTheme(
  useDarkColors: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (useDarkColors) darkColorScheme() else lightColorScheme(),
    content = content,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Samples(
  appTitle: String,
  samples: List<Sample> = Samples,
) {
  setSingletonImageLoaderFactory { context ->
    ImageLoader.Builder(context)
      .memoryCache {
        MemoryCache.Builder()
          .maxSizePercent(context, 0.2)
          .build()
      }
      .diskCache {
        context.cacheDirPath()?.let { cacheDirPath ->
          DiskCache.Builder()
            .directory(cacheDirPath.resolve("image_cache"))
            .maximumMaxSizeBytes(32 * 1024 * 1024)
            .build()
        }
      }
      .build()
  }

  val coilPlatformContext = LocalPlatformContext.current
  LaunchedEffect(coilPlatformContext) {
    // Preload the first 20 precanned image urls
    val imageLoader = SingletonImageLoader.get(coilPlatformContext)
    precannedImageUrls
      .asSequence()
      .map { ImageRequest.Builder(coilPlatformContext).data(it).build() }
      .forEach { imageLoader.enqueue(it) }
  }

  SamplesTheme {
    var currentSample by remember { mutableStateOf<Sample?>(null) }

    val navigator = remember {
      Navigator { currentSample = null }
    }

    Crossfade(
      targetState = currentSample,
      modifier = Modifier.testTagsAsResourceId(true),
    ) { sample ->
      if (sample != null) {
        sample.content(navigator)
      } else {
        Scaffold(
          topBar = {
            TopAppBar(
              title = { Text(text = currentSample?.title ?: appTitle) },
              navigationIcon = {
                if (currentSample != null) {
                  IconButton(onClick = { currentSample = null }) {
                    @Suppress("DEPRECATION")
                    Icon(
                      imageVector = Icons.Default.ArrowBack,
                      contentDescription = "Navigate back",
                    )
                  }
                }
              },
              modifier = Modifier.fillMaxWidth(),
            )
          },
        ) { contentPadding ->
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
          ) {
            items(samples) { sample ->
              ListItem(
                headlineContent = { Text(text = sample.title) },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag(sample.title)
                  .clickable { currentSample = sample },
              )
            }
          }
        }
      }
    }
  }
}
