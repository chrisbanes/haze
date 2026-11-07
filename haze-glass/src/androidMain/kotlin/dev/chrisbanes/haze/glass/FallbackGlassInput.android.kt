// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import android.os.Build
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.createBlurRenderEffect

internal actual fun DrawScope.supportsFallbackGlassBlur(): Boolean =
  Build.VERSION.SDK_INT in 31..32 && drawContext.canvas.nativeCanvas.isHardwareAccelerated

internal actual fun createFallbackGlassInputEffect(style: ResolvedGlassStyle, blurred: Boolean): PlatformRenderEffect? {
  val optics = style.resolvedOptics
  return if (blurred && optics.blurRadiusPx > 0f && optics.depth > 0f) {
    createGlassDepthInputRenderEffect(
      sharp = null,
      blur = createBlurRenderEffect(optics.blurRadiusPx, optics.blurRadiusPx, TileMode.Clamp),
      depth = optics.depth,
    )
  } else {
    null
  }
}
