// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

internal object GlassShaders {
  fun buildDownsamplePrefilter(): String = """
    uniform shader content;
    uniform float2 sampleSize;

    vec2 clampSample(vec2 coord) {
      return clamp(coord, vec2(0.5), sampleSize - vec2(0.5));
    }

    vec4 main(vec2 coord) {
      vec4 result = content.eval(clampSample(coord + vec2(-1.0, -1.0))) * 0.0625;
      result += content.eval(clampSample(coord + vec2(0.0, -1.0))) * 0.125;
      result += content.eval(clampSample(coord + vec2(1.0, -1.0))) * 0.0625;
      result += content.eval(clampSample(coord + vec2(-1.0, 0.0))) * 0.125;
      result += content.eval(clampSample(coord)) * 0.25;
      result += content.eval(clampSample(coord + vec2(1.0, 0.0))) * 0.125;
      result += content.eval(clampSample(coord + vec2(-1.0, 1.0))) * 0.0625;
      result += content.eval(clampSample(coord + vec2(0.0, 1.0))) * 0.125;
      result += content.eval(clampSample(coord + vec2(1.0, 1.0))) * 0.0625;
      return result.a > 0.0 ? result : vec4(0.0);
    }
  """

  fun buildBlur(horizontal: Boolean, progressive: Boolean = false): String {
    val samples = buildString {
      repeat(SemanticBlurKernel.MAX_TAP_PAIRS) { index ->
        val direction = if (horizontal) {
          "vec2(offset$index * blurScale, 0.0)"
        } else {
          "vec2(0.0, offset$index * blurScale)"
        }
        appendLine("if (weight$index > 0.0) {")
        appendLine("  vec2 direction$index = $direction;")
        appendLine("  result += weight$index * content.eval(clampSample(coord - direction$index));")
        appendLine("  result += weight$index * content.eval(clampSample(coord + direction$index));")
        appendLine("}")
      }
    }
    val tapUniforms = buildString {
      repeat(SemanticBlurKernel.MAX_TAP_PAIRS) { index ->
        appendLine("uniform float offset$index;")
        appendLine("uniform float weight$index;")
      }
    }
    return """
      uniform shader content;
      ${if (progressive) "uniform shader mask;" else ""}
      uniform float2 sampleSize;
      uniform float2 materialOrigin;
      ${if (progressive) "uniform float maskCoordinateScale;" else ""}
      uniform float centerWeight;
      $tapUniforms

      vec2 clampSample(vec2 coord) {
        return clamp(coord, vec2(0.5), sampleSize - vec2(0.5));
      }

      vec4 main(vec2 coord) {
        float blurScale = ${if (progressive) "clamp(mask.eval(max(coord - materialOrigin, vec2(0.0)) * maskCoordinateScale).a, 0.0, 1.0)" else "1.0"};
        ${if (progressive) "if (blurScale <= 0.0001) { return content.eval(clampSample(coord)); }" else ""}
        vec4 result = content.eval(clampSample(coord)) * centerWeight;
        $samples
        return result.a > 0.0 ? result : vec4(0.0);
      }
    """
  }

