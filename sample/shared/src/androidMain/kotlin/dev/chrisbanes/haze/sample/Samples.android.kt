// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.remember

internal val AndroidCameraX = Sample(
  route = "camera-x",
  title = "CameraX",
  effects = listOf(SampleEffect.Blur, SampleEffect.Glass),
) { navController, effect ->
  CameraXSample(
    effect = effect,
    onBack = navController::navigateUp,
  )
}

internal val AndroidGlassProfiling = Sample(
  route = "glass-profiling",
  title = "Glass — Profiling",
  effects = listOf(SampleEffect.Glass),
) { navController, _ ->
  val initialScenarioId = LocalInitialProfilingScenarioId.current
  GlassProfilingSampleContent(
    state = remember(initialScenarioId) {
      GlassProfilingState().apply {
        initialScenarioId?.let { id ->
          select(checkNotNull(GlassProfilingScenario.entries.singleOrNull { it.id == id }))
        }
      }
    },
    onBack = navController::navigateUp,
  )
}

internal val AndroidBlurProfiling = Sample(
  route = "blur-profiling",
  title = "Blur — Profiling",
  effects = listOf(SampleEffect.Blur),
) { navController, _ ->
  val initialScenarioId = LocalInitialProfilingScenarioId.current
  BlurProfilingSampleContent(
    state = remember(initialScenarioId) {
      BlurProfilingState().apply {
        initialScenarioId?.let { id ->
          select(checkNotNull(BlurProfilingScenario.entries.singleOrNull { it.id == id }))
        }
      }
    },
    navController = navController,
    onBack = navController::navigateUp,
  )
}

internal val AndroidBlurStyleChurn = Sample(
  route = "blur-style-churn",
  title = "Blur — Equivalent Style Churn",
  effects = listOf(SampleEffect.Blur),
) { navController, _ ->
  ScaffoldSample(
    navController = navController,
    effect = SampleEffect.Blur,
    mode = ScaffoldSampleMode.StyleChurn,
  )
}

val AndroidExoPlayer = Sample(
  route = "exo-player",
  title = "ExoPlayer",
  effects = listOf(SampleEffect.Blur, SampleEffect.Glass),
) { navController, effect ->
  ExoPlayerSample(
    effect = effect,
    onBack = navController::navigateUp,
  )
}

actual val Samples: List<Sample> = buildList {
  addAll(CommonSamples)
  add(AndroidCameraX)
  add(Kamera)
  add(AndroidExoPlayer)
  add(AndroidBlurProfiling)
  add(AndroidBlurStyleChurn)
  add(AndroidGlassProfiling)
}
