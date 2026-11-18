// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

internal fun adaptiveEdgeShader(continuous: Boolean = false, merged: Boolean = false): String = GlassShaders.buildRim(merged = merged, continuous = continuous).replace(
  "vec4 main(vec2 coord) {",
  """
      uniform float rimGain;
      uniform float outlineGain;
      vec4 adaptiveEdge(vec4 edge, float multiplier) {
        float adaptiveAlpha = clamp(edge.a * multiplier, 0.0, 1.0);
        return edge.a > 0.0 ? edge * (adaptiveAlpha / edge.a) : vec4(0.0);
      }
      vec4 main(vec2 coord) {
      vec4 source = content.eval(clamp(coord, vec2(0.5), sampleSize - vec2(0.5)));
      vec3 linear = source.a > 0.0001 ? toLinearSrgb(source.rgb / source.a) : vec3(0.5);
      float y = dot(linear, vec3(0.2126, 0.7152, 0.0722));
      float luminanceStrength = source.a > 0.0001 ? 0.5 + y : 1.0;
  """.trimIndent(),
).replace(
  "return outline;",
  "return adaptiveEdge(outline, mix(1.0, luminanceStrength, clamp(outlineGain, 0.0, 1.0)));",
).replace(
  "return rim + outline * (1.0 - rim.a);",
  """
      rim = adaptiveEdge(rim, mix(1.0, luminanceStrength, clamp(rimGain, 0.0, 1.0)));
      outline = adaptiveEdge(outline, mix(1.0, luminanceStrength, clamp(outlineGain, 0.0, 1.0)));
      return rim + outline * (1.0 - rim.a);
  """.trimIndent(),
)
