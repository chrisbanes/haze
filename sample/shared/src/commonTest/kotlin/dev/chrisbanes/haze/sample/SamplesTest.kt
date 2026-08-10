// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import assertk.assertThat
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
  fun commonSamples_containsOnlyThreeGlassGalleryDestinations() {
    assertThat(
      CommonSamples.map(Sample::title).filter { "Glass" in it },
    ).isEqualTo(
      listOf("Glass — Product", "Glass — Playground", "Glass — Lab"),
    )
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
  fun samplesList_replacesOldGlassEntriesWithShowcaseSuite() = runComposeUiTest {
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

    onNodeWithTag("Credit Card").assertIsDisplayed()
    onNodeWithTag("Glass — Product").assertIsDisplayed()
    onNodeWithTag("Glass — Playground").assertIsDisplayed()
    onNodeWithTag("Glass — Lab").assertIsDisplayed()
    onNodeWithTag("Glass").assertDoesNotExist()
    onNodeWithTag("Glass (Debug)").assertDoesNotExist()
  }

  @Test
  fun creditCard_exposesALocalBlurAndGlassChoice() = runComposeUiTest {
    setContent {
      SampleDestination(
        sample = Sample.CreditCard,
        navController = rememberNavController(),
      )
    }

    onNodeWithTag("sample_effect_picker").assertIsDisplayed()
    onNodeWithTag("sample_effect_blur").assertIsDisplayed().assertIsSelected()
    onNodeWithTag("sample_effect_glass").performClick().assertIsSelected()
    onNodeWithTag("sample_effect_blur").assertIsNotSelected()
    onNodeWithTag("credit_card_2").assertIsDisplayed()
    onNodeWithTag("blur_enabled").assertDoesNotExist()
  }

  @Test
  fun glassScaffold_keepsItsGridContent() = runComposeUiTest {
    setContent {
      SampleDestination(
        sample = Sample.Scaffold,
        navController = rememberNavController(),
      )
    }

    onNodeWithTag("sample_effect_glass").performClick()
    onNodeWithTag("lazy_grid").assertIsDisplayed()
  }

  @Test
  fun glassContentBlurring_omitsTheBlurOnlyClippedControl() = runComposeUiTest {
    setContent {
      SampleDestination(
        sample = Sample.ContentBlurring,
        navController = rememberNavController(),
      )
    }

    onNodeWithTag("sample_effect_glass").performClick()
    onNodeWithTag("content_blur_clipped").assertDoesNotExist()
  }
}
