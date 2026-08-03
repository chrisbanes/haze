// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.blur.HazeBlurDefaults

expect val Samples: List<Sample>

private const val SAMPLES_ROUTE = "samples"

@OptIn(ExperimentalHazeApi::class)
val CommonSamples: List<Sample> = listOf(
  Sample.Scaffold,
  Sample.ScaffoldUnscaled,
  Sample.ScaffoldBalanced,
  Sample.ScaffoldProgressive,
  Sample.ScaffoldProgressiveUnscaled,
  Sample.ScaffoldMasked,
  Sample.ScaffoldMaskedUnscaled,
  Sample.CreditCard,
  Sample.ImageList,
  Sample.ListOverImage,
  Sample.Dialog,
  Sample.Popup,
  Sample.Materials,
  Sample.ListWithStickyHeaders,
  Sample.BottomSheet,
  Sample.ContentBlurring,
  Sample.CustomVisualEffect,
  Sample.LayerTransformations,
  Sample.GlassProduct,
  Sample.GlassPlayground,
  Sample.GlassLab,
)

@OptIn(ExperimentalHazeApi::class)
class Sample(
  val route: String,
  val title: String,
  val content: @Composable (NavHostController, Boolean) -> Unit,
) {
  companion object {
    val Scaffold = Sample("scaffold", "Scaffold") { navController, blurEnabled ->
      ScaffoldSample(navController = navController, blurEnabled = blurEnabled)
    }

    val ScaffoldUnscaled = Sample(
      route = "scaffold-unscaled",
      title = "Scaffold (input unscaled)",
    ) { navController, blurEnabled ->
      ScaffoldSample(
        navController = navController,
        blurEnabled = blurEnabled,
        sampling = HazeSampling.FullResolution,
      )
    }

    val ScaffoldBalanced = Sample(
      route = "scaffold-balanced",
      title = "Scaffold (input fixed 0.8)",
    ) { navController, blurEnabled ->
      ScaffoldSample(
        navController = navController,
        blurEnabled = blurEnabled,
        sampling = HazeSampling.Fixed(0.64f),
      )
    }

    val ScaffoldProgressive = Sample(
      route = "scaffold-progressive",
      title = "Scaffold (progressive blur)",
    ) { navController, blurEnabled ->
      ScaffoldSample(
        navController = navController,
        blurEnabled = blurEnabled,
        mode = ScaffoldSampleMode.Progressive,
      )
    }

    val ScaffoldProgressiveUnscaled = Sample(
      route = "scaffold-progressive-unscaled",
      title = "Scaffold (progressive blur, input unscaled)",
    ) { navController, blurEnabled ->
      ScaffoldSample(
        navController = navController,
        blurEnabled = blurEnabled,
        mode = ScaffoldSampleMode.Progressive,
        sampling = HazeSampling.FullResolution,
      )
    }

    val ScaffoldMasked = Sample(
      route = "scaffold-masked",
      title = "Scaffold (masked)",
    ) { navController, blurEnabled ->
      ScaffoldSample(
        navController = navController,
        blurEnabled = blurEnabled,
        mode = ScaffoldSampleMode.Mask,
      )
    }

    val ScaffoldMaskedUnscaled = Sample(
      route = "scaffold-masked-unscaled",
      title = "Scaffold (masked, input unscaled)",
    ) { navController, blurEnabled ->
      ScaffoldSample(
        navController = navController,
        blurEnabled = blurEnabled,
        mode = ScaffoldSampleMode.Mask,
        sampling = HazeSampling.FullResolution,
      )
    }

    val CreditCard = Sample("credit-card", "Credit Card") { navController, blurEnabled ->
      CreditCardSample(navController = navController, blurEnabled = blurEnabled)
    }

    val ImageList = Sample("images-list", "Images List") { navController, blurEnabled ->
      ImagesList(navController = navController, blurEnabled = blurEnabled)
    }

    val ListOverImage = Sample("list-over-image", "List over Image") { navController, blurEnabled ->
      ListOverImage(navController, blurEnabled)
    }

    val Dialog = Sample("dialog", "Dialog") { navController, blurEnabled ->
      DialogSample(navController, blurEnabled)
    }

    val Popup = Sample("popup", "Popup") { navController, blurEnabled ->
      PopupSample(navController, blurEnabled)
    }

    val Materials = Sample("materials", "Materials") { navController, blurEnabled ->
      MaterialsSample(navController, blurEnabled)
    }

    val ListWithStickyHeaders = Sample(
      route = "list-with-sticky-headers",
      title = "List with Sticky Headers",
    ) { navController, blurEnabled ->
      ListWithStickyHeaders(navController, blurEnabled)
    }

    val BottomSheet = Sample("bottom-sheet", "Bottom Sheet") { navController, blurEnabled ->
      BottomSheet(navController, blurEnabled)
    }

    val ContentBlurring = Sample(
      route = "content-blurring",
      title = "Content Blurring",
    ) { navController, blurEnabled ->
      ContentBlurring(navController, blurEnabled)
    }

    val CustomVisualEffect = Sample(
      route = "custom-visual-effect",
      title = "Custom VisualEffect",
    ) { navController, blurEnabled ->
      CustomVisualEffectSample(navController, blurEnabled)
    }

    val LayerTransformations = Sample(
      route = "layer-transformations",
      title = "Layer Transformations",
    ) { _, blurEnabled ->
      LayerTransformations(blurEnabled = blurEnabled)
    }

    val GlassProduct = Sample("glass-product", "Glass — Product") { navController, _ ->
      GlassProductSample(navController = navController)
    }

    val GlassPlayground = Sample("glass-playground", "Glass — Playground") { navController, _ ->
      GlassPlaygroundSample(navController = navController)
    }

    val GlassLab = Sample("glass-lab", "Glass — Lab") { navController, _ ->
      GlassLabSample(navController = navController)
    }
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
  val coilPlatformContext = LocalPlatformContext.current
  LaunchedEffect(coilPlatformContext) {
    // Preload the first 20 precanned image urls
    val imageLoader = SingletonImageLoader.get(coilPlatformContext)
    precannedImageUrls
      .asSequence()
      .map { ImageRequest.Builder(coilPlatformContext).data(it).build() }
      .forEach { imageLoader.enqueue(it) }
  }

  var blurEnabled by rememberSaveable { mutableStateOf(HazeBlurDefaults.blurEnabled()) }

  SamplesTheme {
    NavHost(
      navController = navController,
      startDestination = SAMPLES_ROUTE,
      modifier = Modifier.testTagsAsResourceId(true),
    ) {
      composable(SAMPLES_ROUTE) {
        val sortedSamples = remember { samples.sortedBy(Sample::title) }
        SamplesList(
          appTitle = appTitle,
          samples = sortedSamples,
          navController = navController,
          forceBlurEnabled = blurEnabled,
          onForceBlurChanged = { blurEnabled = it },
        )
      }

      samples.forEach { sample ->
        composable(sample.route) {
          sample.content(navController, blurEnabled)
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
  forceBlurEnabled: Boolean = false,
  onForceBlurChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = appTitle) },
        actions = {
          Text("Blur enabled:")
          Checkbox(
            checked = forceBlurEnabled,
            onCheckedChange = onForceBlurChanged,
            modifier = Modifier
              .testTag("blur_enabled"),
          )
        },
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
            .clickable { navController.navigate(sample.route) },
        )
      }
    }
  }
}
