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
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

class SamplePerformanceSettingsTest : ContextTest() {
  @Test
  fun newSettings_startAdaptiveWithMetricsDisabled() {
    val settings = SamplePerformanceSettingsState()

    assertThat(settings.performanceMode).isEqualTo(HazePerformanceMode.Adaptive)
    assertThat(settings.metricsEnabled).isEqualTo(false)
  }

  @Test
  fun customQuality_selectsFixedModeAndPreservesValueAcrossPresetChanges() {
    val settings = SamplePerformanceSettingsState()

    settings.updateCustomQuality(0.7f)
    settings.selectPreset(SamplePerformancePreset.Quality)
    settings.selectPreset(SamplePerformancePreset.Custom)

    assertThat(settings.performanceMode).isEqualTo(HazePerformanceMode.Fixed(0.7f))
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun settingsSheet_selectsPreset() = runComposeUiTest {
    val settings = SamplePerformanceSettingsState()
    setContent { SamplePerformanceSettingsSheet(settings, onDismissRequest = {}) }

    onNodeWithTag("sample_performance_quality").performClick()
    waitForIdle()
    runOnIdle {
      assertThat(settings.performanceMode).isEqualTo(HazePerformanceMode.Quality)
    }
  }

  @Test
  fun metricsFlag_canBeEnabledAndDisabled() {
    val settings = SamplePerformanceSettingsState()
    settings.updateMetricsEnabled(true)
    assertThat(settings.metricsEnabled).isEqualTo(true)
    settings.updateMetricsEnabled(false)
    assertThat(settings.metricsEnabled).isEqualTo(false)
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun shell_retainsSelectedModeAcrossNavigation() = runComposeUiTest {
    val firstSample = Sample(
      route = "performance-detail",
      title = "Performance detail",
      effects = listOf(SampleEffect.Blur),
    ) { _, _ ->
      val mode = LocalSamplePerformanceMode.current
      Text(
        text = mode.toString(),
        modifier = Modifier.testTag(
          if (mode == HazePerformanceMode.Quality) "selected_mode_quality" else "selected_mode_other",
        ),
      )
    }
    val secondSample = Sample(
      route = "second-performance-detail",
      title = "Second performance detail",
      effects = listOf(SampleEffect.Blur),
    ) { _, _ ->
      val mode = LocalSamplePerformanceMode.current
      Text(
        text = mode.toString(),
        modifier = Modifier.testTag(
          if (mode == HazePerformanceMode.Quality) {
            "second_selected_mode_quality"
          } else {
            "second_selected_mode_other"
          },
        ),
      )
    }
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
}
