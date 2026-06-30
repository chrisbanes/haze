// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

internal object LiquidGlassShaders {
  enum class ContentMode {
    OverlayWithExternalUnderlay,
    DualInput,
    SingleBlurredInput,
  }

  /**
   * Builds the liquid-glass SKSL/AGSL shader.
   *
   * @param hasBlurredContent When `true` the shader declares and samples a
   *   `blurredContent` uniform. When `false` (the default), the shader emits the
   *   compatibility overlay mode for legacy callers that composite an external
   *   blurred underlay themselves.
   */
  fun build(
    hasBlurredContent: Boolean = false,
  ): String = build(
    contentMode = if (hasBlurredContent) {
      ContentMode.DualInput
    } else {
      ContentMode.OverlayWithExternalUnderlay
    },
  )

  fun build(
    contentMode: ContentMode,
  ): String = """
    uniform shader content;
    ${if (contentMode == ContentMode.DualInput) "uniform shader blurredContent;" else ""}
    uniform float2 layerSize;
    uniform float refractionStrength;
    uniform float specularIntensity;
    uniform float depth;
    uniform float ambientResponse;
    uniform float edgeSoftness;
    uniform float refractionHeight;
    uniform float chromaticAberrationStrength;
    uniform float2 lightPosition;
    uniform vec4 cornerRadii;
    uniform vec4 tintColor;
    // Declared as float because AGSL does not support int uniforms.
    uniform float surfaceProfile;
    // Declared as float because AGSL does not support int uniforms.
    uniform float chromaticAberrationMode;
    uniform float contrast;
    uniform float whitePoint;
    uniform float chromaMultiplier;
    uniform float refractionScale;
    uniform float contentNormalBlend;
    uniform float specularExponent;
    uniform float fresnelExponent;

    vec2 clampCoord(vec2 coord) {
      return clamp(coord, vec2(0.5, 0.5), layerSize - vec2(0.5, 0.5));
    }

    float circleMap(float x) {
      return 1.0 - sqrt(max(0.0, 1.0 - x * x));
    }

    float squircleMap(float x) {
      return pow(1.0 - pow(1.0 - x, 4.0), 0.25);
    }

    float smootherstep(float x) {
      float t = clamp(x, 0.0, 1.0);
      return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    float radiusAt(vec2 coord, vec4 radii) {
      if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
      } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
      }
    }

    float sdRoundedRect(vec2 coord, vec2 halfSize, float radius) {
      vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
      float outside = length(max(cornerCoord, 0.0)) - radius;
      float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
      return outside + inside;
    }

    vec2 safeNormalize(vec2 value, vec2 fallback) {
      float len = length(value);
      return len > 0.0001 ? value / len : fallback;
    }

    vec2 axisSafeSign(vec2 value) {
      return vec2(value.x >= 0.0 ? 1.0 : -1.0, value.y >= 0.0 ? 1.0 : -1.0);
    }

    vec2 gradSdRoundedRect(vec2 coord, vec2 halfSize, float radius) {
      vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
      vec2 coordSign = axisSafeSign(coord);
      if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return coordSign * safeNormalize(max(cornerCoord, 0.0), vec2(0.0));
      } else {
        float edgeBlend = smoothstep(-2.0, 2.0, cornerCoord.x - cornerCoord.y);
        vec2 edgeDir = safeNormalize(
          mix(vec2(0.0, 1.0), vec2(1.0, 0.0), edgeBlend),
          vec2(1.0, 0.0)
        );
        float cornerProximity = smoothstep(-radius, 0.0, cornerCoord.x) * smoothstep(-radius, 0.0, cornerCoord.y);
        vec2 arcDir = safeNormalize(-cornerCoord, vec2(0.70710678, 0.70710678));
        vec2 insideDir = mix(edgeDir, arcDir, cornerProximity);
        return coordSign * safeNormalize(insideDir, edgeDir);
      }
    }

    float evaluateProfile(float t) {
      // t runs from 0 (at the flat interior) to 1 (at the edge). Invert so x=0 at the edge
      // and x=1 at the interior, matching the original circleMap/squircleMap parameterisation.
      float x = 1.0 - clamp(t, 0.0, 1.0);
      if (surfaceProfile == 1) {
        return squircleMap(x);
      } else if (surfaceProfile == 2) {
        return -circleMap(x);
      } else if (surfaceProfile == 3) {
        float convex = circleMap(x);
        float concave = -circleMap(x);
        float blend = smootherstep(clamp(t / 0.7, 0.0, 1.0));
        return mix(convex, concave, blend);
      }
      return circleMap(x);
    }

    float surfaceHeightAt(vec2 coord, float customRadius) {
      vec2 halfSize = layerSize * 0.5;
      vec2 centeredCoord = coord - halfSize;
      float sd = sdRoundedRect(centeredCoord, halfSize, customRadius);
      float distToEdge = max(-sd, 0.0);
      float refractionZone = max(refractionHeight, 0.0001);
      if (distToEdge >= refractionZone) return 0.0;
      float t = clamp(distToEdge / refractionZone, 0.0, 1.0);
      return evaluateProfile(t) * refractionZone;
    }

    float surfaceHeight(vec2 coord) {
      vec2 halfSize = layerSize * 0.5;
      vec2 centeredCoord = coord - halfSize;
      float radius = radiusAt(centeredCoord, cornerRadii);
      return surfaceHeightAt(coord, radius);
    }

    vec2 surfaceGradient(vec2 coord) {
      float sampleStep = 2.0;
      float left = surfaceHeight(clampCoord(coord - vec2(sampleStep, 0.0)));
      float right = surfaceHeight(clampCoord(coord + vec2(sampleStep, 0.0)));
      float up = surfaceHeight(clampCoord(coord - vec2(0.0, sampleStep)));
      float down = surfaceHeight(clampCoord(coord + vec2(0.0, sampleStep)));
      return vec2(right - left, down - up) * 0.25;
    }

    float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

    vec3 computeContentNormal(vec2 coord) {
      float l = luma(content.eval(clampCoord(coord + vec2(1.0, 0.0))).rgb);
      float r = luma(content.eval(clampCoord(coord - vec2(1.0, 0.0))).rgb);
      float t = luma(content.eval(clampCoord(coord + vec2(0.0, 1.0))).rgb);
      float b = luma(content.eval(clampCoord(coord - vec2(0.0, 1.0))).rgb);
      vec2 grad = vec2(r - l, b - t);
      return normalize(vec3(grad, 1.0));
    }

    float edgeMask(float distToEdge) {
      if (edgeSoftness <= 0.0) return 1.0;
      float e = clamp(distToEdge / max(edgeSoftness, 0.0001), 0.0, 1.0);
      return smootherstep(e);
    }

    vec4 sampleChromaSimple(vec2 coord, vec2 chromaOffset) {
      if (chromaticAberrationStrength <= 0.0001) return content.eval(clampCoord(coord));
      vec2 forward = clampCoord(coord + chromaOffset);
      vec2 backward = clampCoord(coord - chromaOffset);
      vec4 base = content.eval(clampCoord(coord));
      return vec4(content.eval(forward).r, base.g, content.eval(backward).b, base.a);
    }

    /**
     * Full spectral chromatic aberration that samples the refracted content at seven
     * wavelength offsets (red through purple) and blends them with position-dependent
     * intensity. Much more expensive than the simple mode but produces a realistic
     * prismatic edge when chromaticAberrationStrength is high.
     */
    vec4 sampleChromaFull(vec2 coord, vec2 chromaOffset) {
      if (length(chromaOffset) < 0.0001) return content.eval(clampCoord(coord));

      vec4 color = vec4(0.0);

      vec4 red = content.eval(clampCoord(coord + chromaOffset));
      color.r += red.r / 3.5;
      color.a += red.a / 7.0;

      vec4 orange = content.eval(clampCoord(coord + chromaOffset * (2.0 / 3.0)));
      color.r += orange.r / 3.5;
      color.g += orange.g / 7.0;
      color.a += orange.a / 7.0;

      vec4 yellow = content.eval(clampCoord(coord + chromaOffset * (1.0 / 3.0)));
      color.r += yellow.r / 3.5;
      color.g += yellow.g / 3.5;
      color.a += yellow.a / 7.0;

      vec4 green = content.eval(clampCoord(coord));
      color.g += green.g / 3.5;
      color.a += green.a / 7.0;

      vec4 cyan = content.eval(clampCoord(coord - chromaOffset * (1.0 / 3.0)));
      color.g += cyan.g / 3.5;
      color.b += cyan.b / 3.0;
      color.a += cyan.a / 7.0;

      vec4 blue = content.eval(clampCoord(coord - chromaOffset * (2.0 / 3.0)));
      color.b += blue.b / 3.0;
      color.a += blue.a / 7.0;

      vec4 purple = content.eval(clampCoord(coord - chromaOffset));
      color.r += purple.r / 7.0;
      color.b += purple.b / 3.0;
      color.a += purple.a / 7.0;

      return color;
    }

    vec4 sampleChroma(vec2 coord, vec2 chromaOffset) {
      if (chromaticAberrationMode == 1) {
        return sampleChromaFull(coord, chromaOffset);
      }
      return sampleChromaSimple(coord, chromaOffset);
    }

    ${blurredContentSampler(contentMode)}

    vec3 srgbToLinear(vec3 s) {
      return mix(s / 12.92, pow((s + 0.055) / 1.055, vec3(2.4)), step(0.04045, s));
    }

    vec3 linearToSrgb(vec3 l) {
      return mix(l * 12.92, 1.055 * pow(l, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, l));
    }

    vec4 applyColorGrading(vec4 color) {
      // Saturation (linear sRGB for perceptual uniformity)
      if (chromaMultiplier != 1.0) {
        vec3 lin = srgbToLinear(color.rgb);
        float y = dot(lin, vec3(0.2126, 0.7152, 0.0722));
        color.rgb = linearToSrgb(mix(vec3(y), lin, chromaMultiplier));
      }

      // White point
      if (whitePoint != 0.0) {
        vec3 target = (whitePoint > 0.0) ? vec3(1.0) : vec3(0.0);
        color.rgb = mix(color.rgb, target, abs(whitePoint));
      }

      // Contrast
      if (contrast != 0.0) {
        color.rgb = clamp((color.rgb - 0.5) * (1.0 + contrast) + 0.5, 0.0, 1.0);
      }

      return color;
    }

    vec4 main(vec2 coord) {
      vec2 halfSize = layerSize * 0.5;
      vec2 centeredCoord = coord - halfSize;
      float radius = radiusAt(centeredCoord, cornerRadii);

      // Actual SDF for edge mask and distance (preserves exact shape).
      float sd = sdRoundedRect(centeredCoord, halfSize, radius);
      float distToEdge = max(-sd, 0.0);

      // Flat-interior early-out: skip refraction when far from edge.
      float refractionZone = max(refractionHeight, 0.0001);
      if (distToEdge >= refractionZone) {
        vec4 base = content.eval(coord);
        ${flatInteriorDepthMix(contentMode)}
        vec3 graded = applyColorGrading(vec4(mixedColor, base.a)).rgb;

        // Deep interior: surface gradient is negligible, use content normal only.
        vec3 normal = computeContentNormal(coord);
        vec2 lightDir2D = normalize(lightPosition - coord);
        vec3 lightDir = normalize(vec3(lightDir2D, 1.0));
        float fresnel = pow(1.0 - max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0), fresnelExponent);
        float ambient = mix(1.0, 1.0 + fresnel, clamp(ambientResponse, 0.0, 1.0));

        vec3 tinted = mix(graded, tintColor.rgb, tintColor.a);
        vec3 finalColor = tinted * ambient;
        ${flatInteriorReturn(contentMode)}
      }

      vec4 base = content.eval(coord);

      float h = surfaceHeight(coord);
      float heightNorm = clamp(h / refractionZone, 0.0, 1.0);
      float displacementMagnitude = -heightNorm * refractionStrength * refractionScale; // Scale factor for refraction displacement

      float smoothRadius = max(radius * 1.5, 30.0);
      float gradRadius = min(smoothRadius, min(halfSize.x, halfSize.y));
      vec2 centerFallbackDir = vec2(1.0, 0.0);
      vec2 refractionDir = safeNormalize(
        gradSdRoundedRect(centeredCoord, halfSize, gradRadius) +
          depth * safeNormalize(centeredCoord, centerFallbackDir),
        centerFallbackDir
      );
      vec2 displacement = refractionDir * displacementMagnitude;
      vec2 refractCoord = clampCoord(coord + displacement);

      float cornerWeight = abs((centeredCoord.x * centeredCoord.y) / max(halfSize.x * halfSize.y, 0.001));
      vec2 chromaOffset = displacement * chromaticAberrationStrength * 0.5 * cornerWeight;
      vec4 refracted = sampleChroma(refractCoord, chromaOffset);
      ${refractedDepthSample(contentMode)}
      ${refractedColor(contentMode)}

      vec2 grad = surfaceGradient(coord);
      vec3 shapeNormal = normalize(vec3(-grad.x, -grad.y, 1.0));
      vec3 contentNormal = computeContentNormal(coord);
      vec3 normal = normalize(mix(shapeNormal, contentNormal, contentNormalBlend)); // Blend shape + content normals

      ${refractedDepthMix(contentMode)}
      vec3 graded = applyColorGrading(vec4(mixedColor, 1.0)).rgb;
      vec3 tinted = mix(graded, tintColor.rgb, tintColor.a);
      vec2 lightDir2D = normalize(lightPosition - coord);
      vec3 lightDir = normalize(vec3(lightDir2D, 1.0));
      float spec = pow(max(dot(normal, lightDir), 0.0), specularExponent) * specularIntensity; // Specular highlight exponent
      float fresnel = pow(1.0 - max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0), fresnelExponent); // Fresnel edge glow exponent
      float ambient = mix(1.0, 1.0 + fresnel, clamp(ambientResponse, 0.0, 1.0));
      ${refractedReturn(contentMode)}
    }
    """

