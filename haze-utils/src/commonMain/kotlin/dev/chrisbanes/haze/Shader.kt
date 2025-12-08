// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Shader

/**
 * Creates a fractal noise shader.
 */
@InternalHazeApi
public expect fun createFractalNoiseShader(
  baseFrequencyX: Float,
  baseFrequencyY: Float,
  numOctaves: Int,
  seed: Float,
): Shader

/**
 * Creates a color filter that blends with a given color.
 */
@InternalHazeApi
public expect fun createBlendColorFilter(
  color: Int,
  blendMode: HazeBlendMode,
): PlatformColorFilter