  fun buildOptical(interactive: Boolean = false): String = """
    uniform shader content;
    uniform float2 sampleSize;
    uniform float2 materialOrigin;
    uniform float2 materialSize;
    uniform float sampleStep;
    uniform float refractionStrength;
    uniform float ambientResponse;
    uniform float edgeSoftness;
    uniform float refractionHeight;
    uniform float chromaticAberrationStrength;
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
    uniform float fresnelExponent;
    uniform float geometryToneGain;
    uniform float geometryNeutralLift;
    ${if (interactive) interactionUniforms(includeRefraction = true, includeWhitePoint = true, includeLighting = false) else ""}

    vec2 materialCoord(vec2 coord) { return coord - materialOrigin; }

    vec2 clampSample(vec2 coord) {
      return clamp(coord, vec2(0.5), sampleSize - vec2(0.5));
    }

    vec2 clampMaterial(vec2 coord) {
      return clamp(coord, vec2(0.0), materialSize);
    }

    ${sdfHelpers()}

    ${surfaceAndDisplacementHelpers()}

    ${if (interactive) interactionFalloffHelper() else ""}

    vec2 surfaceGradient(vec2 localCoord) {
      float left = surfaceHeight(clampMaterial(localCoord - vec2(sampleStep, 0.0)));
      float right = surfaceHeight(clampMaterial(localCoord + vec2(sampleStep, 0.0)));
      float up = surfaceHeight(clampMaterial(localCoord - vec2(0.0, sampleStep)));
      float down = surfaceHeight(clampMaterial(localCoord + vec2(0.0, sampleStep)));
      return vec2(right - left, down - up) * (0.5 / max(sampleStep, 0.0001));
    }

    vec3 unpremultiply(vec4 color) {
      return color.a > 0.0001 ? color.rgb / color.a : vec3(0.0);
    }

    vec4 premultiply(vec3 color, float alpha) {
      return vec4(color * alpha, alpha);
    }

    float luma(vec3 color) {
      return dot(color, vec3(0.299, 0.587, 0.114));
    }

    vec3 computeContentNormal(vec2 coord) {
      float left = luma(unpremultiply(content.eval(clampSample(coord - vec2(sampleStep, 0.0)))));
      float right = luma(unpremultiply(content.eval(clampSample(coord + vec2(sampleStep, 0.0)))));
      float up = luma(unpremultiply(content.eval(clampSample(coord - vec2(0.0, sampleStep)))));
      float down = luma(unpremultiply(content.eval(clampSample(coord + vec2(0.0, sampleStep)))));
      vec2 gradient = vec2(right - left, down - up) * (0.5 / max(sampleStep, 0.0001));
      return normalize(vec3(gradient, 1.0));
    }

    vec3 sampleChromaSimple(vec2 coord, vec2 chromaOffset, vec4 centerSample) {
      if (length(chromaOffset) < 0.0001) {
        return unpremultiply(centerSample);
      }
      vec3 forward = unpremultiply(content.eval(clampSample(coord + chromaOffset)));
      vec3 backward = unpremultiply(content.eval(clampSample(coord - chromaOffset)));
      vec3 centerStraight = unpremultiply(centerSample);
      return vec3(forward.r, centerStraight.g, backward.b);
    }

    vec3 sampleChromaFull(vec2 coord, vec2 chromaOffset, vec4 centerSample) {
      if (length(chromaOffset) < 0.0001) {
        return unpremultiply(centerSample);
      }

      vec3 red = unpremultiply(content.eval(clampSample(coord + chromaOffset)));
      vec3 orange = unpremultiply(content.eval(clampSample(coord + chromaOffset * (2.0 / 3.0))));
      vec3 yellow = unpremultiply(content.eval(clampSample(coord + chromaOffset * (1.0 / 3.0))));
      vec3 green = unpremultiply(centerSample);
      vec3 cyan = unpremultiply(content.eval(clampSample(coord - chromaOffset * (1.0 / 3.0))));
      vec3 blue = unpremultiply(content.eval(clampSample(coord - chromaOffset * (2.0 / 3.0))));
      vec3 purple = unpremultiply(content.eval(clampSample(coord - chromaOffset)));

      return vec3(
        red.r / 3.5 + orange.r / 3.5 + yellow.r / 3.5 + purple.r / 7.0,
        orange.g / 7.0 + yellow.g / 3.5 + green.g / 3.5 + cyan.g / 3.5,
        cyan.b / 3.0 + blue.b / 3.0 + purple.b / 3.0
      );
    }

    vec3 sampleChroma(vec2 coord, vec2 chromaOffset, vec4 centerSample) {
      if (chromaticAberrationMode == 1) {
        return sampleChromaFull(coord, chromaOffset, centerSample);
      }
      return sampleChromaSimple(coord, chromaOffset, centerSample);
    }

    vec3 srgbToLinear(vec3 color) {
      return mix(color / 12.92, pow((color + 0.055) / 1.055, vec3(2.4)), step(0.04045, color));
    }

    vec3 linearToSrgb(vec3 color) {
      vec3 nonNegative = max(color, vec3(0.0));
      return mix(
        nonNegative * 12.92,
        1.055 * pow(nonNegative, vec3(1.0 / 2.4)) - 0.055,
        step(0.0031308, nonNegative)
      );
    }

    vec3 applyColorGrading(vec3 color, float appliedWhitePoint) {
      if (chromaMultiplier != 1.0) {
        vec3 linearColor = srgbToLinear(color);
        float luminance = dot(linearColor, vec3(0.2126, 0.7152, 0.0722));
        color = linearToSrgb(mix(vec3(luminance), linearColor, chromaMultiplier));
      }
      if (appliedWhitePoint != 0.0) {
        vec3 target = appliedWhitePoint > 0.0 ? vec3(1.0) : vec3(0.0);
        color = mix(color, target, abs(appliedWhitePoint));
      }
      if (contrast != 0.0) {
        color = clamp((color - 0.5) * (1.0 + contrast) + 0.5, 0.0, 1.0);
      }
      return color;
    }

    vec4 main(vec2 coord) {
      vec2 localCoord = materialCoord(coord);
      vec2 halfSize = materialSize * 0.5;
      vec2 centeredCoord = localCoord - halfSize;
      float sd = sdRoundedRect(localCoord, materialSize, cornerRadii);
      if (sd > 0.0) return vec4(0.0);

      float distToEdge = max(-sd, 0.0);
      float shapeMask = edgeSoftness <= 0.0
        ? 1.0
        : smootherstep(clamp(distToEdge / max(edgeSoftness, 0.0001), 0.0, 1.0));
      vec4 baseSample = content.eval(clampSample(coord));
      ${if (interactive) {
    """
      float interactionWeight = interactionFalloff(coord);
      float localizedRefractionMultiplier =
        mix(1.0, interactionRefractionMultiplier, interactionWeight);
      float localizedWhitePoint = clamp(
        whitePoint + interactionWhitePointDelta * interactionWeight,
        -1.0,
        1.0
      );
      """
  } else {
    ""
  }}

      float heightNorm = surfaceHeightNorm(localCoord);
      vec2 displacement = refractionDisplacement(
        localCoord,
        heightNorm,
        ${if (interactive) "localizedRefractionMultiplier" else "1.0"}
      );
      vec2 refractCoord = clampSample(coord + displacement);

      float cornerWeight = abs(
        (centeredCoord.x * centeredCoord.y) / max(halfSize.x * halfSize.y, 0.001)
      );
      vec2 chromaOffset = displacement * chromaticAberrationStrength * 0.5 * cornerWeight;
      vec4 refractedCenterSample = content.eval(clampSample(refractCoord));
      vec3 refractedStraightColor = sampleChroma(refractCoord, chromaOffset, refractedCenterSample);

      vec2 gradient = surfaceGradient(localCoord);
      vec3 shapeNormal = normalize(vec3(-gradient.x, -gradient.y, 1.0));
      vec3 contentNormal = computeContentNormal(coord);
      vec3 normal = normalize(mix(shapeNormal, contentNormal, contentNormalBlend));
      float fresnel = pow(
        1.0 - max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0),
        fresnelExponent
      );
      float ambient = mix(1.0, 1.0 + fresnel, clamp(ambientResponse, 0.0, 1.0));
      vec3 opticalColor = refractedStraightColor;
      vec3 gradedColor = applyColorGrading(
        opticalColor,
        ${if (interactive) "localizedWhitePoint" else "whitePoint"}
      );
      gradedColor = mix(
        clamp(gradedColor * geometryToneGain, 0.0, 1.0),
        vec3(1.0),
        clamp(geometryNeutralLift, 0.0, 1.0)
      );
      vec3 tintedColor = mix(gradedColor, tintColor.rgb, tintColor.a);
      vec3 finalStraightColor = tintedColor * ambient;
      vec4 processedColor = premultiply(finalStraightColor, refractedCenterSample.a);
      vec4 composedColor = mix(baseSample, processedColor, shapeMask);
      return composedColor.a > 0.0 ? composedColor : vec4(0.0);
    }
  """

