// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Immutable
import dev.chrisbanes.haze.Poko

internal const val SHOWCASE_LOOP_DURATION_MILLIS = 14_000

@Immutable
@Poko
internal class GlassContainerShowcaseFrame(val expanded: Boolean, val colorDetachFraction: Float)

/** Expand at 2s, detach at 5–6s, rejoin at 8–9s, collapse at 11s. Hold between each beat. */
internal fun glassContainerShowcaseFrame(progress: Float): GlassContainerShowcaseFrame {
  require(progress.isFinite() && progress in 0f..1f)
  val seconds = progress * SHOWCASE_LOOP_DURATION_MILLIS / 1_000f
  val detached = when {
    seconds < 5f -> 0f
    seconds < 6f -> FastOutSlowInEasing.transform(seconds - 5f)
    seconds < 8f -> 1f
    seconds < 9f -> 1f - FastOutSlowInEasing.transform(seconds - 8f)
    else -> 0f
  }
  return GlassContainerShowcaseFrame(expanded = seconds >= 2f && seconds < 11f, colorDetachFraction = detached)
}
