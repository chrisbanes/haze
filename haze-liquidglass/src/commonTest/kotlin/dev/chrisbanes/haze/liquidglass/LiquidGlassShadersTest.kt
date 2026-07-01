// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import kotlin.test.Test

class LiquidGlassShadersTest {

  @Test
  fun shader_uses_analytical_gradient_direction_for_refraction() {
    val shader = LiquidGlassShaders.build()

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

  @Test
  fun shader_contains_flat_interior_early_out() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = true)
    assertThat(shader).contains("if (distToEdge >= refractionZone)")
    assertThat(shader).contains("return vec4(finalColor, base.a);")
  }

  @Test
  fun shader_contains_corner_weighted_dispersion() {
    val shader = LiquidGlassShaders.build()
    assertThat(shader).contains("float cornerWeight = abs((centeredCoord.x * centeredCoord.y) / max(halfSize.x * halfSize.y, 0.001));")
    assertThat(shader).contains("* cornerWeight;")
  }

  @Test
  fun shader_contains_color_grading() {
    val shader = LiquidGlassShaders.build()
    assertThat(shader).contains("uniform float contrast;")
    assertThat(shader).contains("uniform float whitePoint;")
    assertThat(shader).contains("uniform float chromaMultiplier;")
    assertThat(shader).contains("vec4 applyColorGrading(vec4 color)")
    assertThat(shader).contains("clamp((color.rgb - 0.5) * (1.0 + contrast) + 0.5, 0.0, 1.0)")
  }

  @Test
  fun shader_exposes_magic_numbers_as_uniforms() {
    val shader = LiquidGlassShaders.build()
    assertThat(shader).doesNotContain("* 12.0;")
    assertThat(shader).contains("uniform float refractionScale;")
    assertThat(shader).contains("* refractionScale")
    assertThat(shader).doesNotContain("mix(shapeNormal, contentNormal, 0.15)")
    assertThat(shader).contains("mix(shapeNormal, contentNormal, contentNormalBlend)")
    assertThat(shader).contains("uniform float specularExponent;")
    assertThat(shader).contains("uniform float fresnelExponent;")
  }

  @Test
  fun shader_uses_smootherstep_edge_mask() {
    val shader = LiquidGlassShaders.build()
    assertThat(shader).contains("return smootherstep(e);")
  }

  @Test
  fun shader_uses_linear_space_saturation() {
    val shader = LiquidGlassShaders.build()
    assertThat(shader).contains("srgbToLinear")
    assertThat(shader).contains("linearToSrgb")
    assertThat(shader).contains("vec3(0.2126, 0.7152, 0.0722)")
  }

  @Test
  fun shader_flat_interior_has_ambient_lighting() {
    val shader = LiquidGlassShaders.build()
    assertThat(shader).contains("fresnelExponent")
    assertThat(shader).contains("vec3 normal = computeContentNormal(coord);")
  }

  @Test
  fun shader_flat_interior_skips_surface_gradient() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = true)
    val earlyOutSection = shader.substringAfter("if (distToEdge >= refractionZone)")
      .substringBefore("return vec4(finalColor, base.a);")
    assertThat(earlyOutSection).doesNotContain("surfaceGradient(coord)")
  }

  @Test
  fun shader_flat_interior_mixesBlurredContentForDepth() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = true)
    val earlyOutSection = shader.substringAfter("if (distToEdge >= refractionZone)")
      .substringBefore("return vec4(finalColor, base.a);")

    assertThat(earlyOutSection).contains("vec4 blurred = sampleBlurredContent(coord);")
    assertThat(earlyOutSection).contains("mix(base.rgb, blurred.rgb, clamp(depth, 0.0, 1.0))")
  }

  @Test
  fun shader_singleInputVariantDoesNotApproximateBlurredContent() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = false)

    assertThat(shader).doesNotContain("uniform shader blurredContent;")
    assertThat(shader).doesNotContain("uniform float blurRadius;")
    assertThat(shader).doesNotContain("sampleBlurredContent")
    assertThat(shader).doesNotContain("radius * 0.5")
    assertThat(shader).doesNotContain("radius * 0.35")
    assertThat(shader).doesNotContain("vec2 farAxis")
    assertThat(shader).contains("float overlayAlpha =")
  }

  @Test
  fun shader_singleInputVariantPremultipliesOverlayColor() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = false)

    assertThat(shader).contains("return vec4(finalColor * overlayAlpha, base.a * overlayAlpha);")
    assertThat(shader).contains("return vec4(overlayColor, base.a * overlayAlpha);")
  }

  @Test
  fun shader_multiInputVariantSamplesBlurredContentSeparately() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = true)

    assertThat(shader).contains("uniform shader blurredContent;")
    assertThat(shader).contains("return blurredContent.eval(clampCoord(coord));")
    assertThat(shader).contains("vec4 blurred = sampleBlurredContent(refractCoord);")
  }

  @Test
  fun shader_multiInputVariantUsesBlurredRefractionForDepth() {
    val shader = LiquidGlassShaders.build(hasBlurredContent = true)

    val refractedSection = shader
      .substringAfter("vec4 refracted = sampleChroma(refractCoord, chromaOffset);")
      .substringBefore("vec2 grad = surfaceGradient(coord);")
    val returnSection = shader
      .substringAfter("vec3 tinted = mix(graded, tintColor.rgb, tintColor.a);")
      .substringBefore("return vec4(finalColor, base.a) * edge;")

    assertThat(refractedSection).contains("vec4 blurred = sampleBlurredContent(refractCoord);")
    assertThat(refractedSection).contains(
      "vec3 refractedColor = applyColorGrading(vec4(mix(refracted.rgb, blurred.rgb, clamp(depth, 0.0, 1.0)), 1.0)).rgb;",
    )
    assertThat(returnSection).contains("mix(tinted, refractedColor, refractionStrength)")
  }

  @Test
  fun shader_refractedColorPathAppliesColorGrading() {
    val overlayShader = LiquidGlassShaders.build(hasBlurredContent = false)
    val dualInputShader = LiquidGlassShaders.build(hasBlurredContent = true)
    val singleInputShader = LiquidGlassShaders.build(
      contentMode = LiquidGlassShaders.ContentMode.SingleBlurredInput,
    )

    assertThat(overlayShader).contains(
      "vec3 refractedColor = applyColorGrading(refracted).rgb;",
    )
    assertThat(dualInputShader).contains(
      "vec3 refractedColor = applyColorGrading(vec4(mix(refracted.rgb, blurred.rgb, clamp(depth, 0.0, 1.0)), 1.0)).rgb;",
    )
    assertThat(singleInputShader).contains(
      "vec3 refractedColor = applyColorGrading(refracted).rgb;",
    )
  }

  @Test
  fun shader_singleBlurredInputVariantTreatsContentAsBlurred() {
    val shader = LiquidGlassShaders.build(
      contentMode = LiquidGlassShaders.ContentMode.SingleBlurredInput,
    )

    assertThat(shader).doesNotContain("uniform shader blurredContent;")
    assertThat(shader).contains("vec3 refractedColor = applyColorGrading(refracted).rgb;")
    assertThat(shader).contains("return vec4(finalColor, base.a);")
    assertThat(shader).contains("return vec4(finalColor, base.a) * edge;")
    assertThat(shader).doesNotContain("return vec4(overlayColor, base.a * overlayAlpha);")
    assertThat(shader).doesNotContain("return vec4(finalColor * overlayAlpha, base.a * overlayAlpha);")
  }

  @Test
  fun outputMaskShaderClipsOutsideRoundedShape() {
    val shader = LiquidGlassShaders.buildOutputMask()

    assertThat(shader).contains("if (sd > 0.0) return 0.0;")
    assertThat(shader).contains("return content.eval(coord) * shapeMask(coord);")
  }
}
