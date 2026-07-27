// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.glass.GlassOptics

internal const val GLASS_PROFILING_DURATION_MILLIS: Int = 3_000
internal const val GLASS_PROFILING_SETTLING_FRAMES: Int = 8

private val ProfilingProgressiveOptics = GlassOptics.Absolute(
  progressive = HazeProgressive.verticalGradient(
    startIntensity = 0f,
    endIntensity = 1f,
  ),
)
private val ProfilingNoRefractionOptics = GlassOptics.Absolute(refractionStrength = 0f)
private val ProfilingNoBlurOptics = GlassOptics.Absolute(depth = 0f, blurRadius = 0.dp)

internal enum class GlassProfilingScenario(
  val id: String,
  val glassEnabled: Boolean = true,
  val effectCount: Int = 1,
  val attachesDuringMeasurement: Boolean = false,
  val prewarmsBeforeMeasurement: Boolean = false,
  val steadyDraw: Boolean = false,
  val rimEnabled: Boolean = true,
  val fixedInputScale: Float? = null,
  val opticsOverride: GlassOptics.Absolute? = null,
  val fullChroma: Boolean = false,
) {
  EffectAttach(
    id = "effect_attach",
    attachesDuringMeasurement = true,
  ),
  EffectAttach3(
    id = "effect_attach_3",
    effectCount = 3,
    attachesDuringMeasurement = true,
  ),
  EffectAttach9(
    id = "effect_attach_9",
    effectCount = 9,
    attachesDuringMeasurement = true,
  ),
  EffectReattach(
    id = "effect_reattach",
    attachesDuringMeasurement = true,
    prewarmsBeforeMeasurement = true,
  ),
  SteadyFull(
    id = "steady_full",
    steadyDraw = true,
  ),
  SteadyFull3(
    id = "steady_full_3",
    effectCount = 3,
    steadyDraw = true,
  ),
  SteadyFull9(
    id = "steady_full_9",
    effectCount = 9,
    steadyDraw = true,
  ),
  SteadyProgressive(
    id = "steady_progressive",
    steadyDraw = true,
    opticsOverride = ProfilingProgressiveOptics,
  ),
  SteadyProgressive9(
    id = "steady_progressive_9",
    effectCount = 9,
    steadyDraw = true,
    opticsOverride = ProfilingProgressiveOptics,
  ),
  SteadyFullChroma(
    id = "steady_full_chroma",
    steadyDraw = true,
    fullChroma = true,
  ),
  SteadyFullChroma9(
    id = "steady_full_chroma_9",
    effectCount = 9,
    steadyDraw = true,
    fullChroma = true,
  ),
  SteadyNoRim(
    id = "steady_no_rim",
    steadyDraw = true,
    rimEnabled = false,
  ),
  SteadyNoRim9(
    id = "steady_no_rim_9",
    effectCount = 9,
    steadyDraw = true,
    rimEnabled = false,
  ),
  SteadyNoRefraction(
    id = "steady_no_refraction",
    steadyDraw = true,
    opticsOverride = ProfilingNoRefractionOptics,
  ),
  SteadyNoRefraction9(
    id = "steady_no_refraction_9",
    effectCount = 9,
    steadyDraw = true,
    opticsOverride = ProfilingNoRefractionOptics,
  ),
  SteadyNoBlur(
    id = "steady_no_blur",
    steadyDraw = true,
    opticsOverride = ProfilingNoBlurOptics,
  ),
  SteadyNoBlur9(
    id = "steady_no_blur_9",
    effectCount = 9,
    steadyDraw = true,
    opticsOverride = ProfilingNoBlurOptics,
  ),
  SteadyDepth50(
    id = "steady_depth_50",
    steadyDraw = true,
    opticsOverride = GlassOptics.Absolute(depth = 0.5f),
  ),
  SteadyScale60(
    id = "steady_scale_60",
    steadyDraw = true,
    fixedInputScale = 0.6f,
  ),
  SteadyScale50(
    id = "steady_scale_50",
    steadyDraw = true,
    fixedInputScale = 0.5f,
  ),
  SteadyScale50Nine(
    id = "steady_scale_50_9",
    effectCount = 9,
    steadyDraw = true,
    fixedInputScale = 0.5f,
  ),
  SteadyNoGlass(
    id = "steady_no_glass",
    glassEnabled = false,
    steadyDraw = true,
  ),
  RetainedReuse("retained_reuse"),
  InteractionUpdate("interaction_update"),
  InteractionUpdate9("interaction_update_9", effectCount = 9),
  OpticalUpdate("optical_update"),
  DepthUpdate("depth_update", opticsOverride = GlassOptics.Absolute()),
  BlurUpdate("blur_update", opticsOverride = GlassOptics.Absolute()),
  SourceUpdate("source_update"),
  SourceUpdate9("source_update_9", effectCount = 9),
  SourceUpdateNoGlass("source_update_no_glass", glassEnabled = false),
}

