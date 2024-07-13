// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.Shader

/**
 * Heavily influenced by
 * https://www.pushing-pixels.org/2022/04/09/shader-based-render-effects-in-compose-desktop-with-skia.html
 */

internal val RUNTIME_SHADER by lazy {
  RuntimeEffect.makeForShader(SHADER_SKSL)
}

private const val SHADER_SKSL = """
  uniform shader content;
  uniform shader blur;
  uniform shader noise;

  uniform float noiseFactor;

  vec4 main(vec2 coord) {
    vec4 b = blur.eval(coord);
    vec4 n = noise.eval(coord);

    // Add noise for extra texture
    float noiseLuma = dot(n.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Calculate our overlay (noise)
    float overlay = min(1.0, noiseLuma * noiseFactor);

    // Apply the overlay (noise)
    return b + ((vec4(1.0) - b) * overlay);
  }
"""

internal val NOISE_SHADER by lazy {
  Shader.makeFractalNoise(
    baseFrequencyX = 0.45f,
    baseFrequencyY = 0.45f,
    numOctaves = 4,
    seed = 2.0f,
  )
}
