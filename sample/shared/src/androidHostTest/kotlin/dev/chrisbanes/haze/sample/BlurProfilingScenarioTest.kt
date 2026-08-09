// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazePerformanceMode
import kotlin.test.Test

class BlurProfilingScenarioTest {
  @Test
  fun matrix_exposesEachNamedModeForStableAndSourceChangingWorkloads() {
    assertThat(BlurProfilingScenario.entries.map(BlurProfilingScenario::id)).isEqualTo(
      listOf(
        "stable_adaptive",
        "stable_quality",
        "stable_balanced",
        "stable_performance",
        "source_update_adaptive",
        "source_update_quality",
        "source_update_balanced",
        "source_update_performance",
      ),
    )

    assertThat(
      BlurProfilingScenario.entries.groupBy(BlurProfilingScenario::updatesSource)
        .mapValues { (_, scenarios) -> scenarios.map(BlurProfilingScenario::performanceMode) },
    ).isEqualTo(
      mapOf(
        false to listOf(
          HazePerformanceMode.Adaptive,
          HazePerformanceMode.Quality,
          HazePerformanceMode.Balanced,
          HazePerformanceMode.Performance,
        ),
        true to listOf(
          HazePerformanceMode.Adaptive,
          HazePerformanceMode.Quality,
          HazePerformanceMode.Balanced,
          HazePerformanceMode.Performance,
        ),
      ),
    )
  }

  @Test
  fun sourceOffset_changesOnlyForSourceUpdateWorkloads() {
    BlurProfilingScenario.entries.forEach { scenario ->
      val early = blurProfilingSourceOffset(scenario, progress = 0.25f)
      val late = blurProfilingSourceOffset(scenario, progress = 0.75f)
      if (scenario.updatesSource) {
        assertThat(early, name = scenario.id).isNotEqualTo(late)
      } else {
        assertThat(early, name = scenario.id).isEqualTo(late)
      }
    }
  }
}
