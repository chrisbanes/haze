// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import kotlin.test.Test

class GlassProfilingScenarioTest {
  @Test
  fun scenarioIds_areStableAndUnique() {
    assertThat(GlassProfilingScenario.entries.map(GlassProfilingScenario::id)).isEqualTo(
      listOf(
        "effect_attach",
        "effect_attach_3",
        "effect_attach_9",
        "effect_reattach",
        "steady_full",
        "steady_full_3",
        "steady_full_9",
        "steady_no_rim",
        "steady_no_rim_9",
        "steady_no_refraction",
        "steady_no_refraction_9",
        "steady_no_blur",
        "steady_no_blur_9",
        "steady_depth_1",
        "steady_scale_60",
        "steady_scale_50",
        "steady_scale_50_9",
        "steady_no_glass",
        "retained_reuse",
        "interaction_update",
        "optical_update",
        "depth_update",
        "blur_update",
        "source_update",
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
    val expected = mapOf(
      GlassProfilingScenario.EffectAttach to emptySet(),
      GlassProfilingScenario.EffectAttach3 to emptySet(),
      GlassProfilingScenario.EffectAttach9 to emptySet(),
      GlassProfilingScenario.EffectReattach to emptySet(),
      GlassProfilingScenario.SteadyFull to emptySet(),
      GlassProfilingScenario.SteadyFull3 to emptySet(),
      GlassProfilingScenario.SteadyFull9 to emptySet(),
      GlassProfilingScenario.SteadyNoRim to emptySet(),
      GlassProfilingScenario.SteadyNoRim9 to emptySet(),
      GlassProfilingScenario.SteadyNoRefraction to emptySet(),
      GlassProfilingScenario.SteadyNoRefraction9 to emptySet(),
      GlassProfilingScenario.SteadyNoBlur to emptySet(),
      GlassProfilingScenario.SteadyNoBlur9 to emptySet(),
      GlassProfilingScenario.SteadyDepth1 to emptySet(),
      GlassProfilingScenario.SteadyScale60 to emptySet(),
      GlassProfilingScenario.SteadyScale50 to emptySet(),
      GlassProfilingScenario.SteadyScale50Nine to emptySet(),
      GlassProfilingScenario.SteadyNoGlass to emptySet(),
      GlassProfilingScenario.RetainedReuse to setOf("markerOffset"),
      GlassProfilingScenario.InteractionUpdate to setOf("pressed"),
      GlassProfilingScenario.OpticalUpdate to setOf("lightPosition"),
      GlassProfilingScenario.DepthUpdate to setOf("depth"),
      GlassProfilingScenario.BlurUpdate to setOf("blurRadius"),
      GlassProfilingScenario.SourceUpdate to setOf("sourceOffset"),
      GlassProfilingScenario.SourceUpdateNoGlass to setOf("sourceOffset"),
    )

    expected.forEach { (scenario, expectedChanges) ->
      val early = glassProfilingFrame(scenario, 0.25f)
      val late = glassProfilingFrame(scenario, 0.75f)
      assertThat(changedFields(early, late), name = scenario.id).isEqualTo(expectedChanges)
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
      val updatesSource = scenario == GlassProfilingScenario.SourceUpdate ||
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

    state.select(GlassProfilingScenario.SourceUpdate)
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
  if (first.pressed != second.pressed) add("pressed")
}
