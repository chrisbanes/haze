// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

actual val Samples: List<Sample> = buildList {
  addAll(CommonSamples)
  add(Sample("ExoPlayer") { ExoPlayerSample(it) })
}
