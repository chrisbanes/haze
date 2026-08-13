// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SamplesAndroidTest : ContextTest() {
  @Test
  fun exoPlayer_exposesBothBuiltInEffects() {
    assertThat(AndroidExoPlayer.effects).isEqualTo(
      listOf(SampleEffect.Blur, SampleEffect.Glass),
    )
  }

  @Test
  fun samples_switchingSuitesOrReselectingSuiteDisplaysTheTargetCatalog() = runComposeUiTest {
    val blurDemo = Sample(
      route = "blur-demo",
      title = "Blur demo",
      effects = listOf(SampleEffect.Blur),
    ) { _, _ ->
      Text("Blur detail")
    }
    val glassDemo = Sample(
      route = "glass-demo",
      title = "Glass demo",
      effects = listOf(SampleEffect.Glass),
    ) { _, _ ->
      Text("Glass detail")
    }

    setContent {
      Samples(
        appTitle = "Haze Samples",
        samples = listOf(blurDemo, glassDemo),
      )
    }

    onNodeWithTag("Blur demo").performClick()
    onNodeWithText("Blur detail").assertIsDisplayed()

    onNodeWithTag("sample_effect_glass").performClick()
    onNodeWithTag("Glass demo").assertIsDisplayed()
    onNodeWithText("Blur detail").assertDoesNotExist()

    onNodeWithTag("Glass demo").performClick()
    onNodeWithText("Glass detail").assertIsDisplayed()

    onNodeWithTag("sample_effect_glass").performClick()
    onNodeWithTag("Glass demo").assertIsDisplayed()
    onNodeWithText("Glass detail").assertDoesNotExist()
  }
}
