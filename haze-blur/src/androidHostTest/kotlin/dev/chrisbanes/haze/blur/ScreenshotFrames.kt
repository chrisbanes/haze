// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.PixelMap
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.test.ScreenshotUiTest
import kotlin.math.abs

/** Settles the composition, then draws a frame. The effect renderers draw at the capture. */
internal fun ScreenshotUiTest.frame(): PixelMap {
  waitForIdle()
  return captureRootPixels()
}

/** The largest per-channel difference between two equally sized frames, sampled every third pixel. */
internal fun maxDifference(a: PixelMap, b: PixelMap): Float {
  assertThat(a.width).isEqualTo(b.width)
  assertThat(a.height).isEqualTo(b.height)
  var max = 0f
  for (y in 0 until a.height step 3) {
    for (x in 0 until a.width step 3) {
      val first = a[x, y]
      val second = b[x, y]
      max = maxOf(
        max,
        abs(first.red - second.red),
        abs(first.green - second.green),
        abs(first.blue - second.blue),
        abs(first.alpha - second.alpha),
      )
    }
  }
  return max
}
