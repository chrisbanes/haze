// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.chrisbanes.haze.VisualEffectContext

internal actual fun BlurVisualEffect.updateDelegate(
  context: VisualEffectContext,
  drawScope: DrawScope,
): BlurVisualEffect.Delegate {
  return when {
    blurEnabled -> {
      if (delegate !is RenderEffectBlurVisualEffectDelegate) {
        RenderEffectBlurVisualEffectDelegate(this)
      } else {
        delegate
      }
    }

    else -> {
      if (delegate !is ScrimBlurVisualEffectDelegate) {
        ScrimBlurVisualEffectDelegate(this)
      } else {
        delegate
      }
    }
  }
}
