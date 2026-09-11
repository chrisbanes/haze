// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTestApi::class)
class SamplesJvmTest {
  // This test dispatches click actions to Main, which is provided by kotlinx-coroutines-swing here.
  @Test
  fun directLink_seedsNormalBackHistoryAndDoesNotReopenOnRecomposition() = runComposeUiTest {
    var appTitle by mutableStateOf("Haze Samples")
    val sample = Sample(route = "probe", title = "Probe", effects = listOf(SampleEffect.Glass)) { nav, effect ->
      Button(onClick = { nav.popBackStack() }) { Text("Exit ${effect.label} probe") }
    }
    val selection = resolveSampleLaunch(mapOf("sample" to "probe", "effect" to "glass"), listOf(sample))
      as SampleLaunchRequest.Selected
    setContent {
      Samples(appTitle = appTitle, samples = listOf(sample), initialSelection = selection)
    }

    onNodeWithText("Exit Glass probe").assertIsDisplayed()
    withContext(Dispatchers.Main) { onNodeWithText("Exit Glass probe").performClick() }
    onNodeWithTag("sample_list").assertIsDisplayed()
    onNodeWithText("Exit Glass probe").assertDoesNotExist()
    runOnIdle { appTitle = "Updated samples" }
    onNodeWithText("Exit Glass probe").assertDoesNotExist()
    withContext(Dispatchers.Main) { onNodeWithContentDescription("Back").performClick() }
    onNodeWithTag("sample_effect_glass").assertIsDisplayed()
    onNodeWithTag("sample_effect_blur").assertIsDisplayed()
  }

  @Test
  fun kamera_isRegisteredAndExposesBothBuiltInEffects() {
    assertThat(Samples).contains(Kamera)
    assertThat(Kamera.effects).isEqualTo(
      listOf(SampleEffect.Blur, SampleEffect.Glass),
    )
  }
}
