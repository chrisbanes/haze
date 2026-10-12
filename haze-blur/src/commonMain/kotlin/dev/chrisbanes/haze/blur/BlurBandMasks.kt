// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeProgressive

/**
 * The band masks of a layered progressive draw, kept while the gradient's endpoints and step count
 * are unchanged. A mask depends only on those and its band index, so animating intensity or easing
 * keeps them. Confined to the draw thread, like the delegate that owns it.
 */
internal class BandMasks {
  private var start: Offset = Offset.Unspecified
  private var end: Offset = Offset.Unspecified
  private var steps: Int = -1
  private var masks: Array<Brush?> = emptyArray()

  /** How many masks this cache has built. */
  var buildCount: Int = 0
    private set

  val isEmpty: Boolean get() = steps < 0

  fun maskFor(progressive: HazeProgressive.LinearGradient, steps: Int, index: Int): Brush {
    if (steps != this.steps || progressive.start != start || progressive.end != end) {
      this.steps = steps
      start = progressive.start
      end = progressive.end
      masks = arrayOfNulls(steps + 1)
    }
    return masks[index] ?: bandMask(progressive, steps, index).also {
      masks[index] = it
      buildCount++
    }
  }

  fun clear() {
    steps = -1
    masks = emptyArray()
  }
}

internal fun bandMask(
  progressive: HazeProgressive.LinearGradient,
  steps: Int,
  index: Int,
): Brush = Brush.linearGradient(
  (index - 2f) / steps to Color.Transparent,
  (index - 1f) / steps to Color.Black,
  (index + 0f) / steps to Color.Black,
  (index + 1f) / steps to Color.Transparent,
  start = progressive.start,
  end = progressive.end,
)
