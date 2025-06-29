// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.asBrush
import dev.chrisbanes.haze.drawScrim
import dev.chrisbanes.haze.withAlpha

@OptIn(ExperimentalHazeApi::class)
internal class ScrimBlurVisualEffectDelegate(
  val blurVisualEffect: BlurVisualEffect,
) : BlurVisualEffect.Delegate {
  override fun DrawScope.draw() = with(blurVisualEffect) {
    val scrimTint = fallbackTint.takeIf { it.isSpecified }
      ?: tints.firstOrNull()?.boostForFallback(blurRadius.takeOrElse { 0.dp })
      ?: return

    val node = requireNode()
    withAlpha(alpha = alpha, node = node) {
      drawScrim(
        tint = scrimTint,
        node = node,
        mask = mask ?: progressive?.asBrush(),
      )
    }
  }
}

private fun HazeTint.boostForFallback(blurRadius: Dp): HazeTint {
  if (brush != null) {
    // We can't boost brush tints
    return this
  }

  // For color, we can boost the alpha
  val resolved = blurRadius.takeOrElse { HazeDefaults.blurRadius }
  val boosted = color.boostAlphaForBlurRadius(resolved)
  return copy(color = boosted)
}

/**
 * In this implementation, the only tool we have is translucency.
 */
private fun Color.boostAlphaForBlurRadius(blurRadius: Dp): Color {
  // We treat a blur radius of 72.dp as near 'opaque', and linearly boost using that
  val factor = 1 + (blurRadius.value / 72)
  return copy(alpha = (alpha * factor).coerceAtMost(1f))
}
