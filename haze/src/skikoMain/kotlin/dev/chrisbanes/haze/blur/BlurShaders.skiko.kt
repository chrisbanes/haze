// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import dev.chrisbanes.haze.unsynchronizedLazy
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.Shader

/**
 * Heavily influenced by
 * https://www.pushing-pixels.org/2022/04/09/shader-based-render-effects-in-compose-desktop-with-skia.html
 */

internal val RUNTIME_SHADER: RuntimeEffect by unsynchronizedLazy {
  RuntimeEffect.makeForShader(SHADER_SKSL)
}

private const val SHADER_SKSL = """
  uniform shader content;
  uniform shader blur;
  uniform shader noise;

  uniform float noiseFactor;

  half4 main(vec2 coord) {
    half4 b = blur.eval(coord);
    half4 n = noise.eval(coord);

    // Add noise for extra texture
    float noiseLuma = dot(n.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Calculate our overlay (noise)
    float overlay = saturate(noiseLuma * noiseFactor);

    // Apply the overlay (noise)
    return mix(b, half4(1.0), overlay);
  }
"""

internal val VERTICAL_BLUR_SHADER: RuntimeEffect by unsynchronizedLazy {
  RuntimeEffect.makeForShader(VERTICAL_BLUR_SKSL)
}
internal val HORIZONTAL_BLUR_SHADER: RuntimeEffect by unsynchronizedLazy {
  RuntimeEffect.makeForShader(HORIZONTAL_BLUR_SKSL)
}

internal val NOISE_SHADER by unsynchronizedLazy {
  Shader.makeFractalNoise(
    baseFrequencyX = 0.45f,
    baseFrequencyY = 0.45f,
    numOctaves = 4,
    seed = 2.0f,
  )
}
