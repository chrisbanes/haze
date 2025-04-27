// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Build
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas

internal actual fun HazeEffectNode.updateBlurEffectIfNeeded(drawScope: DrawScope) {
  val hwAccelCanvas = drawScope.drawContext.canvas.nativeCanvas.isHardwareAccelerated

  val blur = resolveBlurEnabled()

  when {
    blur && Build.VERSION.SDK_INT >= 31 && hwAccelCanvas -> {
      if (blurEffect !is RenderEffectBlurEffect) {
        blurEffect = RenderEffectBlurEffect(this)
      }
    }

    blur && Build.VERSION.SDK_INT >= 29 && hwAccelCanvas -> {
      if (blurEffect !is OpenGlBlurEffect) {
        blurEffect = OpenGlBlurEffect(this)
      }
    }

    blur && !isRunningOnRobolectric() -> {
      if (blurEffect !is RenderScriptBlurEffect) {
        blurEffect = RenderScriptBlurEffect(this)
      }
    }

    else -> {
      if (blurEffect !is ScrimBlurEffect) {
        blurEffect = ScrimBlurEffect(this)
      }
    }
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