  fun buildOutputMask(): String = """
    uniform shader content;
    uniform float2 layerSize;
    uniform float edgeSoftness;
    uniform vec4 cornerRadii;

    float smootherstep(float x) {
      float t = clamp(x, 0.0, 1.0);
      return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    float radiusAt(vec2 coord, vec4 radii) {
      if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
      } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
      }
    }

    float sdRoundedRect(vec2 coord, vec2 halfSize, float radius) {
      vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));
      float outside = length(max(cornerCoord, 0.0)) - radius;
      float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
      return outside + inside;
    }

    float shapeMask(vec2 coord) {
      vec2 halfSize = layerSize * 0.5;
      vec2 centeredCoord = coord - halfSize;
      float radius = radiusAt(centeredCoord, cornerRadii);
      float sd = sdRoundedRect(centeredCoord, halfSize, radius);
      if (sd > 0.0) return 0.0;
      if (edgeSoftness <= 0.0) return 1.0;

      float distToEdge = max(-sd, 0.0);
      float e = clamp(distToEdge / max(edgeSoftness, 0.0001), 0.0, 1.0);
      return smootherstep(e);
    }

    vec4 main(vec2 coord) {
      return content.eval(coord) * shapeMask(coord);
    }
    """

  private fun blurredContentSampler(contentMode: ContentMode): String = when (contentMode) {
    ContentMode.DualInput -> """
    vec4 sampleBlurredContent(vec2 coord) {
      return blurredContent.eval(clampCoord(coord));
    }
    """

    ContentMode.SingleBlurredInput,
    ContentMode.OverlayWithExternalUnderlay,
    -> """
    """
  }

