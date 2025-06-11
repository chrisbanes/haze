// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import dev.chrisbanes.haze.effect.BlurRenderEffectVisualEffect
import dev.chrisbanes.haze.effect.ScrimVisualEffect

internal actual fun HazeEffectNode.updateBlurEffectIfNeeded(drawScope: DrawScope) {
  when {
    resolveBlurEnabled() -> {
      if (visualEffect !is BlurRenderEffectVisualEffect) {
        visualEffect = BlurRenderEffectVisualEffect(this)
      }
    }
    else -> {
      if (visualEffect !is ScrimVisualEffect) {
        visualEffect = ScrimVisualEffect(this)
      }
    }
  }
}

actual fun invalidateOnHazeAreaPreDraw(): Boolean = false

internal actual fun CompositionLocalConsumerModifierNode.requirePlatformContext(): PlatformContext {
  return PlatformContext.INSTANCE
}
