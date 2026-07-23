// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

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

internal const val GLASS_PROFILING_DURATION_MILLIS: Int = 3_000

internal enum class GlassProfilingScenario(
  val id: String,
  val glassEnabled: Boolean = true,
) {
  EffectAttach("effect_attach"),
  RetainedReuse("retained_reuse"),
  InteractionUpdate("interaction_update"),
  OpticalUpdate("optical_update"),
  DepthUpdate("depth_update"),
  BlurUpdate("blur_update"),
  SourceUpdate("source_update"),
  SourceUpdateNoGlass("source_update_no_glass", glassEnabled = false),
}

internal enum class GlassProfilingPhase(val id: String) {
  Selecting("selecting"),
  Ready("ready"),
  Running("running"),
  Complete("complete"),
}

@Immutable
internal data class GlassProfilingFrame(
  val sourceOffset: Float = 0f,
  val markerOffset: Float = 0f,
  val lightPosition: Offset = Offset(0.25f, 0.25f),
  val depth: Float = 0.5f,
  val blurRadius: Dp = 14.dp,
  val pressed: Boolean = false,
)

internal fun glassProfilingFrame(
  scenario: GlassProfilingScenario,
  progress: Float,
): GlassProfilingFrame {
  require(progress.isFinite() && progress in 0f..1f)
  val base = GlassProfilingFrame()
  return when (scenario) {
    GlassProfilingScenario.EffectAttach -> base
    GlassProfilingScenario.RetainedReuse -> base.copy(
      markerOffset = lerp(-0.4f, 0.4f, progress),
    )
    GlassProfilingScenario.InteractionUpdate -> base.copy(
      pressed = progress < 0.5f,
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
    GlassProfilingScenario.SourceUpdateNoGlass,
    -> base.copy(sourceOffset = lerp(-0.08f, 0.08f, progress))
  }
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

private fun lerp(start: Float, end: Float, fraction: Float): Float =
  start + (end - start) * fraction
