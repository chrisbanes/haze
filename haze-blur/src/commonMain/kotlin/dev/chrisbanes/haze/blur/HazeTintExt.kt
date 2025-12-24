// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.takeOrElse

internal fun HazeTint.boostForFallback(blurRadius: Dp): HazeTint = when (this) {
  is HazeTint.Brush -> {
    // We can't boost brush tints
    this
  }
  is HazeTint.Color -> {
    // For color, we can boost the alpha
    val resolved = blurRadius.takeOrElse { HazeBlurDefaults.blurRadius }
    val boosted = color.boostAlphaForBlurRadius(resolved)
    copy(color = boosted)
  }
}

/**
 * In this implementation, the only tool we have is translucency.
 */
private fun Color.boostAlphaForBlurRadius(blurRadius: Dp): Color {
  // We treat a blur radius of 72.dp as near 'opaque', and linearly boost using that
  val factor = 1 + (blurRadius.value / 72)
  return copy(alpha = (alpha * factor).coerceAtMost(1f))
}
