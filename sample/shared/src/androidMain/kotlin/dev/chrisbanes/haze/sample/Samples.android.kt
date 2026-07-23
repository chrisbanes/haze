// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import kotlinx.serialization.Serializable

actual val Samples: List<Sample> = buildList {
  addAll(CommonSamples)
  add(AndroidExoPlayer)
  add(AndroidGlassProfiling)
}

@Serializable
data object AndroidGlassProfiling : Sample {
  override val title: String = "Glass — Profiling"

  @Composable
  override fun Content(navController: NavHostController, blurEnabled: Boolean) {
    GlassProfilingSampleContent(
      state = remember { GlassProfilingState() },
      onBack = navController::navigateUp,
    )
  }
}

@Serializable
data object AndroidExoPlayer : Sample {
  override val title: String = "ExoPlayer"

  @Composable
  override fun Content(navController: NavHostController, blurEnabled: Boolean) {
    ExoPlayerSample(blurEnabled)
  }
}
