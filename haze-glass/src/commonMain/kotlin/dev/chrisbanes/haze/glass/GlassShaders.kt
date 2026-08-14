// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

internal object GlassShaders {
  fun buildFused(
    interactionOptics: Boolean = false,
    sharpDetail: Boolean = true,
  ): String = """
    uniform shader content;
    uniform float2 sampleSize;
    uniform float2 materialOrigin;
    uniform float2 materialSize;
    uniform float sampleStep;
    uniform float refractionStrength;
    uniform float refractionFoldStrength;
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
    ${if (sharpDetail) {
    """
    uniform float detailWidth;
    uniform float detailIntensity;
    uniform float detailVisibility;
    """
  } else {
    ""
  }}
    ${if (interactionOptics) {
    interactionUniforms(
      includeRefraction = true,
      includeWhitePoint = true,
      includeLighting = false,
    ) + "uniform float interactionOpticalActive;"
  } else {
    ""
  }}

    vec2 materialCoord(vec2 coord) { return coord - materialOrigin; }

    vec2 clampSample(vec2 coord) {
      return clamp(coord, vec2(0.5), sampleSize - vec2(0.5));
    }

    vec2 clampMaterial(vec2 coord) {
      return clamp(coord, vec2(0.0), materialSize);
    }

    ${sdfHelpers()}

    ${surfaceAndDisplacementHelpers()}

    ${if (interactionOptics) interactionFalloffHelper() else ""}

    vec4 sampleDepth(vec2 coord) {
      return content.eval(clampSample(coord));
    }

    ${opticalHelpers()}

    vec4 main(vec2 coord) {
      vec2 localCoord = materialCoord(coord);
      vec2 halfSize = materialSize * 0.5;
      vec2 centeredCoord = localCoord - halfSize;
      float outputSd = sdRoundedRect(localCoord, materialSize, cornerRadii);
      if (outputSd > 0.0) return vec4(0.0);

      float outputDistToEdge = max(-outputSd, 0.0);
      float shapeMask = edgeSoftness <= 0.0
        ? 1.0
        : smootherstep(clamp(outputDistToEdge / max(edgeSoftness, 0.0001), 0.0, 1.0));
      ${if (interactionOptics) {
    """
      float localizedRefractionMultiplier = 1.0;
      float localizedWhitePoint = whitePoint;
      if (interactionOpticalActive > 0.5) {
        float interactionWeight = interactionFalloff(coord);
        localizedRefractionMultiplier =
          mix(1.0, interactionRefractionMultiplier, interactionWeight);
        localizedWhitePoint = clamp(
          whitePoint + interactionWhitePointDelta * interactionWeight,
          -1.0,
          1.0
        );
      }
      """
  } else {
    ""
  }}
      float fieldWeight = opticalFieldWeight();
      float opticalDistance =
        opticalDistanceFromSignedDistance(localCoord, outputSd, fieldWeight);
      float heightNorm = surfaceHeightNormFromOpticalDistance(opticalDistance);
      vec2 displacement = refractionDisplacement(
        localCoord,
        heightNorm,
        opticalDistance,
        ${if (interactionOptics) "localizedRefractionMultiplier" else "1.0"},
        fieldWeight
      );
      vec2 refractCoord = clampSample(coord + displacement);

      float cornerWeight = abs(
        (centeredCoord.x * centeredCoord.y) / max(halfSize.x * halfSize.y, 0.001)
      );
      vec2 chromaOffset =
        displacement * chromaticAberrationStrength * 0.5 * cornerWeight;
      vec4 refractedCenter = sampleDepth(refractCoord);
      vec3 refractedStraightColor =
        sampleChroma(refractCoord, chromaOffset, refractedCenter);

      float ambient = 1.0;
      if (ambientResponse > 0.0) {
        float clampedAmbientResponse = clamp(ambientResponse, 0.0, 1.0);
        if (fresnelExponent == 0.0) {
          ambient = 1.0 + clampedAmbientResponse;
        } else {
          vec2 gradient = surfaceLightingGradient(localCoord, outputSd, fieldWeight);
          vec3 shapeNormal = normalize(vec3(-gradient.x, -gradient.y, 1.0));
          vec3 normal = shapeNormal;
          if (contentNormalBlend > 0.0) {
            vec3 contentNormal = computeContentNormal(refractCoord, refractedCenter);
            normal = normalize(mix(shapeNormal, contentNormal, contentNormalBlend));
          }
          float fresnelBase =
            1.0 - max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0);
          float fresnel = fresnelExponent == 3.0
            ? fresnelBase * fresnelBase * fresnelBase
            : pow(fresnelBase, fresnelExponent);
          ambient = mix(1.0, 1.0 + fresnel, clampedAmbientResponse);
        }
      }
      vec3 gradedColor = applyColorGrading(
        refractedStraightColor,
        ${if (interactionOptics) "localizedWhitePoint" else "whitePoint"}
      );
      gradedColor = mix(
        clamp(gradedColor * geometryToneGain, 0.0, 1.0),
        vec3(1.0),
        clamp(geometryNeutralLift, 0.0, 1.0)
      );
      vec3 tintedColor = mix(gradedColor, tintColor.rgb, tintColor.a);
      vec4 opticalColor = premultiply(tintedColor * ambient, refractedCenter.a);
      if (shapeMask < 1.0) {
        opticalColor = mix(sampleDepth(coord), opticalColor, shapeMask);
      }

      ${if (sharpDetail) {
    """
      float detailAlpha = 0.0;
      float maxPossibleDisplacement = min(
        ${if (interactionOptics) {
      "abs(refractionScale * refractionStrength) * max(1.0, localizedRefractionMultiplier)"
    } else {
      "abs(refractionScale * refractionStrength)"
    }},
        length(sampleSize)
      );
      if (outputDistToEdge <= detailWidth + maxPossibleDisplacement) {
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
        detailAlpha =
          sourceShapeMask * innerEnvelope * outerEnvelope * detailIntensity * detailVisibility;
      }
      opticalColor *= 1.0 - detailAlpha;
      """
  } else {
    ""
  }}
      return opticalColor.a > 0.0 ? opticalColor : vec4(0.0);
    }
  """

