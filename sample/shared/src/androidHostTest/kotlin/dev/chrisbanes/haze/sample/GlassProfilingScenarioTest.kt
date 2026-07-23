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
        scenario == GlassProfilingScenario.SourceUpdateNoGlass,
        !scenario.glassEnabled,
        scenario.id,
      )
    }
  }

  @Test
  fun frames_changeOnlyTheInputNamedByTheScenario() {
    val expected = mapOf(
      GlassProfilingScenario.EffectAttach to emptySet(),
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
  fun state_enforcesSelectingReadyRunningCompleteOrder() {
    val state = GlassProfilingState()
    assertEquals(GlassProfilingPhase.Selecting, state.phase)
    assertFalse(state.start())

    state.select(GlassProfilingScenario.SourceUpdate)
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
    assertTrue(state.start())

    assertFailsWith<IllegalArgumentException> { state.updateProgress(Float.NaN) }
    assertFailsWith<IllegalArgumentException> { state.updateProgress(1.1f) }
    assertFailsWith<IllegalStateException> {
      state.select(GlassProfilingScenario.DepthUpdate)
    }
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
