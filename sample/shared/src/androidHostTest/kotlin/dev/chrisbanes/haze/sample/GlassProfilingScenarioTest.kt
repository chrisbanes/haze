// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassProfilingScenarioTest {
  @Test
  fun scenarioIds_areStableAndUnique() {
    assertEquals(
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
      GlassProfilingScenario.entries.map(GlassProfilingScenario::id),
    )
    assertEquals(
      GlassProfilingScenario.entries.size,
      GlassProfilingScenario.entries.map(GlassProfilingScenario::id).toSet().size,
    )
  }

  @Test
  fun noGlassControl_isTheOnlyDisabledScenario() {
    GlassProfilingScenario.entries.forEach { scenario ->
      assertEquals(
        scenario == GlassProfilingScenario.SourceUpdateNoGlass ||
          scenario == GlassProfilingScenario.SteadyNoGlass,
        !scenario.glassEnabled,
        scenario.id,
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
      assertEquals(expectedChanges, changedFields(early, late), scenario.id)
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

      assertEquals(if (updatesSource) 1 else 0, readCount, scenario.id)
      assertEquals(if (updatesSource) 0.75f else 0f, resolved, scenario.id)
    }
  }

  @Test
  fun state_enforcesSelectingSettlingReadyRunningCompleteOrder() {
    val state = GlassProfilingState()
    assertEquals(GlassProfilingPhase.Selecting, state.phase)
    assertFalse(state.start())

    state.select(GlassProfilingScenario.SourceUpdate)
    assertEquals(GlassProfilingPhase.Settling, state.phase)
    assertFalse(state.start())

    state.markReady()
    assertEquals(GlassProfilingPhase.Ready, state.phase)
    assertTrue(state.start())
    assertEquals(GlassProfilingPhase.Running, state.phase)

    state.updateProgress(0.4f)
    assertEquals(0.4f, state.progress)
    state.complete()
    assertEquals(GlassProfilingPhase.Complete, state.phase)
    assertEquals(1f, state.progress)
    assertFalse(state.start())
  }

  @Test
  fun state_rejectsInvalidProgressAndSelectionDuringRun() {
    val state = GlassProfilingState()
    state.select(GlassProfilingScenario.BlurUpdate)
    state.markReady()
    assertTrue(state.start())

    assertFailsWith<IllegalArgumentException> { state.updateProgress(Float.NaN) }
    assertFailsWith<IllegalArgumentException> { state.updateProgress(1.1f) }
    assertFailsWith<IllegalStateException> {
      state.select(GlassProfilingScenario.DepthUpdate)
    }
  }

  @Test
  fun effectAttachScenarios_areDetachedUntilMeasurementStarts() {
    GlassProfilingScenario.entries
      .filter { it.attachesDuringMeasurement && !it.prewarmsBeforeMeasurement }
      .forEach { scenario ->
        GlassProfilingPhase.entries.forEach { phase ->
          assertEquals(
            phase == GlassProfilingPhase.Running || phase == GlassProfilingPhase.Complete,
            shouldAttachProfilingGlass(scenario, phase),
            "${scenario.id}/${phase.id}",
          )
        }
      }

    assertEquals(
      listOf(1, 3, 9),
      GlassProfilingScenario.entries
        .filter { it.attachesDuringMeasurement && !it.prewarmsBeforeMeasurement }
        .map(GlassProfilingScenario::effectCount),
    )

    GlassProfilingScenario.entries
      .filter { it.glassEnabled && !it.attachesDuringMeasurement }
      .forEach { scenario ->
        GlassProfilingPhase.entries.forEach { phase ->
          assertTrue(shouldAttachProfilingGlass(scenario, phase), "${scenario.id}/${phase.id}")
        }
      }
  }

  @Test
  fun effectReattach_isPrewarmedDetachedAndReattachedAcrossMeasurementBoundary() {
    val scenario = GlassProfilingScenario.EffectReattach

    assertFalse(shouldAttachProfilingGlass(scenario, GlassProfilingPhase.Selecting))
    assertTrue(shouldAttachProfilingGlass(scenario, GlassProfilingPhase.Settling))
    assertFalse(shouldAttachProfilingGlass(scenario, GlassProfilingPhase.Ready))
    assertTrue(shouldAttachProfilingGlass(scenario, GlassProfilingPhase.Running))
    assertTrue(shouldAttachProfilingGlass(scenario, GlassProfilingPhase.Complete))
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
