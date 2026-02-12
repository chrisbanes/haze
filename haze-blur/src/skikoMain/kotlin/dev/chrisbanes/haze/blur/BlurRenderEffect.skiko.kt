// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.Shader
import dev.chrisbanes.haze.HazeBlendMode
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.createBlendRenderEffect
import dev.chrisbanes.haze.createColorFilterRenderEffect
import dev.chrisbanes.haze.createFractalNoiseShader
import dev.chrisbanes.haze.createShaderRenderEffect
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
  val source = createShaderRenderEffect(NOISE_SHADER)

  val noiseEffect = if (noiseFactor < 1f) {
    val matrix = ColorMatrix(
      1f, 0f, 0f, 0f, 0f,
      0f, 1f, 0f, 0f, 0f,
      0f, 0f, 1f, 0f, 0f,
      0f, 0f, 0f, noiseFactor, 0f,
    )

    createColorFilterRenderEffect(ColorFilter.makeMatrix(matrix), source)
  } else {
    source
  }

  return when {
    mask != null -> {
      createBlendRenderEffect(
        blendMode = HazeBlendMode.SrcIn,
        background = createShaderRenderEffect(mask),
        foreground = noiseEffect,
      )
    }

    else -> {
      noiseEffect
    }
  }
}
