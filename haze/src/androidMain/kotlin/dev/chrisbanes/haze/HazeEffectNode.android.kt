// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Build
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.effect.BlurRenderEffectVisualEffect
import dev.chrisbanes.haze.effect.BlurRenderScriptVisualEffect
import dev.chrisbanes.haze.effect.ScrimVisualEffect

internal actual fun HazeEffectNode.updateBlurEffectIfNeeded(drawScope: DrawScope) {
  val canUseRenderEffect = Build.VERSION.SDK_INT >= 31 &&
    drawScope.drawContext.canvas.nativeCanvas.isHardwareAccelerated

  val blurEnabled = resolveBlurEnabled()

  if (blurEnabled && canUseRenderEffect) {
    val newBlurEffect = when (visualEffect) {
      is BlurRenderEffectVisualEffect -> visualEffect
      else -> BlurRenderEffectVisualEffect(this)
    }
    // We have a valid blur effect, so return
    visualEffect = newBlurEffect
    return
  }

  if (blurEnabled) {
    val newBlurEffect = when (visualEffect) {
      is BlurRenderScriptVisualEffect -> visualEffect
      else -> BlurRenderScriptVisualEffect.createOrNull(this)
    }
    if (newBlurEffect != null) {
      // We have a valid blur effect, so return
      visualEffect = newBlurEffect
      return
    }
  }

  // If we reach here, this is the fallback case of using a scrim
  if (visualEffect !is ScrimVisualEffect) {
    visualEffect = ScrimVisualEffect(this)
  }
}

/**
 * We need to manually invalidate if the HazeSourceNode 'draws' on certain API levels:
 *
 * - API 31: Ideally this wouldn't be necessary, but its been seen that API 31 has a few issues
 *   with RenderNodes not automatically re-painting. We workaround it by manually invalidating.
 * - Anything below API 31 does not have RenderEffect so we need to force invalidations.
 */
actual fun invalidateOnHazeAreaPreDraw(): Boolean = Build.VERSION.SDK_INT < 32

internal actual fun CompositionLocalConsumerModifierNode.requirePlatformContext(): PlatformContext {
  return currentValueOf(LocalContext)
}
