// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.remember

internal val AndroidGlassProfiling = Sample(
  route = "glass-profiling",
  title = "Glass — Profiling",
  effects = listOf(SampleEffect.Glass),
) { navController, _ ->
  GlassProfilingSampleContent(
    state = remember { GlassProfilingState() },
    onBack = navController::navigateUp,
  )
}

internal val AndroidBlurProfiling = Sample(
  route = "blur-profiling",
  title = "Blur — Profiling",
  effects = listOf(SampleEffect.Blur),
) { navController, _ ->
  BlurProfilingSampleContent(
    state = remember { BlurProfilingState() },
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
) { _, effect ->
  ExoPlayerSample(effect)
}

actual val Samples: List<Sample> = buildList {
  addAll(CommonSamples)
  add(AndroidExoPlayer)
  add(AndroidBlurProfiling)
  add(AndroidBlurStyleChurn)
  add(AndroidGlassProfiling)
}