  fun buildDownsamplePrefilter(): String = """
    uniform shader content;
    uniform float2 sampleSize;

    vec2 clampSample(vec2 coord) {
      return clamp(coord, vec2(0.5), sampleSize - vec2(0.5));
    }

    vec4 main(vec2 coord) {
      vec4 result = content.eval(clampSample(coord + vec2(-0.5, -0.5))) * 0.25;
      result += content.eval(clampSample(coord + vec2(0.5, -0.5))) * 0.25;
      result += content.eval(clampSample(coord + vec2(-0.5, 0.5))) * 0.25;
      result += content.eval(clampSample(coord + vec2(0.5, 0.5))) * 0.25;
      return result.a > 0.0 ? result : vec4(0.0);
    }
  """

  fun buildFusedDownsamplePrefilter(): String = """
    uniform shader content;
    uniform float2 sampleSize;
    uniform float strength;

    vec2 clampSample(vec2 coord) {
      return clamp(coord, vec2(0.5), sampleSize - vec2(0.5));
    }

    vec4 main(vec2 coord) {
      vec4 source = content.eval(clampSample(coord));
      vec4 filtered = content.eval(clampSample(coord + vec2(-0.5, -0.5))) * 0.25;
      filtered += content.eval(clampSample(coord + vec2(0.5, -0.5))) * 0.25;
      filtered += content.eval(clampSample(coord + vec2(-0.5, 0.5))) * 0.25;
      filtered += content.eval(clampSample(coord + vec2(0.5, 0.5))) * 0.25;
      return mix(source, filtered, strength);
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

  fun buildOptical(
    interactive: Boolean = false,
  ): String = """
    uniform shader content;
    uniform float2 sampleSize;
    uniform float2 materialOrigin;
    uniform float2 materialSize;
    uniform float sampleStep;
    uniform float refractionStrength;
    uniform float refractionFoldStrength;
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

    vec4 sampleDepth(vec2 coord) {
      return content.eval(clampSample(coord));
    }

    ${opticalHelpers()}

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

      float fieldWeight = opticalFieldWeight();
      float opticalDistance =
        opticalDistanceFromSignedDistance(localCoord, sd, fieldWeight);
      float heightNorm = surfaceHeightNormFromOpticalDistance(opticalDistance);
      vec2 displacement = refractionDisplacement(
        localCoord,
        heightNorm,
        opticalDistance,
        ${if (interactive) "localizedRefractionMultiplier" else "1.0"},
        fieldWeight
      );
      vec2 refractCoord = clampSample(coord + displacement);

      float cornerWeight = abs(
        (centeredCoord.x * centeredCoord.y) / max(halfSize.x * halfSize.y, 0.001)
      );
      vec2 chromaOffset = displacement * chromaticAberrationStrength * 0.5 * cornerWeight;
      vec4 refractedCenterSample = content.eval(clampSample(refractCoord));
      vec3 refractedStraightColor =
        sampleChroma(refractCoord, chromaOffset, refractedCenterSample);

