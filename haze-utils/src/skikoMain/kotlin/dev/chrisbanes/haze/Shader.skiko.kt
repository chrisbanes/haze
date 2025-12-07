// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.Shader

@InternalHazeApi
public actual fun createFractalNoiseShader(
  baseFrequencyX: Float,
  baseFrequencyY: Float,
  numOctaves: Int,
  seed: Float,
): androidx.compose.ui.graphics.Shader = Shader.makeFractalNoise(
  baseFrequencyX = baseFrequencyX,
  baseFrequencyY = baseFrequencyY,
  numOctaves = numOctaves,
  seed = seed,
)

@InternalHazeApi
public actual fun createBlendColorFilter(
  color: Int,
  blendMode: HazeBlendMode,
): PlatformColorFilter {
  return ColorFilter.makeBlend(color, blendMode.toSkiaBlendMode())
}
