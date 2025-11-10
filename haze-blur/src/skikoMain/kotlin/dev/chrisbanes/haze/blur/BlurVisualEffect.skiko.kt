// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.chrisbanes.haze.VisualEffectContext

internal actual fun BlurVisualEffect.updateDelegate(
  drawScope: DrawScope,
  context: VisualEffectContext,
) {
  when {
    blurEnabled -> {
      if (delegate !is RenderEffectBlurVisualEffectDelegate) {
        updateDelegate(RenderEffectBlurVisualEffectDelegate(this), context)
      }
    }

    else -> {
      if (delegate !is ScrimBlurVisualEffectDelegate) {
        updateDelegate(ScrimBlurVisualEffectDelegate(this), context)
      }
    }
  }
}
