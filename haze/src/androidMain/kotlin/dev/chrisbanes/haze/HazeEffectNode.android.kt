// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Build
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas

internal actual fun HazeEffectNode.updateBlurEffectIfNeeded(drawScope: DrawScope) {
  val canUseRenderEffect = Build.VERSION.SDK_INT >= 31 &&
    drawScope.drawContext.canvas.nativeCanvas.isHardwareAccelerated

  val blurEnabled = resolveBlurEnabled()

  if (blurEnabled && canUseRenderEffect) {
    val newBlurEffect = when (blurEffect) {
      is RenderEffectBlurEffect -> blurEffect
      else -> RenderEffectBlurEffect(this)
    }
    // We have a valid blur effect, so return
    blurEffect = newBlurEffect
    return
  }

  if (blurEnabled) {
    val newBlurEffect = when (blurEffect) {
      is RenderScriptBlurEffect -> blurEffect
      else -> RenderScriptBlurEffect.createOrNull(this)
    }
    if (newBlurEffect != null) {
      // We have a valid blur effect, so return
      blurEffect = newBlurEffect
      return
    }
  }

  // If we reach here, this is the fallback case of using a scrim
  if (blurEffect !is ScrimBlurEffect) {
    blurEffect = ScrimBlurEffect(this)
  }
}

/**
 * We need to manually invalidate if the HazeSourceNode 'draws' on certain API levels:
 *
 * - API 31: Ideally this wouldn't be necessary, but its been seen that API 31 has a few issues
 *   with RenderNodes not automatically re-painting. We workaround it by manually invalidating.
 * - Anything below API 31 does not have RenderEffect so we need to force invalidations.
 */
internal actual fun invalidateOnHazeAreaPreDraw(): Boolean = Build.VERSION.SDK_INT < 32
