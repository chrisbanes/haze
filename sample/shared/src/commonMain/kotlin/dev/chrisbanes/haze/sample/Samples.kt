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
import kotlinx.serialization.Transient

expect val Samples: List<Sample>

@OptIn(ExperimentalHazeApi::class)
val CommonSamples: List<Sample> = listOf(
  Sample.Scaffold,
  Sample.ScaffoldScaled,
  Sample.ScaffoldProgressive,
  Sample.ScaffoldProgressiveScaled,
  Sample.ScaffoldMasked,
  Sample.ScaffoldMaskedScaled,
  Sample.CreditCard,
  Sample.ImageList,
  Sample.ListOverImage,
  Sample.Dialog,
  Sample.Materials,
  Sample.ListWithStickyHeaders,
  Sample.BottomSheet,
)

@OptIn(ExperimentalHazeApi::class)
interface Sample { // We should seal this interface, but KMP doesn't support it yet.
  val title: String

  @Transient
  val content: @Composable (NavHostController) -> Unit

  @Serializable
  data object SamplesList : Sample {
    override val title: String = "Samples"

    override val content: @Composable (NavHostController) -> Unit
      get() = { _ -> error("SamplesList should never be called") }
  }

  @Serializable
  data object Scaffold : Sample {
    override val title: String = "Scaffold"

    override val content: @Composable (NavHostController) -> Unit = {
      ScaffoldSample(it)
    }
  }

  @Serializable
  data object ScaffoldScaled : Sample {
    override val title: String = "Scaffold (input scaled)"

    override val content: @Composable (NavHostController) -> Unit = {
      ScaffoldSample(
        it,
        inputScale = HazeInputScale.Auto,
      )
    }
  }

  @Serializable
  data object ScaffoldProgressive : Sample {
    override val title: String = "Scaffold (progressive blur)"

    override val content: @Composable (NavHostController) -> Unit = {
      ScaffoldSample(
        it,
        mode = ScaffoldSampleMode.Progressive,
      )
    }
  }

  @Serializable
  data object ScaffoldProgressiveScaled : Sample {
    override val title: String = "Scaffold (progressive blur, input scaled)"

    override val content: @Composable (NavHostController) -> Unit = {
      ScaffoldSample(
        it,
        mode = ScaffoldSampleMode.Progressive,
        inputScale = HazeInputScale.Auto,
      )
    }
  }

  @Serializable
  data object ScaffoldMasked : Sample {
    override val title: String = "Scaffold (masked)"

    override val content: @Composable (NavHostController) -> Unit = {
      ScaffoldSample(
        it,
        mode = ScaffoldSampleMode.Mask,
      )
    }
  }

  @Serializable
  data object ScaffoldMaskedScaled : Sample {
    override val title: String = "Scaffold (masked, input scaled)"

    override val content: @Composable (NavHostController) -> Unit = {
      ScaffoldSample(
        it,
        mode = ScaffoldSampleMode.Mask,
        inputScale = HazeInputScale.Auto,
      )
    }
  }

  @Serializable
  data object CreditCard : Sample {
    override val title: String = "Credit Card"

    override val content: @Composable (NavHostController) -> Unit = { CreditCardSample(it) }
  }

  @Serializable
  data object ImageList : Sample {
    override val title: String = "Images List"

    override val content: @Composable (NavHostController) -> Unit = { ImagesList(it) }
  }

  @Serializable
  data object ListOverImage : Sample {
    override val title: String = "List over Image"

    override val content: @Composable (NavHostController) -> Unit = { ListOverImage(it) }
  }

  @Serializable
  data object Dialog : Sample {
    override val title: String = "Dialog"

    override val content: @Composable (NavHostController) -> Unit = { DialogSample(it) }
  }

  @Serializable
  data object Materials : Sample {
    override val title: String = "Materials"

    override val content: @Composable (NavHostController) -> Unit = { MaterialsSample(it) }
  }

  @Serializable
  data object ListWithStickyHeaders : Sample {
    override val title: String = "List with Sticky Headers"

    override val content: @Composable (NavHostController) -> Unit = { ListWithStickyHeaders(it) }
  }

  @Serializable
  data object BottomSheet : Sample {
    override val title: String = "Bottom Sheet"

    override val content: @Composable (NavHostController) -> Unit = { BottomSheet(it) }
  }
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
      startDestination = Sample.SamplesList,
      modifier = Modifier.testTagsAsResourceId(true),
    ) {
      composable<Sample.SamplesList> {
        val sortedSamples = remember { samples.sortedBy(Sample::title) }
        SamplesList(appTitle, sortedSamples, navController)
      }

      samples.forEach { sample ->
        composable(routeClazz = sample::class) {
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
            .clickable { navController.navigate(sample) },
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
