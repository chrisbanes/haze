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

val AndroidExoPlayer = Sample("exo-player", "ExoPlayer") { _, blurEnabled ->
  ExoPlayerSample(blurEnabled)
}

actual val Samples: List<Sample> = buildList {
  addAll(CommonSamples)
  add(AndroidExoPlayer)
  add(AndroidGlassProfiling)
}
