// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SamplesAndroidTest : ContextTest() {
  @Test
  fun cameraSamples_areRegisteredAndExposeBothBuiltInEffects() {
    assertThat(Samples).contains(AndroidCameraX)
    assertThat(Samples).contains(Kamera)
    assertThat(AndroidCameraX.effects).isEqualTo(
      listOf(SampleEffect.Blur, SampleEffect.Glass),
    )
    assertThat(Kamera.effects).isEqualTo(
      listOf(SampleEffect.Blur, SampleEffect.Glass),
    )
  }

  @Test
  fun exoPlayer_exposesBothBuiltInEffects() {
    assertThat(AndroidExoPlayer.effects).isEqualTo(
      listOf(SampleEffect.Blur, SampleEffect.Glass),
    )
  }

  @Test
  fun samples_nestedEffectListsDisplayTheTargetDetail() = runComposeUiTest {
    val blurDemo = Sample(
      route = "blur-demo",
      title = "Blur demo",
      effects = listOf(SampleEffect.Blur),
    ) { navController, _ ->
      Text(
        text = "Blur detail",
        modifier = Modifier.testTag("blur_detail").clickable(onClick = navController::navigateUp),
      )
    }
    val glassDemo = Sample(
      route = "glass-demo",
      title = "Glass demo",
      effects = listOf(SampleEffect.Glass),
    ) { navController, _ ->
      Text(
        text = "Glass detail",
        modifier = Modifier.testTag("glass_detail").clickable(onClick = navController::navigateUp),
      )
    }

    setContent {
      Samples(
        appTitle = "Haze Samples",
        samples = listOf(blurDemo, glassDemo),
      )
    }

    onNodeWithTag("sample_effect_blur").performClick()
    waitForIdle()
    onNodeWithTag("Blur demo").performClick()
    waitForIdle()
    onNodeWithTag("blur_detail").assertIsDisplayed().performClick()
    waitForIdle()
    onNodeWithTag("sample_list_back").performClick()
    waitForIdle()

    onNodeWithTag("sample_effect_glass").performClick()
    waitForIdle()
    onNodeWithTag("Glass demo").assertIsDisplayed()
    onNodeWithTag("blur_detail").assertDoesNotExist()

    onNodeWithTag("Glass demo").performClick()
    waitForIdle()
    onNodeWithTag("glass_detail").assertIsDisplayed()
    waitForIdle()
  }
}
