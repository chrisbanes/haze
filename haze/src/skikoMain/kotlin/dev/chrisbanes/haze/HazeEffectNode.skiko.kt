// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.drawscope.DrawScope

internal actual fun HazeEffectNode.updateBlurEffectIfNeeded(drawScope: DrawScope) {
  when {
    resolveBlurEnabled() -> {
      if (blurEffect !is RenderEffectBlurEffect) {
        blurEffect = RenderEffectBlurEffect(this)
      }
    }
    else -> {
      if (blurEffect !is ScrimBlurEffect) {
        blurEffect = ScrimBlurEffect(this)
      }
    }
  }
}

internal actual fun invalidateOnHazeAreaPreDraw(): Boolean = false