      float ambient = 1.0;
      if (ambientResponse > 0.0) {
        float clampedAmbientResponse = clamp(ambientResponse, 0.0, 1.0);
        if (fresnelExponent == 0.0) {
          ambient = 1.0 + clampedAmbientResponse;
        } else {
          vec2 gradient = surfaceLightingGradient(localCoord, sd, fieldWeight);
          vec3 shapeNormal = normalize(vec3(-gradient.x, -gradient.y, 1.0));
          vec3 normal = shapeNormal;
          if (contentNormalBlend > 0.0) {
            vec3 contentNormal = computeContentNormal(refractCoord, refractedCenterSample);
            normal = normalize(mix(shapeNormal, contentNormal, contentNormalBlend));
          }
          float fresnelBase = 1.0 - max(dot(normal, vec3(0.0, 0.0, 1.0)), 0.0);
          float fresnel = fresnelExponent == 3.0
            ? fresnelBase * fresnelBase * fresnelBase
            : pow(fresnelBase, fresnelExponent);
          ambient = mix(1.0, 1.0 + fresnel, clampedAmbientResponse);
        }
      }
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
      if (shapeMask >= 1.0) {
        return processedColor.a > 0.0 ? processedColor : vec4(0.0);
      }
      vec4 baseSample = content.eval(clampSample(coord));
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
    uniform float sampleStep;
    uniform float refractionStrength;
    uniform float refractionFoldStrength;
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