  fun buildRefractionDetail(
    interactive: Boolean = false,
    coverageOnly: Boolean = false,
  ): String = """
    uniform shader content;
    uniform float2 sampleSize;
    uniform float2 materialOrigin;
    uniform float2 materialSize;
    uniform float refractionStrength;
    uniform float edgeSoftness;
    uniform float refractionHeight;
    uniform vec4 cornerRadii;
    // Declared as float because AGSL does not support int uniforms.
    uniform float surfaceProfile;
    uniform float refractionScale;
    uniform float detailWidth;
    uniform float detailIntensity;
    uniform float detailVisibility;
    ${if (interactive) interactionUniforms(includeRefraction = true, includeWhitePoint = false, includeLighting = false) else ""}

    vec2 materialCoord(vec2 coord) { return coord - materialOrigin; }

    vec2 clampSample(vec2 coord) {
      return clamp(coord, vec2(0.5), sampleSize - vec2(0.5));
    }

    ${sdfHelpers()}

    ${surfaceAndDisplacementHelpers()}

    ${if (interactive) interactionFalloffHelper() else ""}

    vec4 main(vec2 coord) {
      vec2 localCoord = materialCoord(coord);
      float outputSd = sdRoundedRect(localCoord, materialSize, cornerRadii);
      if (outputSd > 0.0) return vec4(0.0);

      float outputDistToEdge = max(-outputSd, 0.0);
      ${if (interactive) {
    """
      float interactionWeight = interactionFalloff(coord);
      float localizedRefractionMultiplier =
        mix(1.0, interactionRefractionMultiplier, interactionWeight);
      """
  } else {
    ""
  }}
      float sampleDiagonal = length(sampleSize);
      float maxPossibleDisplacement = min(
        abs(refractionScale * refractionStrength)${if (interactive) " * max(1.0, localizedRefractionMultiplier)" else ""},
        sampleDiagonal
      );
      if (outputDistToEdge > detailWidth + maxPossibleDisplacement) return vec4(0.0);

      float heightNorm = surfaceHeightNorm(localCoord);
      vec2 displacement = refractionDisplacement(
        localCoord,
        heightNorm,
        ${if (interactive) "localizedRefractionMultiplier" else "1.0"}
      );
      vec2 refractCoord = clampSample(coord + displacement);
      vec2 refractedLocalCoord = localCoord + displacement;
      float refractedSd = sdRoundedRect(refractedLocalCoord, materialSize, cornerRadii);
      float sourceDistToEdge = max(-refractedSd, 0.0);
      float sourceShapeMask = edgeSoftness <= 0.0
        ? 1.0
        : smootherstep(clamp(sourceDistToEdge / max(edgeSoftness, 0.0001), 0.0, 1.0));
      float detailRamp = max(detailWidth * 0.25, 0.0001);
      float innerEnvelope = smootherstep(
        clamp((sourceDistToEdge - detailWidth * 0.5) / detailRamp, 0.0, 1.0)
      );
      float outerEnvelope = 1.0 - smootherstep(
        clamp(sourceDistToEdge / max(detailWidth, 0.0001), 0.0, 1.0)
      );
      float detailAlpha = sourceShapeMask * innerEnvelope * outerEnvelope * detailIntensity * detailVisibility;
      if (detailAlpha <= 0.0) return vec4(0.0);

      ${if (coverageOnly) "return vec4(vec3(detailAlpha), detailAlpha);" else ""}
      vec4 sharpSample = content.eval(refractCoord);
      vec4 detailColor = sharpSample * detailAlpha;
      return detailColor.a > 0.0 ? detailColor : vec4(0.0);
    }
  """

