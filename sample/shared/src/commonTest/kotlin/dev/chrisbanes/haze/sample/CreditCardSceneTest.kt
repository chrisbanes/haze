// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class CreditCardSceneTest : ContextTest() {
  @Test
  fun creditCardScene_rendersThreeTaggedCards() = runComposeUiTest {
    setContent {
      CreditCardScene(onNavigateUp = {}) { hazeState, modifier, shape, zIndex ->
        Box(
          modifier = modifier
            .hazeSource(hazeState, zIndex = zIndex)
            .clip(shape),
        ) {
          DefaultCreditCardContents()
        }
      }
    }

    onNodeWithTag("credit_card_0").assertIsDisplayed()
    onNodeWithTag("credit_card_1").assertIsDisplayed()
    onNodeWithTag("credit_card_2").assertIsDisplayed()
  }
}
