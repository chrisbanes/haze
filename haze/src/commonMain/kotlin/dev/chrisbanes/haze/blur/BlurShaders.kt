// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import dev.chrisbanes.haze.unsynchronizedLazy

/**
 * Adapted from https://www.shadertoy.com/view/Mtl3Rj
 *
 * Uses the GPU-friendly optimizations, see
 * https://www.rastergrid.com/blog/2010/09/efficient-gaussian-blur-with-linear-sampling
 */
private fun makeBlurSksl(vertical: Boolean): String = """
  uniform shader content;
  uniform float blurRadius;
  uniform vec4 crop;
  uniform shader mask;

  const half maxRadius = 150.0;

  float gaussian(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma));
  }

  vec4 blur(vec2 coord, float radius) {
    // Truncate the radius
    half r = floor(radius);

    // Need to use float and vec here for higher precision, otherwise we see
    // visually clipping on certain devices (Samsung for example)
    // https://github.com/chrisbanes/haze/issues/520
    float sigma = max(radius / 2.0, 1.0);
    float weightSum = 1.0;
    vec4 result = content.eval(coord);

    // We need to use a constant max size Skia to know the size of the program. We use a large
    // number, along with a break
    for (half i = 1.0; i < maxRadius; i += 2.0) {
      // i is always odd.
      // The algorithm needs pixels exist at the offset of [i] (odd) and [i + 1] (even).
      // If radius r is even (i > r), we can break safely here, as all the pixels have been calculated;
      // otherwise (i == r) we need to calculate the pixel at the offset of [r], so break in advance.
      if (i >= r) { break; }

      float weightL = gaussian(i, sigma);
      float weightH = gaussian(i + 1.0, sigma);
      float weight = weightL + weightH;
      vec2 offset = ${if (vertical) "vec2(0.0, i + weightH / weight)" else "vec2(i + weightH / weight, 0.0)"};

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

    // Check if radius is odd
    if (r < maxRadius && mod(r, 2.0) == 1.0) {
      float weight = gaussian(r, sigma);
      vec2 offset = ${if (vertical) "vec2(0.0, r)" else "vec2(r, 0.0)"};

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
    // Offset the coord for the mask, but coerce it to be at least 0, 0
    vec2 maskCoord = max(coord - crop.xy, vec2(0.0, 0.0));
    float intensity = mask.eval(maskCoord).a;

    return blur(coord, mix(0.0, blurRadius, intensity));
  }
"""

internal val VERTICAL_BLUR_SKSL: String by unsynchronizedLazy {
  makeBlurSksl(true)
}
internal val HORIZONTAL_BLUR_SKSL: String by unsynchronizedLazy {
  makeBlurSksl(false)
}