  fun buildInteractionLighting(): String = """
    uniform shader content;
    uniform float2 materialOrigin;
    uniform float2 materialSize;
    uniform vec4 cornerRadii;
    uniform float edgeSoftness;
    ${interactionUniforms(includeRefraction = false, includeWhitePoint = false, includeLighting = true)}

    ${sdfHelpers()}

    ${interactionFalloffHelper()}

    vec4 main(vec2 coord) {
      vec2 localCoord = coord - materialOrigin;
      float sd = sdRoundedRect(localCoord, materialSize, cornerRadii);
      if (sd > 0.0) return vec4(0.0);
      float shapeMask = edgeSoftness <= 0.0
        ? 1.0
        : smootherstep(clamp(max(-sd, 0.0) / max(edgeSoftness, 0.0001), 0.0, 1.0));
      float light = interactionFalloff(coord) * interactionLightingIntensity * shapeMask;
      float contentAlpha = content.eval(coord).a;
      float alpha = light * 0.32 * contentAlpha;
      return vec4(vec3(alpha), alpha);
    }
  """

  fun buildRim(): String = """
    uniform shader content;
    uniform float2 sampleSize;
    uniform float2 materialOrigin;
    uniform float2 materialSize;
    uniform float sampleStep;
    uniform vec4 cornerRadii;
    uniform float specularIntensity;
    uniform float specularExponent;
    uniform float edgeSoftness;
    uniform float2 lightPosition;

    vec2 materialCoord(vec2 coord) { return coord - materialOrigin; }

    vec2 clampMaterial(vec2 coord) {
      return clamp(coord, vec2(0.0), materialSize);
    }

    ${sdfHelpers()}

    float materialSdf(vec2 localCoord) {
      return sdRoundedRect(localCoord, materialSize, cornerRadii);
    }

    vec2 sdfGradient(vec2 localCoord) {
      float left = materialSdf(clampMaterial(localCoord - vec2(sampleStep, 0.0)));
      float right = materialSdf(clampMaterial(localCoord + vec2(sampleStep, 0.0)));
      float up = materialSdf(clampMaterial(localCoord - vec2(0.0, sampleStep)));
      float down = materialSdf(clampMaterial(localCoord + vec2(0.0, sampleStep)));
      return vec2(right - left, down - up) * (0.5 / max(sampleStep, 0.0001));
    }

    vec4 main(vec2 coord) {
      vec2 localCoord = materialCoord(coord);
      float sd = materialSdf(localCoord);
      if (sd > 0.0) return vec4(0.0);

      float edgeWidth = max(edgeSoftness, sampleStep);
      float edge = 1.0 - smootherstep(clamp(-sd / max(edgeWidth, 0.0001), 0.0, 1.0));
      vec2 gradient = sdfGradient(localCoord);
      vec3 normal = normalize(vec3(-gradient.x, -gradient.y, 1.0));
      vec2 lightDirection2D = safeNormalize(lightPosition - localCoord, vec2(0.0, -1.0));
      vec3 lightDirection = normalize(vec3(lightDirection2D, 1.0));
      float specular = pow(max(dot(normal, lightDirection), 0.0), specularExponent);
      float alpha = specular * specularIntensity * edge;
      return alpha > 0.0 ? vec4(vec3(alpha), alpha) : vec4(0.0);
    }
  """

