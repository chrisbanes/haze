// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

internal fun HazeProgressive.asBrush(numStops: Int = 20): Brush = when (this) {
  is HazeProgressive.LinearGradient -> asBrush(numStops)
  is HazeProgressive.RadialGradient -> asBrush(numStops)
  is HazeProgressive.Brush -> brush
}

private fun HazeProgressive.LinearGradient.asBrush(numStops: Int = 20): Brush =
  Brush.linearGradient(
    colors = List(numStops) { i ->
      val x = i * 1f / (numStops - 1)
      Color.Magenta.copy(alpha = lerp(startIntensity, endIntensity, easing.transform(x)))
    },
    start = start,
    end = end,
  )

private fun HazeProgressive.RadialGradient.asBrush(numStops: Int = 20): Brush =
  Brush.radialGradient(
    colors = List(numStops) { i ->
      val x = i * 1f / (numStops - 1)
      Color.Magenta.copy(alpha = lerp(centerIntensity, radiusIntensity, easing.transform(x)))
    },
    center = center,
    radius = radius,
  )