internal enum class GlassProfilingPhase(val id: String) {
  Selecting("selecting"),
  Settling("settling"),
  Ready("ready"),
  Running("running"),
  Complete("complete"),
}

@Immutable
internal data class GlassProfilingFrame(
  val sourceOffset: Float = 0f,
  val markerOffset: Float = 0f,
  val lightPosition: Offset = Offset(0.25f, 0.25f),
  val depth: Float = 1f,
  val blurRadius: Dp = 14.dp,
)

internal fun glassProfilingFrame(
  scenario: GlassProfilingScenario,
  progress: Float,
): GlassProfilingFrame {
  require(progress.isFinite() && progress in 0f..1f)
  val base = GlassProfilingFrame()
  return when (scenario) {
    GlassProfilingScenario.EffectAttach,
    GlassProfilingScenario.EffectAttach3,
    GlassProfilingScenario.EffectAttach9,
    GlassProfilingScenario.EffectReattach,
    GlassProfilingScenario.SteadyFull,
    GlassProfilingScenario.SteadyFull3,
    GlassProfilingScenario.SteadyFull9,
    GlassProfilingScenario.SteadyProgressive,
    GlassProfilingScenario.SteadyProgressive9,
    GlassProfilingScenario.SteadyFullChroma,
    GlassProfilingScenario.SteadyFullChroma9,
    GlassProfilingScenario.SteadyNoRim,
    GlassProfilingScenario.SteadyNoRim9,
    GlassProfilingScenario.SteadyNoRefraction,
    GlassProfilingScenario.SteadyNoRefraction9,
    GlassProfilingScenario.SteadyNoBlur,
    GlassProfilingScenario.SteadyNoBlur9,
    GlassProfilingScenario.SteadyDepth50,
    GlassProfilingScenario.SteadyScale60,
    GlassProfilingScenario.SteadyScale50,
    GlassProfilingScenario.SteadyScale50Nine,
    GlassProfilingScenario.SteadyNoGlass,
    GlassProfilingScenario.InteractionUpdate,
    GlassProfilingScenario.InteractionUpdate9,
    -> base
    GlassProfilingScenario.RetainedReuse -> base.copy(
      markerOffset = lerp(-0.4f, 0.4f, progress),
    )
    GlassProfilingScenario.OpticalUpdate -> base.copy(
      lightPosition = Offset(lerp(0.2f, 0.8f, progress), 0.2f),
    )
    GlassProfilingScenario.DepthUpdate -> base.copy(
      depth = lerp(0.15f, 0.85f, progress),
    )
    GlassProfilingScenario.BlurUpdate -> base.copy(
      blurRadius = lerp(4f, 28f, progress).dp,
    )
    GlassProfilingScenario.SourceUpdate,
    GlassProfilingScenario.SourceUpdate9,
    GlassProfilingScenario.SourceUpdateNoGlass,
    -> base.copy(sourceOffset = lerp(-0.08f, 0.08f, progress))
  }
}

internal inline fun glassProfilingSourceProgress(
  scenario: GlassProfilingScenario,
  progress: () -> Float,
): Float = when (scenario) {
  GlassProfilingScenario.SourceUpdate,
  GlassProfilingScenario.SourceUpdate9,
  GlassProfilingScenario.SourceUpdateNoGlass,
  -> progress()
  else -> 0f
}

internal fun shouldAttachProfilingGlass(
  scenario: GlassProfilingScenario,
  phase: GlassProfilingPhase,
): Boolean = when {
  !scenario.attachesDuringMeasurement -> true
  scenario.prewarmsBeforeMeasurement ->
    phase == GlassProfilingPhase.Settling ||
      phase == GlassProfilingPhase.Running ||
      phase == GlassProfilingPhase.Complete
  else -> phase == GlassProfilingPhase.Running || phase == GlassProfilingPhase.Complete
}

@Stable
internal class GlassProfilingState {
  var scenario: GlassProfilingScenario? by mutableStateOf(null)
    private set
  var phase: GlassProfilingPhase by mutableStateOf(GlassProfilingPhase.Selecting)
    private set
  var progress: Float by mutableFloatStateOf(0f)
    private set

  fun select(value: GlassProfilingScenario) {
    check(phase != GlassProfilingPhase.Running)
    scenario = value
    progress = 0f
    phase = GlassProfilingPhase.Settling
  }

  fun markReady() {
    check(phase == GlassProfilingPhase.Settling)
    phase = GlassProfilingPhase.Ready
  }

  fun start(): Boolean {
    if (phase != GlassProfilingPhase.Ready) return false
    progress = 0f
    phase = GlassProfilingPhase.Running
    return true
  }

  fun updateProgress(value: Float) {
    check(phase == GlassProfilingPhase.Running)
    require(value.isFinite() && value in 0f..1f)
    progress = value
  }

  fun complete() {
    check(phase == GlassProfilingPhase.Running)
    progress = 1f
    phase = GlassProfilingPhase.Complete
  }
}
