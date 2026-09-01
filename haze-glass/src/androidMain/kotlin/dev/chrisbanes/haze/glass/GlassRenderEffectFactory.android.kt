// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import android.graphics.BlendMode
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.os.Build
import androidx.annotation.RequiresApi
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformRenderEffect

@RequiresApi(Build.VERSION_CODES.S)
internal actual fun createGlassDepthInputRenderEffect(
  sharp: PlatformRenderEffect?,
  blur: PlatformRenderEffect?,
  depth: Float,
): PlatformRenderEffect? {
  if (blur == null || depth <= 0.0001f) return null
  if (depth >= 0.9999f) return blur

  return wrapGlassRuntimeEffectConstruction {
    fun scaledInput(scale: Float, input: RenderEffect? = null): RenderEffect {
      val matrix = ColorMatrix().apply {
        // Color filters operate on straight RGB and premultiply the result. Scaling both RGB and
        // alpha would therefore apply the factor twice to premultiplied color. Scale alpha only to
        // match Canvas layer alpha, which scales premultiplied RGBA once.
        setScale(1f, 1f, 1f, scale)
      }
      val filter = ColorMatrixColorFilter(matrix)
      return if (input != null) {
        RenderEffect.createColorFilterEffect(filter, input)
      } else {
        RenderEffect.createColorFilterEffect(filter)
      }
    }

    RenderEffect.createBlendModeEffect(
      scaledInput(1f - depth, sharp),
      scaledInput(depth, blur),
      BlendMode.PLUS,
    )
  }
}

internal actual val supportsFusedGlassRenderEffect: Boolean = true
