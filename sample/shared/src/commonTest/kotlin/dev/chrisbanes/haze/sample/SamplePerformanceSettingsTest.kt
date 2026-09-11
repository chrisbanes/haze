// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
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
    settings.selectMode(HazePerformanceMode.Quality)
    settings.selectMode(null)

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

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun settingsSheet_sliderSelectsCustomFixedMode() = runComposeUiTest {
    val settings = SamplePerformanceSettingsState()
    setContent { SamplePerformanceSettingsSheet(settings, onDismissRequest = {}) }

    onNodeWithTag("sample_performance_custom_slider")
      .performSemanticsAction(SemanticsActions.SetProgress) { action -> action(0.7f) }
    runOnIdle {
      assertThat(settings.performanceMode).isEqualTo(HazePerformanceMode.Fixed(0.7f))
    }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun settingsSheet_metricsSwitchEnablesAndDisablesCollection() = runComposeUiTest {
    val settings = SamplePerformanceSettingsState()
    setContent { SamplePerformanceSettingsSheet(settings, onDismissRequest = {}) }

    onNodeWithTag("sample_metrics_enabled")
      .performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
    runOnIdle { assertThat(settings.metricsEnabled).isEqualTo(true) }
    onNodeWithTag("sample_metrics_enabled")
      .performSemanticsAction(SemanticsActions.OnClick) { action -> action() }
    runOnIdle { assertThat(settings.metricsEnabled).isEqualTo(false) }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun freshShell_exposesAdaptivePreset() = runComposeUiTest {
    setContent { Samples(appTitle = "Haze Samples", samples = listOf(Sample.CreditCard)) }

    onNodeWithTag("sample_performance_settings").performClick()
    onNodeWithTag("sample_performance_adaptive").assertIsSelected()
  }
}
