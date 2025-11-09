// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.drawscope.DrawScope

internal actual fun BlurVisualEffect.updateDelegate(drawScope: DrawScope) {
  when {
    blurEnabled -> {
      if (delegate !is RenderEffectBlurVisualEffectDelegate) {
        delegate = RenderEffectBlurVisualEffectDelegate(this)
      }
    }

    else -> {
      if (delegate !is ScrimBlurVisualEffectDelegate) {
        delegate = ScrimBlurVisualEffectDelegate(this)
      }
    }
  }
}
