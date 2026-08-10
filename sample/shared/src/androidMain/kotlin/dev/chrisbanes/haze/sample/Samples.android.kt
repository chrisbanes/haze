// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.remember

internal val AndroidGlassProfiling = Sample(
  route = "glass-profiling",
  title = "Glass — Profiling",
) { navController, _ ->
  GlassProfilingSampleContent(
    state = remember { GlassProfilingState() },
    onBack = navController::navigateUp,
  )
}

internal val AndroidBlurProfiling = Sample(
  route = "blur-profiling",
  title = "Blur — Profiling",
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
) { navController, blurEnabled ->
  ScaffoldSample(
    navController = navController,
    blurEnabled = blurEnabled,
    mode = ScaffoldSampleMode.StyleChurn,
  )
}

val AndroidExoPlayer = Sample("exo-player", "ExoPlayer") { _, blurEnabled ->
  ExoPlayerSample(blurEnabled)
}

actual val Samples: List<Sample> = buildList {
  addAll(CommonSamples)
  add(AndroidExoPlayer)
  add(AndroidBlurProfiling)
  add(AndroidBlurStyleChurn)
  add(AndroidGlassProfiling)
}
