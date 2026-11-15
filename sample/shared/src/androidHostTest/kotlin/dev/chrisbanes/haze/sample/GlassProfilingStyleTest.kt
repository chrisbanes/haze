// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.glass.ContinuousRoundedCornerShape
import dev.chrisbanes.haze.glass.GlassStyle
import kotlin.test.Test

class GlassProfilingStyleTest {
  @Test
  fun comparisonWorkloads_resolveMatchingCircularCorners() {
    listOf(
      GlassProfilingScenario.SourceUpdateQuality,
      GlassProfilingScenario.SourceUpdateBalanced,
      GlassProfilingScenario.SourceUpdatePerformance,
      GlassProfilingScenario.SourceUpdateIndependent2,
      GlassProfilingScenario.SourceUpdateContainer2,
      GlassProfilingScenario.SourceUpdateContainer4,
      GlassProfilingScenario.ContainerGap2,
      GlassProfilingScenario.ContainerJoinLeave,
      GlassProfilingScenario.ContainerGroup3,
      GlassProfilingScenario.ContainerPress2,
      GlassProfilingScenario.EffectAttach,
      GlassProfilingScenario.EffectAttachContainer2,
      GlassProfilingScenario.LazyIndependentScroll,
      GlassProfilingScenario.LazyContainerScroll,
    ).forEach { scenario ->
      assertThat(resolvedShape(scenario), name = scenario.id).isEqualTo(RoundedCornerShape(16.dp))
    }
  }

  @Test
  fun continuousWorkloads_resolveContinuousCorners() {
    listOf(
      GlassProfilingScenario.SourceUpdateBalancedContinuous,
      GlassProfilingScenario.SourceUpdateContainer2Continuous,
      GlassProfilingScenario.SourceUpdateContainer4Continuous,
      GlassProfilingScenario.SourceUpdateContainer4ContinuousPerformance,
    ).forEach { scenario ->
      assertThat(resolvedShape(scenario), name = scenario.id).isEqualTo(ContinuousRoundedCornerShape(16.dp))
    }
  }

  @Test
  fun shapeUpdateWorkloads_keepTheirAnimatedCornerRadius() {
    listOf(
      GlassProfilingScenario.ShapeUpdateBalanced,
      GlassProfilingScenario.ShapeUpdateBalanced9,
    ).forEach { scenario ->
      assertThat(resolvedShape(scenario), name = scenario.id).isEqualTo(RoundedCornerShape(24.dp))
    }
  }
}

private fun resolvedShape(scenario: GlassProfilingScenario): CornerBasedShape {
  val style = profilingGlassStyle(scenario, glassProfilingFrame(scenario, 0.75f), Color.White) { 24.dp }
  // Replay the real style without adding a public library API just for a sample test.
  val appearance = Class.forName("dev.chrisbanes.haze.glass.GlassSystemAppearance").enumConstants.first()
  val resolve = Class.forName("dev.chrisbanes.haze.glass.GlassStyleKt")
    .methods.single { it.name == "resolveGlassStyleValues" }
  val values = resolve.invoke(null, GlassStyle, style, appearance)
  return values.javaClass.getMethod("getShape").invoke(values) as CornerBasedShape
}
