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
      uniform float centerWeight;
      $tapUniforms

      vec2 clampSample(vec2 coord) {
        return clamp(coord, vec2(0.5), sampleSize - vec2(0.5));
      }

      vec4 main(vec2 coord) {
        float blurScale = ${if (progressive) "clamp(mask.eval(max(coord - materialOrigin, vec2(0.0))).a, 0.0, 1.0)" else "1.0"};
        ${if (progressive) "if (blurScale <= 0.0001) { return content.eval(clampSample(coord)); }" else ""}
        vec4 result = content.eval(clampSample(coord)) * centerWeight;
        $samples
        return result.a > 0.0 ? result : vec4(0.0);
      }
    """
  }

  fun buildOptical(): String = """
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

    vec2 materialCoord(vec2 coord) { return coord - materialOrigin; }

    vec2 clampSample(vec2 coord) {
      return clamp(coord, vec2(0.5), sampleSize - vec2(0.5));
    }

    vec2 clampMaterial(vec2 coord) {
      return clamp(coord, vec2(0.0), materialSize);
    }

    ${sdfHelpers()}

    ${surfaceAndDisplacementHelpers()}

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
      return mix(color * 12.92, 1.055 * pow(color, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, color));
    }

    vec3 applyColorGrading(vec3 color) {
      if (chromaMultiplier != 1.0) {
        vec3 linearColor = srgbToLinear(color);
        float luminance = dot(linearColor, vec3(0.2126, 0.7152, 0.0722));
        color = linearToSrgb(mix(vec3(luminance), linearColor, chromaMultiplier));
      }
      if (whitePoint != 0.0) {
        vec3 target = whitePoint > 0.0 ? vec3(1.0) : vec3(0.0);
        color = mix(color, target, abs(whitePoint));
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
      float radius = radiusAt(centeredCoord, cornerRadii);
      float sd = sdRoundedRect(centeredCoord, halfSize, radius);
      if (sd > 0.0) return vec4(0.0);

      float distToEdge = max(-sd, 0.0);
      float shapeMask = edgeSoftness <= 0.0
        ? 1.0
        : smootherstep(clamp(distToEdge / max(edgeSoftness, 0.0001), 0.0, 1.0));
      vec4 baseSample = content.eval(clampSample(coord));

      float heightNorm = surfaceHeightNorm(localCoord);
      vec2 displacement = refractionDisplacement(centeredCoord, halfSize, radius, heightNorm);
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
      vec3 gradedColor = applyColorGrading(opticalColor);
      gradedColor = mix(
        clamp(gradedColor * geometryToneGain, 0.0, 1.0),
        vec3(1.0),
        clamp(geometryNeutralLift, 0.0, 1.0)
      );
      vec3 tintedColor = mix(gradedColor, tintColor.rgb, tintColor.a);
      vec3 finalStraightColor = tintedColor * ambient;
      vec4 processedColor = premultiply(finalStraightColor, refractedCenterSample.a);
      vec4 coveredColor = processedColor * shapeMask;
      vec4 composedColor = coveredColor + baseSample * (1.0 - coveredColor.a);
      return composedColor.a > 0.0 ? composedColor : vec4(0.0);
    }
  """

  fun buildRefractionDetail(): String = """
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

    vec2 materialCoord(vec2 coord) { return coord - materialOrigin; }

    vec2 clampSample(vec2 coord) {
      return clamp(coord, vec2(0.5), sampleSize - vec2(0.5));
    }

    ${sdfHelpers()}

    ${surfaceAndDisplacementHelpers()}

    vec4 main(vec2 coord) {
      vec2 localCoord = materialCoord(coord);
      vec2 halfSize = materialSize * 0.5;
      vec2 centeredCoord = localCoord - halfSize;
      float radius = radiusAt(centeredCoord, cornerRadii);
      float outputSd = sdRoundedRect(centeredCoord, halfSize, radius);
      if (outputSd > 0.0) return vec4(0.0);

      float outputDistToEdge = max(-outputSd, 0.0);
      float sampleDiagonal = length(sampleSize);
      float maxPossibleDisplacement = min(
        abs(refractionScale * refractionStrength),
        sampleDiagonal
      );
      if (outputDistToEdge > detailWidth + maxPossibleDisplacement) return vec4(0.0);

      float heightNorm = surfaceHeightNorm(localCoord);
      vec2 displacement = refractionDisplacement(centeredCoord, halfSize, radius, heightNorm);
      vec2 refractCoord = clampSample(coord + displacement);
      vec2 refractedLocalCoord = localCoord + displacement;
      vec2 refractedCenteredCoord = refractedLocalCoord - halfSize;
      float refractedRadius = radiusAt(refractedCenteredCoord, cornerRadii);
      float refractedSd = sdRoundedRect(refractedCenteredCoord, halfSize, refractedRadius);
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

      vec4 sharpSample = content.eval(refractCoord);
      vec4 detailColor = sharpSample * detailAlpha;
      return detailColor.a > 0.0 ? detailColor : vec4(0.0);
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
      vec2 halfSize = materialSize * 0.5;
      vec2 centeredCoord = localCoord - halfSize;
      float radius = radiusAt(centeredCoord, cornerRadii);
      return sdRoundedRect(centeredCoord, halfSize, radius);
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
        float cornerProximity =
          smoothstep(-radius, 0.0, cornerCoord.x) *
          smoothstep(-radius, 0.0, cornerCoord.y);
        vec2 arcDir = safeNormalize(-cornerCoord, vec2(0.70710678, 0.70710678));
        vec2 insideDir = mix(edgeDir, arcDir, cornerProximity);
        return coordSign * safeNormalize(insideDir, edgeDir);
      }
    }
  """

  private fun surfaceAndDisplacementHelpers(): String = """
    float circleMap(float x) {
      return 1.0 - sqrt(max(0.0, 1.0 - x * x));
    }

    float squircleMap(float x) {
      return pow(1.0 - pow(1.0 - x, 4.0), 0.25);
    }

    float evaluateProfile(float t) {
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

    float surfaceHeightAt(vec2 localCoord, float customRadius) {
      vec2 halfSize = materialSize * 0.5;
      vec2 centeredCoord = localCoord - halfSize;
      float sd = sdRoundedRect(centeredCoord, halfSize, customRadius);
      float distToEdge = max(-sd, 0.0);
      float refractionZone = max(refractionHeight, 0.0001);
      float t = clamp(distToEdge / refractionZone, 0.0, 1.0);
      return evaluateProfile(t) * refractionZone;
    }

    float surfaceHeight(vec2 localCoord) {
      vec2 halfSize = materialSize * 0.5;
      vec2 centeredCoord = localCoord - halfSize;
      float radius = radiusAt(centeredCoord, cornerRadii);
      return surfaceHeightAt(localCoord, radius);
    }

    float surfaceHeightNorm(vec2 localCoord) {
      float refractionZone = max(refractionHeight, 0.0001);
      return clamp(surfaceHeight(localCoord) / refractionZone, -1.0, 1.0);
    }

    vec2 refractionDisplacement(
      vec2 centeredCoord,
      vec2 halfSize,
      float radius,
      float heightNorm
    ) {
      float displacementMagnitude = heightNorm * refractionStrength * refractionScale;
      float smoothRadius = max(radius * 1.5, 30.0);
      float gradRadius = min(smoothRadius, min(halfSize.x, halfSize.y));
      vec2 centerFallbackDir = vec2(1.0, 0.0);
      vec2 refractionDir = safeNormalize(
        gradSdRoundedRect(centeredCoord, halfSize, gradRadius),
        centerFallbackDir
      );
      return -refractionDir * displacementMagnitude;
    }
  """
}
