// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import android.graphics.BlendModeColorFilter
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Shader

// Fractal noise SKSL for Android
private const val FRACTAL_NOISE_SKSL = """
uniform float baseFrequencyX;
uniform float baseFrequencyY;
uniform int numOctaves;
uniform float seed;

// Simple hash function
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// Value noise
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Fractal Brownian motion
float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        if (i >= numOctaves) break;
        value += amplitude * noise(p);
        p *= 2.0;
        amplitude *= 0.5;
    }
    return value;
}

vec4 main(vec2 coord) {
    vec2 p = coord * vec2(baseFrequencyX, baseFrequencyY) + seed;
    float n = fbm(p);
    return vec4(n, n, n, 1.0);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@InternalHazeApi
public actual fun createFractalNoiseShader(
  baseFrequencyX: Float,
  baseFrequencyY: Float,
  numOctaves: Int,
  seed: Float,
): Shader {
  return RuntimeShader(FRACTAL_NOISE_SKSL).apply {
    setFloatUniform("baseFrequencyX", baseFrequencyX)
    setFloatUniform("baseFrequencyY", baseFrequencyY)
    setIntUniform("numOctaves", numOctaves)
    setFloatUniform("seed", seed)
  }
}

@RequiresApi(Build.VERSION_CODES.Q)
@InternalHazeApi
public actual fun createBlendColorFilter(
  color: Int,
  blendMode: HazeBlendMode,
): PlatformColorFilter = BlendModeColorFilter(color, blendMode.toAndroidBlendMode())
