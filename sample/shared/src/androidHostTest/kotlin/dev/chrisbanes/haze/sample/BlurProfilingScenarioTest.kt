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
    assertThat(BlurProfilingScenario.entries.filter { it.noiseTintProperty == null }.map(BlurProfilingScenario::id)).isEqualTo(
      listOf(
        "stable_quality",
        "backdrop_stable_quality",
        "stable_balanced",
        "stable_performance",
        "progressive_quality",
        "progressive_balanced",
        "source_update_quality",
        "backdrop_source_update_quality",
        "source_update_balanced",
        "source_update_performance",
      ),
    )

    assertThat(
      BlurProfilingScenario.entries.filter { it.noiseTintProperty == null }.groupBy(BlurProfilingScenario::updatesSource)
        .mapValues { (_, scenarios) -> scenarios.map(BlurProfilingScenario::performanceMode) },
    ).isEqualTo(
      mapOf(
        false to listOf(
          HazePerformanceMode.Quality,
          HazePerformanceMode.Quality,
          HazePerformanceMode.Balanced,
          HazePerformanceMode.Performance,
          HazePerformanceMode.Quality,
          HazePerformanceMode.Balanced,
        ),
        true to listOf(
          HazePerformanceMode.Quality,
          HazePerformanceMode.Quality,
          HazePerformanceMode.Balanced,
          HazePerformanceMode.Performance,
        ),
      ),
    )
  }

  @Test
  fun noiseTintMatrix_changesOnlySelectedPropertyAndKeepsIndependentNodes() {
    val scenarios = BlurProfilingScenario.entries.filter { it.noiseTintProperty != null }
    assertThat(scenarios.map { it.id }).isEqualTo(
      listOf(
        "noise_tint_stable_1",
        "noise_tint_stable_3",
        "noise_tint_tint_1",
        "noise_tint_tint_3",
        "noise_tint_radius_1",
        "noise_tint_radius_3",
        "noise_tint_noise_1",
        "noise_tint_noise_3",
      ),
    )
    for (scenario in scenarios) {
      assertThat(scenario.performanceMode).isEqualTo(HazePerformanceMode.Quality)
      assertThat(scenario.usesBackdrop).isEqualTo(false)
      assertThat(scenario.updatesSource).isEqualTo(false)
      val start = List(scenario.noiseTintNodes) { blurNoiseTintParameters(scenario, 0f, it) }
      assertThat(start.toSet().size).isEqualTo(scenario.noiseTintNodes)
      for (node in start.indices) for (progress in listOf(0.5f, 1f)) {
        val later = blurNoiseTintParameters(scenario, progress, node)
        if (scenario.noiseTintProperty == BlurNoiseTintProperty.Radius) {
          assertThat(later.radius).isNotEqualTo(start[node].radius)
        } else {
          assertThat(later.radius).isEqualTo(start[node].radius)
        }
        if (scenario.noiseTintProperty == BlurNoiseTintProperty.Noise) {
          assertThat(later.noise).isNotEqualTo(start[node].noise)
        } else {
          assertThat(later.noise).isEqualTo(start[node].noise)
        }
        if (scenario.noiseTintProperty == BlurNoiseTintProperty.Tint) {
          assertThat(later.tint).isNotEqualTo(start[node].tint)
        } else {
          assertThat(later.tint).isEqualTo(start[node].tint)
        }
      }
    }
  }

  @Test
  fun qualityPairs_changeOnlyTheirInputBackend() {
    assertThat(BlurProfilingScenario.StableQuality.usesBackdrop).isEqualTo(false)
    assertThat(BlurProfilingScenario.BackdropStableQuality.usesBackdrop).isEqualTo(true)
    assertThat(BlurProfilingScenario.StableQuality.updatesSource)
      .isEqualTo(BlurProfilingScenario.BackdropStableQuality.updatesSource)
    assertThat(BlurProfilingScenario.SourceUpdateQuality.usesBackdrop).isEqualTo(false)
    assertThat(BlurProfilingScenario.BackdropSourceUpdateQuality.usesBackdrop).isEqualTo(true)
    assertThat(BlurProfilingScenario.SourceUpdateQuality.updatesSource)
      .isEqualTo(BlurProfilingScenario.BackdropSourceUpdateQuality.updatesSource)
  }

  @Test
  fun progressiveScenarios_useProgressiveModeWithTheirNamedPerformanceTier() {
    assertThat(
      BlurProfilingScenario.entries.filter { it.mode == ScaffoldSampleMode.Progressive },
    ).isEqualTo(
      listOf(
        BlurProfilingScenario.ProgressiveQuality,
        BlurProfilingScenario.ProgressiveBalanced,
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