  private fun flatInteriorDepthMix(contentMode: ContentMode): String = when (contentMode) {
    ContentMode.DualInput -> """
        vec4 blurred = sampleBlurredContent(coord);
        vec3 mixedColor = mix(base.rgb, blurred.rgb, clamp(depth, 0.0, 1.0));
    """

    ContentMode.SingleBlurredInput,
    ContentMode.OverlayWithExternalUnderlay,
    -> """
        vec3 mixedColor = base.rgb;
    """
  }

  private fun flatInteriorReturn(contentMode: ContentMode): String = when (contentMode) {
    ContentMode.DualInput,
    ContentMode.SingleBlurredInput,
    -> "return vec4(finalColor, base.a);"

    ContentMode.OverlayWithExternalUnderlay -> """
        float overlayAlpha = 1.0 - clamp(depth, 0.0, 1.0);
        return vec4(finalColor * overlayAlpha, base.a * overlayAlpha);
    """
  }

  private fun refractedDepthSample(contentMode: ContentMode): String = when (contentMode) {
    ContentMode.DualInput -> "vec4 blurred = sampleBlurredContent(refractCoord);"
    ContentMode.SingleBlurredInput,
    ContentMode.OverlayWithExternalUnderlay,
    -> ""
  }

  private fun refractedColor(contentMode: ContentMode): String = when (contentMode) {
    ContentMode.DualInput ->
      "vec3 refractedColor = applyColorGrading(vec4(mix(refracted.rgb, blurred.rgb, clamp(depth, 0.0, 1.0)), 1.0)).rgb;"

    ContentMode.SingleBlurredInput,
    ContentMode.OverlayWithExternalUnderlay,
    -> "vec3 refractedColor = applyColorGrading(refracted).rgb;"
  }

