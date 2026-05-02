// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.os.Build
import androidx.compose.ui.platform.LocalView
import dev.chrisbanes.haze.VisualEffectContext

internal actual fun BlurVisualEffect.updateDelegate(context: VisualEffectContext): BlurVisualEffect.Delegate {
  val canUseRenderEffect = canUseRenderEffect(
    sdkInt = Build.VERSION.SDK_INT,
    isHardwareAccelerated = context.currentValueOf(LocalView).isHardwareAccelerated,
  )

  val blurEnabled = blurEnabled

  if (blurEnabled && canUseRenderEffect) {
    return when (delegate) {
      is RenderEffectBlurVisualEffectDelegate -> delegate
      else -> RenderEffectBlurVisualEffectDelegate(this)
    }
  }

  if (blurEnabled) {
    val newDelegate = when (delegate) {
      is RenderScriptBlurVisualEffectDelegate -> delegate
      else -> RenderScriptBlurVisualEffectDelegate.createOrNull(this, context)
    }
    if (newDelegate != null) {
      return newDelegate
    }
  }

  // If we reach here, this is the fallback case of using a scrim
  return if (delegate is ScrimBlurVisualEffectDelegate) {
    delegate
  } else {
    ScrimBlurVisualEffectDelegate(this)
  }
}