  private fun sdfHelpers(): String = """
    float smootherstep(float x) {
      float t = clamp(x, 0.0, 1.0);
      return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    float sdRectangle(vec2 localCoord, vec2 size) {
      vec2 halfSize = size * 0.5;
      vec2 edgeDistance = abs(localCoord - halfSize) - halfSize;
      float outside = length(max(edgeDistance, 0.0));
      float inside = min(max(edgeDistance.x, edgeDistance.y), 0.0);
      return outside + inside;
    }

    float sdRoundedRect(vec2 localCoord, vec2 size, vec4 radii) {
      float sd = sdRectangle(localCoord, size);
      if (localCoord.x < radii.x && localCoord.y < radii.x) {
        sd = max(sd, length(localCoord - vec2(radii.x)) - radii.x);
      }
      if (localCoord.x > size.x - radii.y && localCoord.y < radii.y) {
        sd = max(sd, length(localCoord - vec2(size.x - radii.y, radii.y)) - radii.y);
      }
      if (localCoord.x > size.x - radii.z && localCoord.y > size.y - radii.z) {
        sd = max(
          sd,
          length(localCoord - vec2(size.x - radii.z, size.y - radii.z)) - radii.z
        );
      }
      if (localCoord.x < radii.w && localCoord.y > size.y - radii.w) {
        sd = max(sd, length(localCoord - vec2(radii.w, size.y - radii.w)) - radii.w);
      }
      return sd;
    }

    vec2 safeNormalize(vec2 value, vec2 fallback) {
      float len = length(value);
      return len > 0.0001 ? value / len : fallback;
    }

    vec4 reversedSmootherstep(vec4 t) {
      t = clamp(t, 0.0, 1.0);
      return vec4(1.0) - t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    vec2 gradSdRectangle(vec2 localCoord, vec2 size, float blendWidth) {
      vec4 edgeDistance = vec4(
        -localCoord.x,
        localCoord.x - size.x,
        -localCoord.y,
        localCoord.y - size.y
      );
      float maxDistance = max(
        max(edgeDistance.x, edgeDistance.y),
        max(edgeDistance.z, edgeDistance.w)
      );
      vec4 weights = reversedSmootherstep(
        (vec4(maxDistance) - edgeDistance) / blendWidth
      );
      float totalWeight = dot(weights, vec4(1.0));
      return vec2(weights.y - weights.x, weights.w - weights.z) /
        max(totalWeight, 0.0001);
    }

    vec2 gradSdRoundedRect(vec2 localCoord, vec2 size, vec4 radii, float blendWidth) {
      vec2 rectangleGradient = gradSdRectangle(localCoord, size, blendWidth);
      vec2 topLeftDelta = min(localCoord - vec2(radii.x), vec2(0.0));
      vec2 topRightDelta = vec2(
        max(localCoord.x - (size.x - radii.y), 0.0),
        min(localCoord.y - radii.y, 0.0)
      );
      vec2 bottomRightDelta = max(
        localCoord - vec2(size.x - radii.z, size.y - radii.z),
        vec2(0.0)
      );
      vec2 bottomLeftDelta = vec2(
        min(localCoord.x - radii.w, 0.0),
        max(localCoord.y - (size.y - radii.w), 0.0)
      );
      vec4 cornerWeights = vec4(
        smootherstep(clamp(length(topLeftDelta) / max(radii.x, 0.0001), 0.0, 1.0)),
        smootherstep(clamp(length(topRightDelta) / max(radii.y, 0.0001), 0.0, 1.0)),
        smootherstep(clamp(length(bottomRightDelta) / max(radii.z, 0.0001), 0.0, 1.0)),
        smootherstep(clamp(length(bottomLeftDelta) / max(radii.w, 0.0001), 0.0, 1.0))
      );
      vec2 cornerGradient = safeNormalize(
        safeNormalize(topLeftDelta, rectangleGradient) * cornerWeights.x +
          safeNormalize(topRightDelta, rectangleGradient) * cornerWeights.y +
          safeNormalize(bottomRightDelta, rectangleGradient) * cornerWeights.z +
          safeNormalize(bottomLeftDelta, rectangleGradient) * cornerWeights.w,
        rectangleGradient
      );
      float cornerWeight = max(
        max(cornerWeights.x, cornerWeights.y),
        max(cornerWeights.z, cornerWeights.w)
      );
      return mix(rectangleGradient, cornerGradient, cornerWeight);
    }

  """

