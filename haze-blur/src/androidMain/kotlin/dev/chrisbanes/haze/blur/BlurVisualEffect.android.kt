// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.os.Build
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas

internal actual fun BlurVisualEffect.updateDelegate(drawScope: DrawScope) {
  val canUseRenderEffect = Build.VERSION.SDK_INT >= 31 &&
    drawScope.drawContext.canvas.nativeCanvas.isHardwareAccelerated

  val blurEnabled = blurEnabled

  if (blurEnabled && canUseRenderEffect) {
    val newBlurEffect = when (delegate) {
      is RenderEffectBlurVisualEffectDelegate -> delegate
      else -> RenderEffectBlurVisualEffectDelegate(this)
    }
    // We have a valid blur effect, so return
    delegate = newBlurEffect
    return
  }

  if (blurEnabled) {
    val newDelegate = when (delegate) {
      is RenderScriptBlurVisualEffectDelegate -> delegate
      else -> RenderScriptBlurVisualEffectDelegate.createOrNull(this)
    }
    if (newDelegate != null) {
      // We have a valid blur effect, so return
      delegate = newDelegate
      return
    }
  }

  // If we reach here, this is the fallback case of using a scrim
  if (delegate !is ScrimBlurVisualEffectDelegate) {
    delegate = ScrimBlurVisualEffectDelegate(this)
  }
}
