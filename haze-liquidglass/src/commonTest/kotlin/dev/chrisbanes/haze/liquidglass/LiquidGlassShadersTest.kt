// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import kotlin.test.Test

class LiquidGlassShadersTest {

  @Test
  fun shader_uses_qmdeve_style_direction_field_only_for_refraction() {
    val shader = LiquidGlassShaders.LIQUID_GLASS_SKSL

    // Direction-only refraction path: analytical rounded-rect gradient with mild smoothing
    // and center bias, instead of deriving displacement direction from `surfaceGradient()`.
    assertThat(shader).contains("vec2 gradSdRoundedRect(vec2 coord, vec2 halfSize, float radius)")
    assertThat(shader).contains("vec2 axisSafeSign(vec2 value)")
    assertThat(shader).contains("vec2 safeNormalize(vec2 value, vec2 fallback)")
    assertThat(shader).contains("vec2 centerFallbackDir = vec2(1.0, 0.0);")
    assertThat(shader).contains("float gradRadius =")
    assertThat(shader).contains("depth * safeNormalize(centeredCoord, centerFallbackDir)")
    assertThat(shader).contains("vec2 refractionDir = safeNormalize(")
    assertThat(shader).contains("vec2 displacement = refractionDir * displacementMagnitude;")

    // Exact-shape magnitude path remains based on the sampled surface height.
    assertThat(shader).contains("float h = surfaceHeight(coord);")
    assertThat(shader).contains("float displacementMagnitude =")

    // Specular normal should stay on the existing sampled-height path for this experiment.
    assertThat(shader).contains("vec2 grad = surfaceGradient(coord);")
    assertThat(shader).contains("vec3 shapeNormal = normalize(vec3(-grad.x, -grad.y, 1.0));")

    // Inside corners should use the broader edge-to-arc blend from the pass-2 refinement.
    assertThat(shader).contains("float edgeBlend =")
    assertThat(shader).contains("vec2 edgeDir = safeNormalize(")
    assertThat(shader).contains("float cornerProximity =")
    assertThat(shader).contains("smoothstep(-radius, 0.0, cornerCoord.x)")
    assertThat(shader).contains("smoothstep(-radius, 0.0, cornerCoord.y)")
    assertThat(shader).contains("vec2 arcDir = safeNormalize(-cornerCoord, vec2(0.70710678, 0.70710678));")
    assertThat(shader).contains("vec2 insideDir = mix(edgeDir, arcDir, cornerProximity);")
    assertThat(shader).contains("return coordSign * safeNormalize(insideDir, edgeDir);")

    assertThat(shader).doesNotContain("float cornerBlend = smoothstep(-2.0, 2.0, cornerDelta);")
    assertThat(shader).doesNotContain("vec2 insideDir = mix(vec2(0.0, 1.0), vec2(1.0, 0.0), cornerBlend);")

    assertThat(shader).doesNotContain("float gradX = step(cornerCoord.y, cornerCoord.x);")
    assertThat(shader).doesNotContain("return coordSign * vec2(gradX, 1.0 - gradX);")
  }
}