  private fun refractedDepthMix(contentMode: ContentMode): String = when (contentMode) {
    ContentMode.DualInput -> "vec3 mixedColor = mix(base.rgb, blurred.rgb, clamp(depth, 0.0, 1.0));"
    ContentMode.SingleBlurredInput,
    ContentMode.OverlayWithExternalUnderlay,
    -> "vec3 mixedColor = base.rgb;"
  }

  private fun refractedReturn(contentMode: ContentMode): String = when (contentMode) {
    ContentMode.DualInput,
    ContentMode.SingleBlurredInput,
    -> """
      vec3 finalColor = mix(tinted, refractedColor, refractionStrength) * ambient + spec;
      float edge = edgeMask(distToEdge);
      return vec4(finalColor, base.a) * edge;
    """

    ContentMode.OverlayWithExternalUnderlay -> """
      float depthAmount = clamp(depth, 0.0, 1.0);
      float refractionAmount = clamp(refractionStrength, 0.0, 1.0);
      float baseCoeff = (1.0 - depthAmount) * (1.0 - refractionAmount);
      float refractedCoeff = refractionAmount;
      float overlayAlpha = baseCoeff + refractedCoeff;
      vec3 baseColor = tinted * ambient;
      vec3 refractedColorPremul = refractedColor * ambient;
      vec3 overlayColor = baseColor * baseCoeff + refractedColorPremul * refractedCoeff + spec;
      return vec4(overlayColor, base.a * overlayAlpha);
    """
  }
}
