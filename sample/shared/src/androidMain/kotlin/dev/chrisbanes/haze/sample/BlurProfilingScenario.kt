// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazePerformanceMode

internal const val BLUR_PROFILING_DURATION_MILLIS: Int = 3_000
internal const val BLUR_PROFILING_SETTLING_FRAMES: Int = 8

internal enum class BlurProfilingScenario(
  val id: String,
  val performanceMode: HazePerformanceMode,
  val updatesSource: Boolean,
  val mode: ScaffoldSampleMode = ScaffoldSampleMode.Default,
  val usesBackdrop: Boolean = false,
) {
  StableAdaptive("stable_adaptive", HazePerformanceMode.Adaptive, updatesSource = false),
  StableQuality("stable_quality", HazePerformanceMode.Quality, updatesSource = false),
  BackdropStableQuality(
    "backdrop_stable_quality",
    HazePerformanceMode.Quality,
    updatesSource = false,
    usesBackdrop = true,
  ),
  StableBalanced("stable_balanced", HazePerformanceMode.Balanced, updatesSource = false),
  StablePerformance("stable_performance", HazePerformanceMode.Performance, updatesSource = false),
  ProgressiveQuality(
    "progressive_quality",
    HazePerformanceMode.Quality,
    updatesSource = false,
    mode = ScaffoldSampleMode.Progressive,
  ),
  ProgressiveBalanced(
    "progressive_balanced",
    HazePerformanceMode.Balanced,
    updatesSource = false,
    mode = ScaffoldSampleMode.Progressive,
  ),
  SourceUpdateAdaptive("source_update_adaptive", HazePerformanceMode.Adaptive, updatesSource = true),
  SourceUpdateQuality("source_update_quality", HazePerformanceMode.Quality, updatesSource = true),
  BackdropSourceUpdateQuality(
    "backdrop_source_update_quality",
    HazePerformanceMode.Quality,
    updatesSource = true,
    usesBackdrop = true,
  ),
  SourceUpdateBalanced("source_update_balanced", HazePerformanceMode.Balanced, updatesSource = true),
  SourceUpdatePerformance("source_update_performance", HazePerformanceMode.Performance, updatesSource = true),
}

internal enum class BlurProfilingPhase(val id: String) {
  Selecting("selecting"),
  Settling("settling"),
  Ready("ready"),
  Running("running"),
  Complete("complete"),
}

internal fun blurProfilingSourceOffset(
  scenario: BlurProfilingScenario,
  progress: Float,
): Float {
  require(progress.isFinite() && progress in 0f..1f)
  return if (scenario.updatesSource) lerp(-48f, 48f, progress) else 0f
}

@Stable
internal class BlurProfilingState {
  var scenario: BlurProfilingScenario? by mutableStateOf(null)
    private set
  var phase: BlurProfilingPhase by mutableStateOf(BlurProfilingPhase.Selecting)
    private set
  var progress: Float by mutableFloatStateOf(0f)
    private set

  fun select(value: BlurProfilingScenario) {
    check(phase != BlurProfilingPhase.Running)
    scenario = value
    progress = 0f
    phase = BlurProfilingPhase.Settling
  }

  fun markReady() {
    check(phase == BlurProfilingPhase.Settling)
    phase = BlurProfilingPhase.Ready
  }

  fun start(): Boolean {
    if (phase != BlurProfilingPhase.Ready) return false
    progress = 0f
    phase = BlurProfilingPhase.Running
    return true
  }

  fun updateProgress(value: Float) {
    check(phase == BlurProfilingPhase.Running)
    require(value.isFinite() && value in 0f..1f)
    progress = value
  }

  fun complete() {
    check(phase == BlurProfilingPhase.Running)
    progress = 1f
    phase = BlurProfilingPhase.Complete
  }
}
