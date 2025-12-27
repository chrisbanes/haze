// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.Shader
import dev.chrisbanes.haze.HazeBlendMode
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.createBlendImageFilter
import dev.chrisbanes.haze.createBlurImageFilter
import dev.chrisbanes.haze.createColorFilterImageFilter
import dev.chrisbanes.haze.createFractalNoiseShader
import dev.chrisbanes.haze.createShaderImageFilter
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix

private val NOISE_SHADER by lazy(LazyThreadSafetyMode.NONE) {
  createFractalNoiseShader(
    baseFrequencyX = 0.45f,
    baseFrequencyY = 0.45f,
    numOctaves = 4,
    seed = 2.0f,
  )
}

internal actual fun createNoiseEffect(
  context: PlatformContext,
  noiseFactor: Float,
  mask: Shader?,
  scale: Float,
): PlatformRenderEffect {
  val source = createShaderImageFilter(NOISE_SHADER)

  val noiseEffect = if (noiseFactor < 1f) {
    val matrix = ColorMatrix(
      1f, 0f, 0f, 0f, 0f,
      0f, 1f, 0f, 0f, 0f,
      0f, 0f, 1f, 0f, 0f,
      0f, 0f, 0f, noiseFactor, 0f,
    )

    createColorFilterImageFilter(ColorFilter.makeMatrix(matrix), source)
  } else {
    source
  }

  return when {
    mask != null -> {
      createBlendImageFilter(
        blendMode = HazeBlendMode.SrcIn,
        background = createShaderImageFilter(mask),
        foreground = noiseEffect,
      )
    }

    else -> {
      noiseEffect
    }
  }
}

internal actual fun createBlurRenderEffect(
  blurRadiusPx: Float,
  params: RenderEffectParams,
): PlatformRenderEffect {
  return createBlurImageFilter(radiusX = blurRadiusPx, radiusY = blurRadiusPx, tileMode = params.blurTileMode)
}