  private fun surfaceAndDisplacementHelpers(): String = """
    float circleMap(float x) {
      return 1.0 - sqrt(max(0.0, 1.0 - x * x));
    }

    float squircleMap(float t) {
      float profile = pow(max(0.0, 1.0 - pow(t, 4.0)), 0.25);
      float terminalT = clamp((t - 0.75) / 0.25, 0.0, 1.0);
      float terminalTaper = 1.0 - smootherstep(terminalT);
      return profile * terminalTaper;
    }

    float evaluateProfile(float t) {
      float x = 1.0 - clamp(t, 0.0, 1.0);
      if (surfaceProfile == 1) {
        return squircleMap(t);
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

    float surfaceHeightAt(vec2 localCoord, vec4 customRadii) {
      float sd = sdRoundedRect(localCoord, materialSize, customRadii);
      float distToEdge = max(-sd, 0.0);
      float refractionZone = max(refractionHeight, 0.0001);
      float t = clamp(distToEdge / refractionZone, 0.0, 1.0);
      return evaluateProfile(t) * refractionZone;
    }

    float surfaceHeight(vec2 localCoord) {
      return surfaceHeightAt(localCoord, cornerRadii);
    }

    float surfaceHeightNorm(vec2 localCoord) {
      float refractionZone = max(refractionHeight, 0.0001);
      return clamp(surfaceHeight(localCoord) / refractionZone, -1.0, 1.0);
    }

    vec2 refractionDisplacement(
      vec2 localCoord,
      float heightNorm,
      float refractionMultiplier
    ) {
      float effectiveRefractionStrength =
        clamp(refractionStrength * refractionMultiplier, 0.0, 1.0);
      float displacementMagnitude =
        heightNorm * effectiveRefractionStrength * refractionScale;
      float normalBlendWidth = max(refractionHeight, 1.0);
      vec2 opticalGradient =
        gradSdRoundedRect(localCoord, materialSize, cornerRadii, normalBlendWidth);
      float gradientLength = length(opticalGradient);
      float centerFade = smootherstep(clamp(gradientLength / 0.5, 0.0, 1.0));
      vec2 refractionDir = opticalGradient / max(gradientLength, 0.0001);
      return -refractionDir * displacementMagnitude * centerFade;
    }
  """

  private fun interactionUniforms(
    includeRefraction: Boolean,
    includeWhitePoint: Boolean,
    includeLighting: Boolean,
  ): String = """
    uniform float2 interactionPosition;
    uniform float interactionRadius;
    ${if (includeRefraction) "uniform float interactionRefractionMultiplier;" else ""}
    ${if (includeWhitePoint) "uniform float interactionWhitePointDelta;" else ""}
    ${if (includeLighting) "uniform float interactionLightingIntensity;" else ""}
  """

  private fun interactionFalloffHelper(): String = """
    float interactionFalloff(vec2 coord) {
      float normalized = distance(coord, interactionPosition) / max(interactionRadius, 0.0001);
      return 1.0 - smootherstep(clamp(normalized, 0.0, 1.0));
    }
  """
}
