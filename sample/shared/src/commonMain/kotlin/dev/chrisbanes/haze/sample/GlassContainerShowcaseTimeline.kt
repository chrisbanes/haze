// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Immutable
import dev.chrisbanes.haze.Poko
import kotlin.math.PI
import kotlin.math.cos

internal const val SHOWCASE_LOOP_DURATION_MILLIS = 10_000

@Immutable
@Poko
internal class GlassContainerShowcaseFrame(
  /** 0 when Search sits next to the tabs, 1 when it has moved far enough to split away. */
  val searchDetachFraction: Float,
  val selecting: Boolean,
  /** 0 at the top of the drift, 1 at the bottom. Equal at the start and end of the loop. */
  val gridDrift: Float,
)

/**
 * The scripted loop, in seconds of a 10 second loop:
 *
 * - 0–1: rest.
 * - 1–2.5: Search slides away until its bridge snaps, then holds.
 * - 3–4.5: Search slides back and merges.
 * - 4.5–7.5: selection mode. The bar choreographs its members as the mode changes.
 * - 7.5–10: the bar returns to rest, with time to settle before the loop restarts.
 */
internal fun glassContainerShowcaseFrame(progress: Float): GlassContainerShowcaseFrame {
  require(progress.isFinite() && progress in 0f..1f)
  val seconds = progress * SHOWCASE_LOOP_DURATION_MILLIS / 1_000f
  val searchDetachFraction = when {
    seconds < 1f -> 0f
    seconds < 2.5f -> FastOutSlowInEasing.transform((seconds - 1f) / 1.5f)
    seconds < 3f -> 1f
    seconds < 4.5f -> 1f - FastOutSlowInEasing.transform((seconds - 3f) / 1.5f)
    else -> 0f
  }
  return GlassContainerShowcaseFrame(
    searchDetachFraction = searchDetachFraction,
    selecting = seconds >= 4.5f && seconds < 7.5f,
    gridDrift = ((1 - cos(2 * PI * progress)) / 2).toFloat(),
  )
}
