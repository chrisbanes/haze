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
import dev.chrisbanes.haze.MutableRuntimeShaderRenderEffect
import dev.chrisbanes.haze.PlatformRenderEffect

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@InternalHazeApi
internal actual fun createGlassBlurRenderEffect(
  horizontal: Boolean,
  progressive: Boolean,
): MutableRuntimeShaderRenderEffect = createSharedGlassBlurRenderEffect(horizontal, progressive)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createGlassBlurPrefilterRenderEffect(): MutableRuntimeShaderRenderEffect =
  createSharedGlassBlurPrefilterRenderEffect()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createGlassOpticalRenderEffect(): MutableRuntimeShaderRenderEffect =
  createSharedGlassOpticalRenderEffect()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createRefractionDetailRenderEffect(): MutableRuntimeShaderRenderEffect =
  createSharedRefractionDetailRenderEffect()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createGlassRimRenderEffect(): MutableRuntimeShaderRenderEffect =
  createSharedGlassRimRenderEffect()

@RequiresApi(Build.VERSION_CODES.S)
internal actual fun createGlassDepthInputRenderEffect(
  blur: PlatformRenderEffect?,
  depth: Float,
): PlatformRenderEffect? {
  if (blur == null || depth <= 0.0001f) return null
  if (depth >= 0.9999f) return blur

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

  return RenderEffect.createBlendModeEffect(
    scaledInput(1f - depth),
    scaledInput(depth, blur),
    BlendMode.PLUS,
  )
}

internal actual val supportsMergedGlassDepthOpticalLayer: Boolean = true

internal actual val supportsSharedGlassBlur: Boolean = true

internal actual val supportsFusedGlassRenderEffect: Boolean = true
