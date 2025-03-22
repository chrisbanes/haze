// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.ComposeNavigatorDestinationBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.get
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlinx.serialization.Serializable

expect val Samples: List<Sample>

@OptIn(ExperimentalHazeApi::class)
val CommonSamples = listOf(
  Sample(SampleRoute.Scaffold, "Scaffold") { ScaffoldSample(it) },
  Sample(SampleRoute.ScaffoldScaled, "Scaffold (input scaled)") {
    ScaffoldSample(
      it,
      inputScale = HazeInputScale.Auto,
    )
  },
  Sample(SampleRoute.ScaffoldProgressive, "Scaffold (progressive blur)") {
    ScaffoldSample(
      it,
      ScaffoldSampleMode.Progressive,
    )
  },
  Sample(
    SampleRoute.ScaffoldProgressiveScaled,
    "Scaffold (progressive blur, input scaled)",
  ) { ScaffoldSample(it, ScaffoldSampleMode.Progressive, HazeInputScale.Auto) },
  Sample(SampleRoute.ScaffoldMasked, "Scaffold (masked)") {
    ScaffoldSample(
      it,
      ScaffoldSampleMode.Mask,
    )
  },
  Sample(SampleRoute.ScaffoldMaskedScaled, "Scaffold (masked, input scaled)") {
    ScaffoldSample(
      navController = it,
      mode = ScaffoldSampleMode.Mask,
      inputScale = HazeInputScale.Auto,
    )
  },
  Sample(SampleRoute.CreditCard, "Credit Card") { CreditCardSample(it) },
  Sample(SampleRoute.ImageList, "Images List") { ImagesList(it) },
  Sample(SampleRoute.ListOverImage, "List over Image") { ListOverImage(it) },
  Sample(SampleRoute.Dialog, "Dialog") { DialogSample(it) },
  Sample(SampleRoute.Materials, "Materials") { MaterialsSample(it) },
  Sample(
    key = SampleRoute.ListWithStickyHeaders,
    title = "List with Sticky Headers",
  ) { ListWithStickyHeaders(it) },
  Sample(SampleRoute.BottomSheet, "Bottom Sheet") { BottomSheet(it) },
)

data class Sample(
  val key: SampleRoute,
  val title: String,
  val content: @Composable (NavHostController) -> Unit,
)

sealed interface SampleRoute {
  @Serializable
  data object SamplesList : SampleRoute

  @Serializable
  data object Scaffold : SampleRoute

  @Serializable
  data object ScaffoldScaled : SampleRoute

  @Serializable
  data object ScaffoldProgressive : SampleRoute

  @Serializable
  data object ScaffoldProgressiveScaled : SampleRoute

  @Serializable
  data object ScaffoldMasked : SampleRoute

  @Serializable
  data object ScaffoldMaskedScaled : SampleRoute

  @Serializable
  data object CreditCard : SampleRoute

  @Serializable
  data object ImageList : SampleRoute

  @Serializable
  data object ListOverImage : SampleRoute

  @Serializable
  data object Dialog : SampleRoute

  @Serializable
  data object Materials : SampleRoute

  @Serializable
  data object ListWithStickyHeaders : SampleRoute

  @Serializable
  data object BottomSheet : SampleRoute

  @Serializable
  data object AndroidExoPlayer : SampleRoute
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

@Composable
fun Samples(
  appTitle: String,
  navController: NavHostController = rememberNavController(),
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
    NavHost(
      navController = navController,
      startDestination = SampleRoute.SamplesList,
      modifier = Modifier.testTagsAsResourceId(true),
    ) {
      composable<SampleRoute.SamplesList> {
        val sortedSamples = remember { samples.sortedBy(Sample::title) }
        SamplesList(appTitle, sortedSamples, navController)
      }

      samples.forEach { sample ->
        composable(routeClazz = sample.key::class) {
          sample.content(navController)
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SamplesList(
  appTitle: String,
  samples: List<Sample>,
  navController: NavHostController,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = appTitle) },
        modifier = Modifier.fillMaxWidth(),
      )
    },
    modifier = modifier,
  ) { contentPadding ->
    LazyColumn(
      modifier = Modifier
        .testTag("sample_list")
        .fillMaxSize(),
      contentPadding = contentPadding,
    ) {
      items(samples) { sample ->
        ListItem(
          headlineContent = { Text(text = sample.title) },
          modifier = Modifier
            .fillMaxWidth()
            .testTag(sample.title)
            .clickable { navController.navigate(sample.key) },
        )
      }
    }
  }
}

internal fun NavGraphBuilder.composable(
  routeClazz: KClass<*>,
  typeMap: Map<KType, NavType<*>> = emptyMap(),
  content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
  destination(
    ComposeNavigatorDestinationBuilder(
      provider[ComposeNavigator::class],
      routeClazz,
      typeMap,
      content,
    ),
  )
}
