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
import androidx.navigation.compose.rememberNavController
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode
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

  @Test
  fun shell_retainsSelectedModeAcrossNavigation() = runComposeUiTest {
    val firstSample = performanceModeSample("performance-detail", "Performance detail", "selected_mode")
    val secondSample = performanceModeSample(
      "second-performance-detail",
      "Second performance detail",
      "second_selected_mode",
    )
    lateinit var navController: androidx.navigation.NavHostController
    setContent {
      navController = rememberNavController()
      Samples(
        appTitle = "Haze Samples",
        navController = navController,
        samples = listOf(firstSample, secondSample),
      )
    }

    onNodeWithTag("sample_effect_blur").performClick()
    onNodeWithTag("Performance detail").performClick()
    onNodeWithTag("sample_performance_settings").performClick()
    onNodeWithTag("sample_performance_quality").performClick()
    onNodeWithTag("sample_performance_done").performClick()
    runOnIdle { navController.navigate("second-performance-detail/blur") }

    onNodeWithTag("second_selected_mode_quality").assertIsDisplayed()
  }

  @Test
  fun profilingRoutes_excludeGlobalSettingsChrome() = runComposeUiTest {
    val profilingSample = Sample(
      route = "blur-profiling",
      title = "Blur profiling",
      effects = listOf(SampleEffect.Blur),
    ) { _, _ -> Text("Profiling") }
    setContent { Samples(appTitle = "Haze Samples", samples = listOf(profilingSample)) }

    onNodeWithTag("sample_effect_blur").performClick()
    onNodeWithTag("Blur profiling").performClick()

    onNodeWithTag("sample_performance_settings").assertDoesNotExist()
  }

  private fun performanceModeSample(
    route: String,
    title: String,
    tagPrefix: String,
  ) = Sample(
    route = route,
    title = title,
    effects = listOf(SampleEffect.Blur),
  ) { _, _ ->
    val mode = LocalHazePerformanceMode.current
    Text(
      text = mode.toString(),
      modifier = Modifier.testTag(
        if (mode == HazePerformanceMode.Quality) "${tagPrefix}_quality" else "${tagPrefix}_other",
      ),
    )
  }
}
