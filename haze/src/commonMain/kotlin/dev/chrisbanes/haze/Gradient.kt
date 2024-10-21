// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

internal fun HazeProgressive.LinearGradient.asBrush(numStops: Int = 20): Brush {
  return Brush.linearGradient(
    colors = List(numStops) { i ->
      val x = i * 1f / (numStops - 1)
      Color.Black.copy(alpha = lerp(startIntensity, endIntensity, easing.transform(x)))
    },
    start = start,
    end = end,
  )
}
