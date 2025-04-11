// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import kotlinx.serialization.Serializable

actual val Samples: List<Sample> = buildList {
  addAll(CommonSamples)
  add(AndroidExoPlayer)
}

@Serializable
data object AndroidExoPlayer : Sample {
  override val title: String = "ExoPlayer"
  override val content: @Composable (NavHostController) -> Unit = {
    ExoPlayerSample()
  }
}
