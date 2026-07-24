// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SamplesTest : ContextTest() {
  @Test
  fun commonSamples_containsOnlyThreeGlassGalleryDestinations() {
    assertThat(
      CommonSamples.map(Sample::title).filter { "Glass" in it },
    ).isEqualTo(
      listOf("Glass — Product", "Glass — Playground", "Glass — Lab"),
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
}
