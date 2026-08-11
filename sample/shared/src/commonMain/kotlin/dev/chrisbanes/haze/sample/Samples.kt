// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle

expect val Samples: List<Sample>

private const val SAMPLES_ROUTE = "samples"

enum class SampleEffect(val label: String) {
  Blur("Blur"),
  Glass("Glass"),
}

private fun SampleEffect.route(): String = "$SAMPLES_ROUTE/${name.lowercase()}"

private fun Sample.route(effect: SampleEffect): String = "$route/${effect.name.lowercase()}"

internal fun List<Sample>.forEffect(effect: SampleEffect): List<Sample> = filter {
  effect in it.effects
}

@OptIn(ExperimentalHazeApi::class)
val CommonSamples: List<Sample> = listOf(
  Sample.Scaffold,
  Sample.ScaffoldAdaptive,
  Sample.ScaffoldQuality,
  Sample.ScaffoldBalanced,
  Sample.ScaffoldPerformance,
  Sample.ScaffoldProgressive,
  Sample.ScaffoldProgressiveQuality,
  Sample.ScaffoldMasked,
  Sample.ScaffoldMaskedQuality,
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
  val effects: List<SampleEffect> = listOf(SampleEffect.Blur),
  val content: @Composable (NavHostController, SampleEffect) -> Unit,
) {
  init {
    require(effects.isNotEmpty())
  }

  companion object {
    private val BuiltInEffects = listOf(SampleEffect.Blur, SampleEffect.Glass)

    val Scaffold = Sample(
      route = "scaffold",
      title = "Scaffold",
      effects = BuiltInEffects,
    ) { navController, effect ->
      ScaffoldSample(navController = navController, effect = effect)
    }

    val ScaffoldAdaptive = Sample(
      route = "scaffold-adaptive",
      title = "Scaffold (adaptive)",
      effects = BuiltInEffects,
    ) { navController, effect ->
      ScaffoldSample(
        navController = navController,
        effect = effect,
        performanceMode = HazePerformanceMode.Adaptive,
      )
    }

    val ScaffoldQuality = Sample(
      route = "scaffold-quality",
      title = "Scaffold (quality)",
      effects = BuiltInEffects,
    ) { navController, effect ->
      ScaffoldSample(
        navController = navController,
        effect = effect,
        performanceMode = HazePerformanceMode.Quality,
      )
    }

    val ScaffoldBalanced = Sample(
      route = "scaffold-balanced",
      title = "Scaffold (balanced)",
      effects = BuiltInEffects,
    ) { navController, effect ->
      ScaffoldSample(
        navController = navController,
        effect = effect,
        performanceMode = HazePerformanceMode.Balanced,
      )
    }

    val ScaffoldPerformance = Sample(
      route = "scaffold-performance",
      title = "Scaffold (performance)",
      effects = BuiltInEffects,
    ) { navController, effect ->
      ScaffoldSample(
        navController = navController,
        effect = effect,
        performanceMode = HazePerformanceMode.Performance,
      )
    }

    val ScaffoldProgressive = Sample(
      route = "scaffold-progressive",
      title = "Scaffold (progressive blur)",
      effects = BuiltInEffects,
    ) { navController, effect ->
      ScaffoldSample(
        navController = navController,
        effect = effect,
        mode = ScaffoldSampleMode.Progressive,
      )
    }

    val ScaffoldProgressiveQuality = Sample(
      route = "scaffold-progressive-quality",
      title = "Scaffold (progressive blur, quality)",
      effects = BuiltInEffects,
    ) { navController, effect ->
      ScaffoldSample(
        navController = navController,
        effect = effect,
        mode = ScaffoldSampleMode.Progressive,
        performanceMode = HazePerformanceMode.Quality,
      )
    }

    val ScaffoldMasked = Sample(
      route = "scaffold-masked",
      title = "Scaffold (masked)",
      effects = BuiltInEffects,
    ) { navController, effect ->
      ScaffoldSample(
        navController = navController,
        effect = effect,
        mode = ScaffoldSampleMode.Mask,
      )
    }

    val ScaffoldMaskedQuality = Sample(
      route = "scaffold-masked-quality",
      title = "Scaffold (masked, quality)",
      effects = BuiltInEffects,
    ) { navController, effect ->
      ScaffoldSample(
        navController = navController,
        effect = effect,
        mode = ScaffoldSampleMode.Mask,
        performanceMode = HazePerformanceMode.Quality,
      )
    }

    val CreditCard = Sample(
      route = "credit-card",
      title = "Credit Card",
      effects = BuiltInEffects,
    ) { navController, effect ->
      CreditCardSample(navController = navController, effect = effect)
    }

    val ImageList = Sample(
      route = "images-list",
      title = "Images List",
      effects = BuiltInEffects,
    ) { navController, effect ->
      ImagesList(navController = navController, effect = effect)
    }

    val ListOverImage = Sample(
      route = "list-over-image",
      title = "List over Image",
      effects = BuiltInEffects,
    ) { navController, effect ->
      ListOverImage(navController, effect)
    }

    val Dialog = Sample(
      route = "dialog",
      title = "Dialog",
      effects = BuiltInEffects,
    ) { navController, effect ->
      DialogSample(navController, effect)
    }

    val Popup = Sample(
      route = "popup",
      title = "Popup",
      effects = BuiltInEffects,
    ) { navController, effect ->
      PopupSample(navController, effect)
    }

    val Materials = Sample(
      route = "materials",
      title = "Materials",
      effects = BuiltInEffects,
    ) { navController, effect ->
      MaterialsSample(navController, effect)
    }

    val ListWithStickyHeaders = Sample(
      route = "list-with-sticky-headers",
      title = "List with Sticky Headers",
      effects = BuiltInEffects,
    ) { navController, effect ->
      ListWithStickyHeaders(navController, effect)
    }

    val BottomSheet = Sample(
      route = "bottom-sheet",
      title = "Bottom Sheet",
      effects = BuiltInEffects,
    ) { navController, effect ->
      BottomSheet(navController, effect)
    }

    val ContentBlurring = Sample(
      route = "content-blurring",
      title = "Content Blurring",
      effects = BuiltInEffects,
    ) { navController, effect ->
      ContentBlurring(navController, effect)
    }

    val CustomVisualEffect = Sample(
      route = "custom-visual-effect",
      title = "Custom VisualEffect",
    ) { navController, effect ->
      CustomVisualEffectSample(navController)
    }

    val LayerTransformations = Sample(
      route = "layer-transformations",
      title = "Layer Transformations",
      effects = BuiltInEffects,
    ) { navController, effect ->
      LayerTransformations(
        effect = effect,
        onBack = navController::navigateUp,
      )
    }

    val GlassProduct = Sample(
      route = "glass-product",
      title = "Glass — Product",
      effects = listOf(SampleEffect.Glass),
    ) { navController, _ ->
      GlassProductSample(navController = navController)
    }

    val GlassPlayground = Sample(
      route = "glass-playground",
      title = "Glass — Playground",
      effects = listOf(SampleEffect.Glass),
    ) { navController, _ ->
      GlassPlaygroundSample(navController = navController)
    }

    val GlassLab = Sample(
      route = "glass-lab",
      title = "Glass — Lab",
      effects = listOf(SampleEffect.Glass),
    ) { navController, _ ->
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
  forceBlur: Boolean = false,
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

  val localBlurStyle = remember(forceBlur) {
    if (forceBlur) {
      HazeBlurStyle.then { blurEnabled(true) }
    } else {
      HazeBlurStyle
    }
  }

  SamplesTheme {
    CompositionLocalProvider(LocalHazeBlurStyle provides localBlurStyle) {
      NavHost(
        navController = navController,
        startDestination = SAMPLES_ROUTE,
        modifier = Modifier.testTagsAsResourceId(true),
      ) {
        composable(SAMPLES_ROUTE) {
          EffectList(
            appTitle = appTitle,
            effects = SampleEffect.entries.toList(),
            onEffectSelected = { effect -> navController.navigate(effect.route()) },
          )
        }

        SampleEffect.entries.forEach { effect ->
          composable(effect.route()) {
            val effectSamples = remember(samples, effect) {
              samples.forEffect(effect).sortedBy(Sample::title)
            }
            SamplesList(
              appTitle = "$appTitle — ${effect.label}",
              samples = effectSamples,
              onNavigateUp = navController::navigateUp,
              onSampleSelected = { selected ->
                navController.navigate(selected.route(effect))
              },
            )
          }
        }

        samples.forEach { sample ->
          sample.effects.forEach { effect ->
            composable(sample.route(effect)) {
              sample.content(navController, effect)
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EffectList(
  appTitle: String,
  effects: List<SampleEffect>,
  onEffectSelected: (SampleEffect) -> Unit,
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
        .testTag("sample_effect_list")
        .fillMaxSize(),
      contentPadding = contentPadding,
    ) {
      items(effects) { effect ->
        ListItem(
          headlineContent = { Text(text = effect.label) },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("sample_effect_${effect.name.lowercase()}")
            .clickable { onEffectSelected(effect) },
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SamplesList(
  appTitle: String,
  samples: List<Sample>,
  onNavigateUp: () -> Unit,
  onSampleSelected: (Sample) -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = appTitle) },
        navigationIcon = {
          IconButton(
            onClick = onNavigateUp,
            modifier = Modifier.testTag("sample_list_back"),
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
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
            .clickable { onSampleSelected(sample) },
        )
      }
    }
  }
}
