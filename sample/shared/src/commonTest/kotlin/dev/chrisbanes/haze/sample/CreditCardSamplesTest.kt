// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class CreditCardSamplesTest : ContextTest() {
  @Test
  fun creditCardSample_keepsBenchmarkCardTag() = runComposeUiTest {
    setContent {
      val navController = rememberNavController()
      CreditCardSample(navController = navController, blurEnabled = false)
    }

    onNodeWithTag("credit_card_2").assertIsDisplayed()
  }

  @Test
  fun liquidGlassSample_keepsBenchmarkCardTag() = runComposeUiTest {
    setContent {
      val navController = rememberNavController()
      LiquidGlassCreditCardSample(navController = navController)
    }

    onNodeWithTag("credit_card_2").assertIsDisplayed()
  }
}