      float fieldWeight = opticalFieldWeight();
      float opticalDistance =
        opticalDistanceFromSignedDistance(localCoord, outputSd, fieldWeight);
      float heightNorm = surfaceHeightNormFromOpticalDistance(opticalDistance);
      vec2 displacement = refractionDisplacement(
        localCoord,
        heightNorm,
        opticalDistance,
        ${if (interactive) "localizedRefractionMultiplier" else "1.0"},
        fieldWeight
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

  fun buildInteractionOutputComposite(): String = """
    uniform shader content;
    uniform float2 interactionPosition;
    uniform float interactionRadius;
    uniform float featherWidth;

    vec4 main(vec2 coord) {
      vec4 color = content.eval(coord);
      if (color.a <= 0.0001) return vec4(0.0);

      float distanceToEdge = interactionRadius - distance(coord, interactionPosition);
      float mask = smoothstep(0.0, max(featherWidth, 0.0001), distanceToEdge);
      return vec4((color.rgb / color.a) * mask, mask);
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

    ${sdfShapeHelpers()}

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
      if (edge <= 0.0) return vec4(0.0);
      vec2 gradient = sdfGradient(localCoord);
      vec3 normal = normalize(vec3(-gradient.x, -gradient.y, 1.0));
      vec2 lightDirection2D = safeNormalize(lightPosition - localCoord, vec2(0.0, -1.0));
      vec3 lightDirection = normalize(vec3(lightDirection2D, 1.0));
      float specularBase = max(dot(normal, lightDirection), 0.0);
      float specular = specularExponent == 0.0 ? 1.0 : pow(specularBase, specularExponent);
      float alpha = specular * specularIntensity * edge;
      return alpha > 0.0 ? vec4(vec3(alpha), alpha) : vec4(0.0);
    }
  """

  private fun sdfHelpers(): String = """
    ${sdfShapeHelpers()}

    ${sdfGradientHelpers()}
  """

  private fun sdfShapeHelpers(): String = """
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
  """

  private fun sdfGradientHelpers(): String = """
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
      return 1.0 - smootherstep(t * sqrt(t));
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

    float elongationWeight() {
      float shortestSide = max(min(materialSize.x, materialSize.y), 0.0001);
      float aspectRatio = max(materialSize.x, materialSize.y) / shortestSide;
      // Preserve near-square optics and complete the transition for long controls.
      return smootherstep(clamp((aspectRatio - 1.5) / 1.5, 0.0, 1.0));
    }

    vec2 normalizedMaterialCoord(vec2 localCoord) {
      vec2 halfSize = max(materialSize * 0.5, vec2(0.0001));
      return (localCoord - halfSize) / halfSize;
    }

    float opticalFieldWeight() {
      float inradius = max(min(materialSize.x, materialSize.y) * 0.5, 0.0001);
      // Begin the transition before opposing edge profiles reach the medial axis.
      float overlapWeight = smootherstep(
        clamp((refractionHeight / inradius - 0.75) / 0.25, 0.0, 1.0)
      );
      return elongationWeight() * overlapWeight;
    }

    float domeRadius(vec2 normalizedCoord) {
      vec2 squaredCoord = normalizedCoord * normalizedCoord;
      // A smooth rectangular radius: zero at center and one on every axis-aligned edge.
      return sqrt(clamp(
        squaredCoord.x + squaredCoord.y - squaredCoord.x * squaredCoord.y,
        0.0,
        1.0
      ));
    }

    float domeDistance(vec2 localCoord) {
      return max(
        (1.0 - domeRadius(normalizedMaterialCoord(localCoord))) * refractionHeight,
        0.0
      );
    }

    float opticalDistanceFromSignedDistance(
      vec2 localCoord,
      float sd,
      float fieldWeight
    ) {
      float distToEdge = max(-sd, 0.0);
      if (fieldWeight <= 0.0) return distToEdge;
      float distanceFromDome = domeDistance(localCoord);
      if (fieldWeight >= 1.0) return distanceFromDome;
      return mix(distToEdge, distanceFromDome, fieldWeight);
    }

    float surfaceHeightFromOpticalDistance(float opticalDistance) {
      float refractionZone = max(refractionHeight, 0.0001);
      float t = clamp(opticalDistance / refractionZone, 0.0, 1.0);
      return evaluateProfile(t) * refractionZone;
    }

    float surfaceHeightAt(vec2 localCoord, vec4 customRadii, float fieldWeight) {
      if (fieldWeight >= 1.0) {
        return surfaceHeightFromOpticalDistance(domeDistance(localCoord));
      }
      float sd = sdRoundedRect(localCoord, materialSize, customRadii);
      float opticalDistance = opticalDistanceFromSignedDistance(localCoord, sd, fieldWeight);
      return surfaceHeightFromOpticalDistance(opticalDistance);
    }

    float surfaceHeight(vec2 localCoord, float fieldWeight) {
      return surfaceHeightAt(localCoord, cornerRadii, fieldWeight);
    }

    float surfaceHeightNormFromOpticalDistance(float opticalDistance) {
      float refractionZone = max(refractionHeight, 0.0001);
      return clamp(
        surfaceHeightFromOpticalDistance(opticalDistance) / refractionZone,
        -1.0,
        1.0
      );
    }

    vec2 opticalSurfaceGradient(vec2 localCoord, float fieldWeight) {
      if (fieldWeight >= 1.0) {
        vec2 normalizedCoord = normalizedMaterialCoord(localCoord);
        vec2 squaredCoord = normalizedCoord * normalizedCoord;
        return vec2(
          normalizedCoord.x * (1.0 - squaredCoord.y),
          normalizedCoord.y * (1.0 - squaredCoord.x)
        );
      }
      float normalBlendWidth = max(refractionHeight, 1.0);
      vec2 boundaryGradient = gradSdRoundedRect(
        localCoord,
        materialSize,
        cornerRadii,
        normalBlendWidth
      );
      if (fieldWeight <= 0.0) return boundaryGradient;
      vec2 normalizedCoord = normalizedMaterialCoord(localCoord);
      vec2 squaredCoord = normalizedCoord * normalizedCoord;
      vec2 domeGradient = vec2(
        normalizedCoord.x * (1.0 - squaredCoord.y),
        normalizedCoord.y * (1.0 - squaredCoord.x)
      );
      return mix(
        boundaryGradient,
        domeGradient,
        fieldWeight
      );
    }

    vec2 refractionDisplacement(
      vec2 localCoord,
      float heightNorm,
      float opticalDistance,
      float refractionMultiplier,
      float fieldWeight
    ) {
      float effectiveRefractionStrength =
        clamp(refractionStrength * refractionMultiplier, 0.0, 1.0);
      float foldWidth = max(refractionHeight, sampleStep);
      float foldT = clamp(opticalDistance / foldWidth, 0.0, 1.0);
      float foldEnvelope = 16.0 * foldT * foldT * (1.0 - foldT) * (1.0 - foldT);
      float foldWeight = clamp(refractionFoldStrength * foldEnvelope, 0.0, 1.0);
      float foldDirection = -heightNorm / max(abs(heightNorm), 0.0001);
      float foldTarget = foldDirection * foldEnvelope;
      float effectiveHeightNorm = mix(heightNorm, foldTarget, foldWeight);
      float displacementMagnitude =
        effectiveHeightNorm * effectiveRefractionStrength * refractionScale;
      vec2 opticalGradient = opticalSurfaceGradient(localCoord, fieldWeight);
      vec2 displacementGradient = opticalGradient;
      if (fieldWeight < 1.0) {
        float gradientLength = length(opticalGradient);
        float centerFade = smootherstep(clamp(gradientLength / 0.5, 0.0, 1.0));
        vec2 normalizedGradient =
          opticalGradient / max(gradientLength, 0.0001) * centerFade;
        displacementGradient = mix(normalizedGradient, opticalGradient, fieldWeight);
      }
      return -displacementGradient * displacementMagnitude;
    }
  """

  private fun opticalHelpers(): String = """
    vec2 surfaceGradient(vec2 localCoord, float fieldWeight) {
      float left = surfaceHeight(
        clampMaterial(localCoord - vec2(sampleStep, 0.0)),
        fieldWeight
      );
      float right = surfaceHeight(
        clampMaterial(localCoord + vec2(sampleStep, 0.0)),
        fieldWeight
      );
      float up = surfaceHeight(
        clampMaterial(localCoord - vec2(0.0, sampleStep)),
        fieldWeight
      );
      float down = surfaceHeight(
        clampMaterial(localCoord + vec2(0.0, sampleStep)),
        fieldWeight
      );
      return vec2(right - left, down - up) * (0.5 / max(sampleStep, 0.0001));
    }

    vec2 surfaceLightingGradient(vec2 localCoord, float sd, float fieldWeight) {
      if (surfaceProfile == 1) {
        float refractionZone = max(refractionHeight, 0.0001);
        float opticalDistance = opticalDistanceFromSignedDistance(localCoord, sd, fieldWeight);
        float t = clamp(opticalDistance / refractionZone, 0.0, 1.0);
        float lightingSlope = 2.0 * (1.0 - smootherstep(t));
        return opticalSurfaceGradient(localCoord, fieldWeight) * lightingSlope;
      }
      return surfaceGradient(localCoord, fieldWeight);
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

    vec3 sampleChromaSimple(
      vec2 coord,
      vec2 chromaOffset,
      vec4 centerSample
    ) {
      if (length(chromaOffset) < 0.0001) return unpremultiply(centerSample);
      vec3 forward = unpremultiply(sampleDepth(coord + chromaOffset));
      vec3 backward = unpremultiply(sampleDepth(coord - chromaOffset));
      vec3 centerStraight = unpremultiply(centerSample);
      return vec3(forward.r, centerStraight.g, backward.b);
    }

    vec3 sampleChromaFull(
      vec2 coord,
      vec2 chromaOffset,
      vec4 centerSample
    ) {
      if (length(chromaOffset) < 0.0001) return unpremultiply(centerSample);
      vec3 red = unpremultiply(sampleDepth(coord + chromaOffset));
      vec3 orange =
        unpremultiply(sampleDepth(coord + chromaOffset * (2.0 / 3.0)));
      vec3 yellow =
        unpremultiply(sampleDepth(coord + chromaOffset * (1.0 / 3.0)));
      vec3 green = unpremultiply(centerSample);
      vec3 cyan =
        unpremultiply(sampleDepth(coord - chromaOffset * (1.0 / 3.0)));
      vec3 blue =
        unpremultiply(sampleDepth(coord - chromaOffset * (2.0 / 3.0)));
      vec3 purple = unpremultiply(sampleDepth(coord - chromaOffset));
      return vec3(
        red.r / 3.5 + orange.r / 3.5 + yellow.r / 3.5 + purple.r / 7.0,
        orange.g / 7.0 + yellow.g / 3.5 + green.g / 3.5 + cyan.g / 3.5,
        cyan.b / 3.0 + blue.b / 3.0 + purple.b / 3.0
      );
    }

    vec3 sampleChroma(
      vec2 coord,
      vec2 chromaOffset,
      vec4 centerSample
    ) {
      if (chromaticAberrationMode == 1) {
        return sampleChromaFull(coord, chromaOffset, centerSample);
      }
      return sampleChromaSimple(coord, chromaOffset, centerSample);
    }

    vec3 computeContentNormal(vec2 coord, vec4 centerSample) {
      float center = luma(unpremultiply(centerSample));
      float right = luma(
        unpremultiply(sampleDepth(coord + vec2(sampleStep, 0.0)))
      );
      float down = luma(
        unpremultiply(sampleDepth(coord + vec2(0.0, sampleStep)))
      );
      vec2 gradient =
        vec2(right - center, down - center) / max(sampleStep, 0.0001);
      return normalize(vec3(gradient, 1.0));
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
