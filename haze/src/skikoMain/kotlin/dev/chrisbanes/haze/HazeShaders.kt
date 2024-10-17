// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.Shader

/**
 * Heavily influenced by
 * https://www.pushing-pixels.org/2022/04/09/shader-based-render-effects-in-compose-desktop-with-skia.html
 */

internal val RUNTIME_SHADER: RuntimeEffect by lazy {
  RuntimeEffect.makeForShader(SHADER_SKSL)
}

private const val SHADER_SKSL = """
  uniform shader content;
  uniform shader blur;
  uniform shader noise;
  uniform shader progressive;

  uniform float noiseFactor;

  vec4 main(vec2 coord) {
    float intensity = progressive.eval(coord).a;

    vec4 b = blur.eval(coord) * intensity;
    vec4 n = noise.eval(coord);

    // Add noise for extra texture
    float noiseLuma = dot(n.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Calculate our overlay (noise)
    float overlay = min(1.0, noiseLuma * noiseFactor);

    // Apply the overlay (noise)
    return b + ((vec4(1.0) - b) * overlay);
  }
"""

internal val BLUR_SHADER: RuntimeEffect by lazy {
  RuntimeEffect.makeForShader(BLUR_SKSL)
}

private const val BLUR_SKSL = """
  // uniform shader content;

  const float blurRadius = 24;
  // 0 or 1 to indicate vertical or horizontal pass
  const int horizontalPass = 2;
  // The sigma value for the gaussian function: higher value means more blur
  const float sigma = 10.0;

  const float pi = 3.14159265;

  vec4 main(vec2 coord) {
    float numBlurPixelsPerSide = blurRadius;

    vec2 blurMultiplyVec = 0 < horizontalPass ? vec2(1.0, 0.0) : vec2(0.0, 1.0);

    // Incremental Gaussian Coefficent Calculation (See GPU Gems 3 pp. 877 - 889)
    vec3 incrementalGaussian;
    incrementalGaussian.x = 1.0 / (sqrt(2.0 * pi) * sigma);
    incrementalGaussian.y = exp(-0.5 / (sigma * sigma));
    incrementalGaussian.z = incrementalGaussian.y * incrementalGaussian.y;

    vec4 avgValue = vec4(0.0, 0.0, 0.0, 0.0);
    float coefficientSum = 0.0;

    // Take the central sample first...
    avgValue += iImage1.eval(coord) * incrementalGaussian.x;
    coefficientSum += incrementalGaussian.x;
    incrementalGaussian.xy *= incrementalGaussian.yz;

    // Go through the remaining 8 vertical samples (4 on each side of the center)
    for (float i = 1.0; i <= blurRadius; i++) {
      avgValue += iImage1.eval(coord - i * blurMultiplyVec) * incrementalGaussian.x;
      avgValue += iImage1.eval(coord + i * blurMultiplyVec) * incrementalGaussian.x;
      coefficientSum += 2.0 * incrementalGaussian.x;
      incrementalGaussian.xy *= incrementalGaussian.yz;
    }

    return avgValue / coefficientSum;
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
