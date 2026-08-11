// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SamplesTest : ContextTest() {
  @Test
  fun commonSamples_routesAndTitlesAreUnique() {
    assertThat(CommonSamples.map(Sample::route).distinct().size)
      .isEqualTo(CommonSamples.size)
    assertThat(CommonSamples.map(Sample::title).distinct().size)
      .isEqualTo(CommonSamples.size)
  }

  @Test
  fun commonSamples_hasDedicatedGlassGalleryDestinations() {
    assertThat(
      CommonSamples.map(Sample::title).filter { "Glass" in it },
    ).isEqualTo(
      listOf("Glass — Product", "Glass — Playground", "Glass — Lab"),
    )
  }

  @Test
  fun commonSamples_catalogsIncludeCustomVisualEffectAndGlassShowcases() {
    assertThat(CommonSamples.forEffect(SampleEffect.Blur).map(Sample::title))
      .contains("Custom VisualEffect")
    assertThat(CommonSamples.forEffect(SampleEffect.Glass).size).isEqualTo(22)
    val glassTitles = CommonSamples.forEffect(SampleEffect.Glass).map(Sample::title)
    assertThat(glassTitles).contains("Glass — Product")
    assertThat(glassTitles).contains("Glass — Playground")
    assertThat(glassTitles).contains("Glass — Lab")
  }

  @Test
  fun scaffoldProfiles_haveExplicitNamedPerformanceModes() {
    assertThat(
      listOf(
        Sample.ScaffoldAdaptive,
        Sample.ScaffoldQuality,
        Sample.ScaffoldBalanced,
        Sample.ScaffoldPerformance,
      ).map(Sample::title),
    ).isEqualTo(
      listOf(
        "Scaffold (adaptive)",
        "Scaffold (quality)",
        "Scaffold (balanced)",
        "Scaffold (performance)",
      ),
    )
  }

  @Test
  fun commonBuiltInSamples_exposeBothBuiltInEffects() {
    val comparisonSamples = listOf(
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
      Sample.LayerTransformations,
    )

    assertThat(comparisonSamples.map(Sample::effects)).isEqualTo(
      List(comparisonSamples.size) { listOf(SampleEffect.Blur, SampleEffect.Glass) },
    )
  }

  @Test
  fun samples_startsWithEffectCategories() = runComposeUiTest {
    setContent {
      Samples(
        appTitle = "Haze Samples",
        samples = listOf(
          Sample.CreditCard,
          Sample.GlassProduct,
          Sample.GlassPlayground,
          Sample.GlassLab,
        ),
      )
    }

    onNodeWithTag("sample_effect_blur").assertIsDisplayed()
    onNodeWithTag("sample_effect_glass").assertIsDisplayed()
    onNodeWithTag("Credit Card").assertDoesNotExist()
  }

  @Test
  fun effectList_selectsGlassCategory() = runComposeUiTest {
    var selectedEffect: SampleEffect? = null
    setContent {
      EffectList(
        appTitle = "Haze Samples",
        effects = SampleEffect.entries.toList(),
        onEffectSelected = { selectedEffect = it },
      )
    }

    onNodeWithTag("sample_effect_glass").performClick()
    runOnIdle {
      assertThat(selectedEffect).isEqualTo(SampleEffect.Glass)
    }
  }

  @Test
  fun samplesForEffect_keepsSharedAndEffectSpecificSamplesSeparate() {
    val sharedSample = Sample(
      route = "shared",
      title = "Shared sample",
      effects = listOf(SampleEffect.Blur, SampleEffect.Glass),
    ) { _, _ -> }
    val blurOnlySample = Sample(
      route = "blur-only",
      title = "Blur-only sample",
    ) { _, _ -> }
    val glassOnlySample = Sample(
      route = "glass-only",
      title = "Glass-only sample",
      effects = listOf(SampleEffect.Glass),
    ) { _, _ -> }

    val samples = listOf(sharedSample, blurOnlySample, glassOnlySample)

    assertThat(samples.forEffect(SampleEffect.Blur).map(Sample::title))
      .isEqualTo(listOf("Shared sample", "Blur-only sample"))
    assertThat(samples.forEffect(SampleEffect.Glass).map(Sample::title))
      .isEqualTo(listOf("Shared sample", "Glass-only sample"))
  }

  @Test
  fun sampleContent_usesItsEffectRoute() = runComposeUiTest {
    val sample = Sample(
      route = "effect-picker",
      title = "Effect picker",
      effects = listOf(SampleEffect.Blur, SampleEffect.Glass),
    ) { _, effect ->
      Text(
        text = "Selected ${effect.label}",
        modifier = Modifier.testTag("selected_effect_${effect.name.lowercase()}"),
      )
    }

    setContent {
      sample.content(rememberNavController(), SampleEffect.Glass)
    }

    onNodeWithTag("selected_effect_glass").assertIsDisplayed()
    onNodeWithTag("selected_effect_blur").assertDoesNotExist()
  }

  @Test
  fun glassScaffold_keepsItsGridContent() = runComposeUiTest {
    setContent {
      Sample.Scaffold.content(rememberNavController(), SampleEffect.Glass)
    }

    onNodeWithTag("lazy_grid").assertIsDisplayed()
  }

  @Test
  fun glassContentBlurring_omitsTheBlurOnlyClippedControl() = runComposeUiTest {
    setContent {
      Sample.ContentBlurring.content(rememberNavController(), SampleEffect.Glass)
    }

    onNodeWithTag("content_blur_clipped").assertDoesNotExist()
  }
}
