// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.effect

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.asBrush
import dev.chrisbanes.haze.boostForFallback
import dev.chrisbanes.haze.withAlpha

@OptIn(ExperimentalHazeApi::class)
internal class ScrimBlurVisualEffectDelegate(
  val blurVisualEffect: BlurVisualEffect,
) : BlurVisualEffect.Delegate {
  override fun DrawScope.draw() {
    val scrimTint = blurVisualEffect.fallbackTint.takeIf { it.isSpecified }
      ?: blurVisualEffect.tints.firstOrNull()
        ?.boostForFallback(blurVisualEffect.blurRadius.takeOrElse { 0.dp })
      ?: return

    withAlpha(alpha = blurVisualEffect.alpha, node = blurVisualEffect.requireNode()) {
      drawScrim(
        tint = scrimTint,
        node = blurVisualEffect.requireNode(),
        mask = blurVisualEffect.mask ?: blurVisualEffect.progressive?.asBrush(),
      )
    }
  }
}
