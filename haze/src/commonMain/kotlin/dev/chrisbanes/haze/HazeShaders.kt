// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import kotlin.math.PI

/**
 * Adapted from https://www.shadertoy.com/view/Mtl3Rj
 */
internal const val BLUR_SKSL = """
  uniform shader content;
  // 0 for horizontal pass, 1 for vertical
  uniform int direction;
  uniform float blurRadius;
  uniform vec4 crop;
  uniform shader mask;

  const int maxRadius = 150;
  const half2 directionHorizontal = half2(1.0, 0.0);
  const half2 directionVertical = half2(0.0, 1.0);

  float gaussian(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma)) / (2.0 * ${PI.toFloat()} * sigma * sigma);
  }

  vec4 blur(vec2 coord, float radius) {
    half2 directionVec = direction == 0 ? directionHorizontal : directionVertical;

    // Need to use float and vec here for higher precision, otherwise  we see
    // visually clipping on certain devices (Samsung for example)
    // https://github.com/chrisbanes/haze/issues/520
    float sigma = max(radius / 2.0, 1.0);
    float weight = gaussian(0.0, sigma);
    vec4 result = weight * content.eval(coord);
    float weightSum = weight;

    // We need to use a constant max size Skia to know the size of the program. We use a large
    // number, along with a break
    for (int i = 1; i <= maxRadius; i++) {
      half halfI = half(i);
      if (halfI > radius) { break; }

      float weight = gaussian(halfI, sigma);
      vec2 offset = halfI * directionVec;

      vec2 newCoord = coord - offset;
      if (newCoord.x >= crop[0] && newCoord.y >= crop[1]) {
        result += weight * content.eval(newCoord);
        weightSum += weight;
      }

      newCoord = coord + offset;
      if (newCoord.x < crop[2] && newCoord.y < crop[3]) {
        result += weight * content.eval(newCoord);
        weightSum += weight;
      }
    }

    return result / weightSum;
  }

  vec4 main(vec2 coord) {
    float intensity = mask.eval(coord).a;
    return blur(coord, mix(0.0, blurRadius, intensity));
  }
"""
