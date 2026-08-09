// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.glass.GlassOptics
import kotlin.test.Test

class GlassProfilingScenarioTest {
  @Test
  fun calibrationMatrix_exposesEachNamedModeForStableAndSourceChangingWorkloads() {
    val matrixIds = setOf(
      "stable_adaptive",
      "stable_quality",
      "stable_balanced",
      "stable_performance",
      "source_update_adaptive",
      "source_update_quality",
      "source_update_balanced",
      "source_update_performance",
    )
    val matrix = GlassProfilingScenario.entries.filter { it.id in matrixIds }

    assertThat(matrix.map(GlassProfilingScenario::id)).isEqualTo(
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
    assertThat(matrix.take(4).map(GlassProfilingScenario::performanceMode)).isEqualTo(
      listOf(
        HazePerformanceMode.Adaptive,
        HazePerformanceMode.Quality,
        HazePerformanceMode.Balanced,
        HazePerformanceMode.Performance,
      ),
    )
    assertThat(matrix.drop(4).map(GlassProfilingScenario::performanceMode)).isEqualTo(
      listOf(
        HazePerformanceMode.Adaptive,
        HazePerformanceMode.Quality,
        HazePerformanceMode.Balanced,
        HazePerformanceMode.Performance,
      ),
    )
  }

  @Test
  fun scenarioIds_areStableAndUnique() {
    assertThat(GlassProfilingScenario.entries.map(GlassProfilingScenario::id)).isEqualTo(
      listOf(
        "effect_attach",
        "effect_attach_3",
        "effect_attach_9",
        "effect_reattach",
        "stable_adaptive",
        "stable_quality",
        "stable_balanced",
        "stable_performance",
        "steady_full_3",
        "steady_full_9",
        "steady_progressive",
        "steady_progressive_9",
        "steady_full_chroma",
        "steady_full_chroma_9",
        "steady_no_rim",
        "steady_no_rim_9",
        "steady_no_refraction",
        "steady_no_refraction_9",
        "steady_no_blur",
        "steady_no_blur_9",
        "steady_depth_50",
        "steady_performance_9",
        "steady_no_glass",
        "retained_reuse",
        "interaction_update",
        "interaction_update_9",
        "optical_update",
        "depth_update",
        "blur_update",
        "source_update_adaptive",
        "source_update_quality",
        "source_update_balanced",
        "source_update_performance",
        "source_update_9",
        "source_update_no_glass",
      ),
    )
    assertThat(
      GlassProfilingScenario.entries.map(GlassProfilingScenario::id).toSet().size,
    ).isEqualTo(GlassProfilingScenario.entries.size)
  }

  @Test
  fun noGlassControl_isTheOnlyDisabledScenario() {
    GlassProfilingScenario.entries.forEach { scenario ->
      assertThat(
        !scenario.glassEnabled,
        name = scenario.id,
      ).isEqualTo(
        scenario == GlassProfilingScenario.SourceUpdateNoGlass ||
          scenario == GlassProfilingScenario.SteadyNoGlass,
      )
    }
  }

  @Test
  fun frames_changeOnlyTheInputNamedByTheScenario() {
    GlassProfilingScenario.entries.forEach { scenario ->
      val expectedChanges = when (scenario) {
        GlassProfilingScenario.RetainedReuse -> setOf("markerOffset")
        GlassProfilingScenario.OpticalUpdate -> setOf("lightPosition")
        GlassProfilingScenario.DepthUpdate -> setOf("depth")
        GlassProfilingScenario.BlurUpdate -> setOf("blurRadius")
        GlassProfilingScenario.SourceUpdateAdaptive,
        GlassProfilingScenario.SourceUpdateQuality,
        GlassProfilingScenario.SourceUpdateBalanced,
        GlassProfilingScenario.SourceUpdatePerformance,
        GlassProfilingScenario.SourceUpdate9,
        GlassProfilingScenario.SourceUpdateNoGlass,
        -> setOf("sourceOffset")
        else -> emptySet()
      }
      val early = glassProfilingFrame(scenario, 0.25f)
      val late = glassProfilingFrame(scenario, 0.75f)
      assertThat(changedFields(early, late), name = scenario.id).isEqualTo(expectedChanges)
    }
  }

  @Test
  fun styles_changeOnlyForOpticalDepthAndBlurScenarios() {
    GlassProfilingScenario.entries.forEach { scenario ->
      assertThat(profilingStyleUsesFrame(scenario), name = scenario.id).isEqualTo(
        scenario == GlassProfilingScenario.OpticalUpdate ||
          scenario == GlassProfilingScenario.DepthUpdate ||
          scenario == GlassProfilingScenario.BlurUpdate,
      )
    }
  }

  @Test
  fun fullScenarios_useDefaultGlassStyleWithoutOpticsOverrides() {
    listOf(
      GlassProfilingScenario.StableAdaptive,
      GlassProfilingScenario.StableQuality,
      GlassProfilingScenario.StableBalanced,
      GlassProfilingScenario.StablePerformance,
      GlassProfilingScenario.SteadyFull3,
      GlassProfilingScenario.SteadyFull9,
    ).forEach { scenario ->
      assertThat(scenario.opticsOverride, name = scenario.id).isNull()
    }
  }

  @Test
  fun ablationScenarios_useExplicitFixedOptics() {
    assertThat(GlassProfilingScenario.SteadyNoRefraction.opticsOverride).isEqualTo(
      GlassOptics.Fixed(refractionStrength = 0f),
    )
    assertThat(GlassProfilingScenario.SteadyNoBlur.opticsOverride).isEqualTo(
      GlassOptics.Fixed(depth = 0f, blurRadius = 0.dp),
    )
    assertThat(GlassProfilingScenario.SteadyDepth50.opticsOverride).isEqualTo(
      GlassOptics.Fixed(depth = 0.5f),
    )
  }

  @Test
  fun featureScenarios_startFromDefaultStyleAndOverrideOnlyTheirFeature() {
    listOf(
      GlassProfilingScenario.SteadyProgressive,
      GlassProfilingScenario.SteadyProgressive9,
    ).forEach { scenario ->
      assertThat(scenario.opticsOverride, name = scenario.id).isEqualTo(
        scenario.opticsOverride,
      )
    }
    listOf(
      GlassProfilingScenario.SteadyFullChroma,
      GlassProfilingScenario.SteadyFullChroma9,
    ).forEach { scenario ->
      assertThat(scenario.fullChroma, name = scenario.id).isTrue()
    }
  }

  @Test
  fun sourceProgress_isReadOnlyForSourceScenarios() {
    GlassProfilingScenario.entries.forEach { scenario ->
      var readCount = 0
      val resolved = glassProfilingSourceProgress(scenario) {
        readCount++
        0.75f
      }
      val updatesSource = scenario == GlassProfilingScenario.SourceUpdateAdaptive ||
        scenario == GlassProfilingScenario.SourceUpdateQuality ||
        scenario == GlassProfilingScenario.SourceUpdateBalanced ||
        scenario == GlassProfilingScenario.SourceUpdatePerformance ||
        scenario == GlassProfilingScenario.SourceUpdate9 ||
        scenario == GlassProfilingScenario.SourceUpdateNoGlass

      assertThat(readCount, name = scenario.id).isEqualTo(if (updatesSource) 1 else 0)
      assertThat(resolved, name = scenario.id).isEqualTo(if (updatesSource) 0.75f else 0f)
    }
  }

  @Test
  fun state_enforcesSelectingSettlingReadyRunningCompleteOrder() {
    val state = GlassProfilingState()
    assertThat(state.phase).isEqualTo(GlassProfilingPhase.Selecting)
    assertThat(state.start()).isFalse()

    state.select(GlassProfilingScenario.SourceUpdateAdaptive)
    assertThat(state.phase).isEqualTo(GlassProfilingPhase.Settling)
    assertThat(state.start()).isFalse()

    state.markReady()
    assertThat(state.phase).isEqualTo(GlassProfilingPhase.Ready)
    assertThat(state.start()).isTrue()
    assertThat(state.phase).isEqualTo(GlassProfilingPhase.Running)

    state.updateProgress(0.4f)
    assertThat(state.progress).isEqualTo(0.4f)
    state.complete()
    assertThat(state.phase).isEqualTo(GlassProfilingPhase.Complete)
    assertThat(state.progress).isEqualTo(1f)
    assertThat(state.start()).isFalse()
  }

  @Test
  fun state_rejectsInvalidProgressAndSelectionDuringRun() {
    val state = GlassProfilingState()
    state.select(GlassProfilingScenario.BlurUpdate)
    state.markReady()
    assertThat(state.start()).isTrue()

    assertFailure { state.updateProgress(Float.NaN) }.isInstanceOf<IllegalArgumentException>()
    assertFailure { state.updateProgress(1.1f) }.isInstanceOf<IllegalArgumentException>()
    assertFailure {
      state.select(GlassProfilingScenario.DepthUpdate)
    }.isInstanceOf<IllegalStateException>()
  }

  @Test
  fun effectAttachScenarios_areDetachedUntilMeasurementStarts() {
    GlassProfilingScenario.entries
      .filter { it.attachesDuringMeasurement && !it.prewarmsBeforeMeasurement }
      .forEach { scenario ->
        GlassProfilingPhase.entries.forEach { phase ->
          assertThat(
            shouldAttachProfilingGlass(scenario, phase),
            name = "${scenario.id}/${phase.id}",
          ).isEqualTo(
            phase == GlassProfilingPhase.Running || phase == GlassProfilingPhase.Complete,
          )
        }
      }

    assertThat(
      GlassProfilingScenario.entries
        .filter { it.attachesDuringMeasurement && !it.prewarmsBeforeMeasurement }
        .map(GlassProfilingScenario::effectCount),
    ).isEqualTo(listOf(1, 3, 9))

    GlassProfilingScenario.entries
      .filter { it.glassEnabled && !it.attachesDuringMeasurement }
      .forEach { scenario ->
        GlassProfilingPhase.entries.forEach { phase ->
          assertThat(
            shouldAttachProfilingGlass(scenario, phase),
            name = "${scenario.id}/${phase.id}",
          ).isTrue()
        }
      }
  }

  @Test
  fun effectReattach_isPrewarmedDetachedAndReattachedAcrossMeasurementBoundary() {
    val scenario = GlassProfilingScenario.EffectReattach

    assertThat(shouldAttachProfilingGlass(scenario, GlassProfilingPhase.Selecting)).isFalse()
    assertThat(shouldAttachProfilingGlass(scenario, GlassProfilingPhase.Settling)).isTrue()
    assertThat(shouldAttachProfilingGlass(scenario, GlassProfilingPhase.Ready)).isFalse()
    assertThat(shouldAttachProfilingGlass(scenario, GlassProfilingPhase.Running)).isTrue()
    assertThat(shouldAttachProfilingGlass(scenario, GlassProfilingPhase.Complete)).isTrue()
  }
}

private fun changedFields(
  first: GlassProfilingFrame,
  second: GlassProfilingFrame,
): Set<String> = buildSet {
  if (first.sourceOffset != second.sourceOffset) add("sourceOffset")
  if (first.markerOffset != second.markerOffset) add("markerOffset")
  if (first.lightPosition != second.lightPosition) add("lightPosition")
  if (first.depth != second.depth) add("depth")
  if (first.blurRadius != second.blurRadius) add("blurRadius")
}
