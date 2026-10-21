// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

internal object GlassShaders {
  fun buildFused(
    interactionOptics: Boolean = false,
    sharpDetail: Boolean = true,
    merged: Boolean = false,
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
    uniform float edgeRefractionWidth;
    uniform float chromaticAberrationStrength;
    uniform vec4 cornerRadii;
    layout(color) uniform vec4 tintColor;
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

    ${sdfHelpers()}${mergedShapeHelpers(merged, tint = true)}${if (merged) MERGED_OPTICAL_HELPERS else ""}

    ${surfaceAndDisplacementHelpers(merged)}

    ${if (interactionOptics) interactionFalloffHelper() else ""}

    vec4 sampleDepth(vec2 coord) {
      return content.eval(clampSample(coord));
    }

    ${opticalHelpers(merged)}

    vec4 main(vec2 coord) {
      vec2 localCoord = materialCoord(coord);
      vec2 halfSize = materialSize * 0.5;
      vec2 centeredCoord = localCoord - halfSize;
      float outputSd = ${shapeSd(merged, "localCoord")};
      float coverage = shapeCoverage(outputSd, sampleStep * 0.5);
      if (coverage <= 0.0) return vec4(0.0);

      float outputDistToEdge = max(-outputSd, 0.0);
      float shapeMask = edgeSoftness <= 0.0
        ? 1.0
        : smootherstep(clamp(outputDistToEdge / max(edgeSoftness, 0.0001), 0.0, 1.0));
      ${if (merged) {
    """
      vec2 memberOptics = mergedOpticalValues(localCoord);
      float localizedRefractionMultiplier = memberOptics.x;
      float localizedWhitePoint = clamp(whitePoint + memberOptics.y, -1.0, 1.0);
      """
  } else if (interactionOptics) {
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
      float fieldWeight = ${fieldWeight(merged)};
      float opticalDistance =
        opticalDistanceFromSignedDistance(localCoord, outputSd, fieldWeight);
      float heightNorm = surfaceHeightNormFromOpticalDistance(opticalDistance);
      vec2 displacement = refractionDisplacement(
        localCoord,
        heightNorm,
        edgeRefractionWidth >= 0.0 ? outputDistToEdge : opticalDistance,
        ${if (interactionOptics || merged) "localizedRefractionMultiplier" else "1.0"},
        fieldWeight
      );
      vec2 refractCoord = clampSample(coord + displacement);

      float cornerWeight = ${cornerWeight(merged)};
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
        ${if (interactionOptics || merged) "localizedWhitePoint" else "whitePoint"}
      );
      gradedColor = clamp(gradedColor, 0.0, 1.0);
      ${tintedColor(merged)}
      vec4 opticalColor = premultiply(applyAmbient(tintedColor, ambient), refractedCenter.a);
      if (shapeMask < 1.0) {
        opticalColor = mix(sampleDepth(coord), opticalColor, shapeMask);
      }

      ${if (sharpDetail) {
    """
      float detailAlpha = 0.0;
      float maxPossibleDisplacement = min(
        ${if (interactionOptics || merged) {
      "abs(refractionScale * refractionStrength) * max(1.0, localizedRefractionMultiplier)"
    } else {
      "abs(refractionScale * refractionStrength)"
    }},
        length(sampleSize)
      );
      if (outputDistToEdge <= detailWidth + maxPossibleDisplacement) {
        vec2 refractedLocalCoord = localCoord + displacement;
        float refractedSd = ${shapeSd(merged, "refractedLocalCoord")};
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
      return opticalColor.a > 0.0 ? opticalColor * coverage : vec4(0.0);
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
      uniform float sourceIsOpaque;
      $tapUniforms

      vec2 clampSample(vec2 coord) {
        return clamp(coord, vec2(0.5), sampleSize - vec2(0.5));
      }

      vec4 main(vec2 coord) {
        float blurScale = ${if (progressive) "clamp(mask.eval(max(coord - materialOrigin, vec2(0.0)) * maskCoordinateScale).a, 0.0, 1.0)" else "1.0"};
        ${if (progressive) "if (blurScale <= 0.0001) { return content.eval(clampSample(coord)); }" else ""}
        vec4 result = content.eval(clampSample(coord)) * centerWeight;
        $samples
        // Runtime image filters can crop the child to the requested output, even with padded
        // captures. For an opaque source, lost alpha identifies unavailable taps. Normalize
        // the surviving samples rather than introducing a dark band that refraction pulls inwards.
        // This is a truncated-kernel boundary rule; it does not reconstruct missing colours.
        if (sourceIsOpaque > 0.5 && result.a > 0.0001) {
          return vec4(result.rgb / result.a, 1.0);
        }
        return result.a > 0.0 ? result : vec4(0.0);
      }
    """
  }

  fun buildOptical(
    interactive: Boolean = false,
    merged: Boolean = false,
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
    uniform float edgeRefractionWidth;
    uniform float chromaticAberrationStrength;
    uniform vec4 cornerRadii;
    layout(color) uniform vec4 tintColor;
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
    ${if (interactive) interactionUniforms(includeRefraction = true, includeWhitePoint = true, includeLighting = false) else ""}

    vec2 materialCoord(vec2 coord) { return coord - materialOrigin; }

    vec2 clampSample(vec2 coord) {
      return clamp(coord, vec2(0.5), sampleSize - vec2(0.5));
    }

    vec2 clampMaterial(vec2 coord) {
      return clamp(coord, vec2(0.0), materialSize);
    }

    ${sdfHelpers()}${mergedShapeHelpers(merged, tint = true)}${if (merged) MERGED_OPTICAL_HELPERS else ""}

    ${surfaceAndDisplacementHelpers(merged)}

    ${if (interactive) interactionFalloffHelper() else ""}

    vec4 sampleDepth(vec2 coord) {
      return content.eval(clampSample(coord));
    }

    ${opticalHelpers(merged)}

    vec4 main(vec2 coord) {
      vec2 localCoord = materialCoord(coord);
      vec2 halfSize = materialSize * 0.5;
      vec2 centeredCoord = localCoord - halfSize;
      float sd = ${shapeSd(merged, "localCoord")};
      float coverage = shapeCoverage(sd, sampleStep * 0.5);
      if (coverage <= 0.0) return vec4(0.0);

      float distToEdge = max(-sd, 0.0);
      float shapeMask = edgeSoftness <= 0.0
        ? 1.0
        : smootherstep(clamp(distToEdge / max(edgeSoftness, 0.0001), 0.0, 1.0));
      ${if (merged) {
    """
      vec2 memberOptics = mergedOpticalValues(localCoord);
      float localizedRefractionMultiplier = memberOptics.x;
      float localizedWhitePoint = clamp(whitePoint + memberOptics.y, -1.0, 1.0);
      """
  } else if (interactive) {
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

      float fieldWeight = ${fieldWeight(merged)};
      float opticalDistance =
        opticalDistanceFromSignedDistance(localCoord, sd, fieldWeight);
      float heightNorm = surfaceHeightNormFromOpticalDistance(opticalDistance);
      vec2 displacement = refractionDisplacement(
        localCoord,
        heightNorm,
        edgeRefractionWidth >= 0.0 ? distToEdge : opticalDistance,
        ${if (interactive || merged) "localizedRefractionMultiplier" else "1.0"},
        fieldWeight
      );
      vec2 refractCoord = clampSample(coord + displacement);

      float cornerWeight = ${cornerWeight(merged)};
      vec2 chromaOffset = displacement * chromaticAberrationStrength * 0.5 * cornerWeight;
      vec4 refractedCenterSample = sampleDepth(refractCoord);
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
        ${if (interactive || merged) "localizedWhitePoint" else "whitePoint"}
      );
      gradedColor = clamp(gradedColor, 0.0, 1.0);
      ${tintedColor(merged)}
      vec3 finalStraightColor = applyAmbient(tintedColor, ambient);
      vec4 processedColor = premultiply(finalStraightColor, refractedCenterSample.a);
      if (shapeMask >= 1.0) {
        return processedColor.a > 0.0 ? processedColor * coverage : vec4(0.0);
      }
      vec4 baseSample = content.eval(clampSample(coord));
      vec4 composedColor = mix(baseSample, processedColor, shapeMask);
      return composedColor.a > 0.0 ? composedColor * coverage : vec4(0.0);
    }
  """

  fun buildRefractionDetail(
    interactive: Boolean = false,
    coverageOnly: Boolean = false,
    merged: Boolean = false,
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
    uniform float edgeRefractionWidth;
    uniform vec4 cornerRadii;
    // Declared as float because AGSL does not support int uniforms.
    uniform float surfaceProfile;
    uniform float refractionScale;
    uniform float detailWidth;
    uniform float detailIntensity;
    uniform float detailVisibility;
    ${if (interactive) interactionUniforms(includeRefraction = true, includeWhitePoint = false, includeLighting = false) else ""}

    vec2 materialCoord(vec2 coord) { return coord - materialOrigin; }

    ${if (coverageOnly) {
    ""
  } else {
    """
    vec2 clampSample(vec2 coord) {
      return clamp(coord, vec2(0.5), sampleSize - vec2(0.5));
    }
    """
  }}

    ${sdfHelpers()}${mergedShapeHelpers(merged)}${if (merged) MERGED_OPTICAL_HELPERS else ""}

    ${surfaceAndDisplacementHelpers(merged)}

    ${if (interactive) interactionFalloffHelper() else ""}

    vec4 main(vec2 coord) {
      vec2 localCoord = materialCoord(coord);
      float outputSd = ${shapeSd(merged, "localCoord")};
      float coverage = shapeCoverage(outputSd, sampleStep * 0.5);
      if (coverage <= 0.0) return vec4(0.0);

      float outputDistToEdge = max(-outputSd, 0.0);
      ${if (merged) {
    """
      float localizedRefractionMultiplier = mergedOpticalValues(localCoord).x;
      """
  } else if (interactive) {
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
        abs(refractionScale * refractionStrength)${if (interactive || merged) " * max(1.0, localizedRefractionMultiplier)" else ""},
        sampleDiagonal
      );
      if (outputDistToEdge > detailWidth + maxPossibleDisplacement) return vec4(0.0);

      float fieldWeight = ${fieldWeight(merged)};
      float opticalDistance =
        opticalDistanceFromSignedDistance(localCoord, outputSd, fieldWeight);
      float heightNorm = surfaceHeightNormFromOpticalDistance(opticalDistance);
      vec2 displacement = refractionDisplacement(
        localCoord,
        heightNorm,
        edgeRefractionWidth >= 0.0 ? outputDistToEdge : opticalDistance,
        ${if (interactive || merged) "localizedRefractionMultiplier" else "1.0"},
        fieldWeight
      );
      ${if (coverageOnly) "" else "vec2 refractCoord = clampSample(coord + displacement);"}
      vec2 refractedLocalCoord = localCoord + displacement;
      float refractedSd = ${shapeSd(merged, "refractedLocalCoord")};
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

      ${if (coverageOnly) {
    // DstOut removes a fraction of an optical layer that already has shape coverage.
    // Applying coverage here too would leave excess optical colour at the boundary.
    "return vec4(vec3(detailAlpha), detailAlpha);"
  } else {
    """
      vec4 sharpSample = content.eval(refractCoord);
      vec4 detailColor = sharpSample * (detailAlpha * coverage);
      return detailColor.a > 0.0 ? detailColor : vec4(0.0);
      """
  }}
    }
  """

  fun buildInteractionLighting(merged: Boolean = false): String = """
    uniform shader content;
    uniform float2 materialOrigin;
    uniform float2 materialSize;
    uniform vec4 cornerRadii;
    uniform float edgeSoftness;
    ${if (merged) "" else interactionUniforms(includeRefraction = false, includeWhitePoint = false, includeLighting = true)}

    ${sdfHelpers()}${if (merged) MERGED_FIELD_HELPERS + MERGED_LIGHTING_HELPERS else ""}

    ${if (merged) "" else interactionFalloffHelper()}

    vec4 main(vec2 coord) {
      vec2 localCoord = coord - materialOrigin;
      float sd = ${shapeSd(merged, "localCoord")};
      if (sd > 0.0) return vec4(0.0);
      float shapeMask = edgeSoftness <= 0.0
        ? 1.0
        : smootherstep(clamp(max(-sd, 0.0) / max(edgeSoftness, 0.0001), 0.0, 1.0));
      float light = ${if (merged) "memberLight(localCoord)" else "interactionFalloff(coord) * interactionLightingIntensity"} * shapeMask;
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

  fun buildRim(merged: Boolean = false): String = """
    uniform shader content;
    uniform float2 sampleSize;
    uniform float2 materialOrigin;
    uniform float2 materialSize;
    uniform float sampleStep;
    uniform vec4 cornerRadii;
    uniform float specularIntensity;
    layout(color) uniform vec4 edgeShadow;
    uniform float specularExponent;
    uniform float edgeSoftness;
    uniform float2 lightPosition;

    vec2 materialCoord(vec2 coord) { return coord - materialOrigin; }

    vec2 clampMaterial(vec2 coord) {
      return clamp(coord, vec2(0.0), materialSize);
    }

    ${sdfShapeHelpers()}${mergedShapeHelpers(merged)}

    float materialSdf(vec2 localCoord) {
      return ${shapeSd(merged, "localCoord")};
    }

    vec2 sdfGradient(vec2 localCoord) {
      float left = materialSdf(${clampMaterial(merged, "localCoord - vec2(sampleStep, 0.0)")});
      float right = materialSdf(${clampMaterial(merged, "localCoord + vec2(sampleStep, 0.0)")});
      float up = materialSdf(${clampMaterial(merged, "localCoord - vec2(0.0, sampleStep)")});
      float down = materialSdf(${clampMaterial(merged, "localCoord + vec2(0.0, sampleStep)")});
      return vec2(right - left, down - up) * (0.5 / max(sampleStep, 0.0001));
    }

    vec4 main(vec2 coord) {
      vec2 localCoord = materialCoord(coord);
      float sd = materialSdf(localCoord);
      float coverage = shapeCoverage(sd, sampleStep * 0.5);
      if (coverage <= 0.0) return vec4(0.0);

      float edgeWidth = max(edgeSoftness, sampleStep);
      float edge = 1.0 - smootherstep(clamp(-sd / max(edgeWidth, 0.0001), 0.0, 1.0));
      if (edge <= 0.0) return vec4(0.0);
      vec2 gradient = sdfGradient(localCoord);
      vec3 normal = normalize(vec3(-gradient.x, -gradient.y, 1.0));
      vec2 lightDirection2D = safeNormalize(lightPosition - localCoord, vec2(0.0, -1.0));
      vec3 lightDirection = normalize(vec3(lightDirection2D, 1.0));
      float specularBase = max(dot(normal, lightDirection), 0.0);
      float specular = specularExponent == 0.0 ? 1.0 : pow(specularBase, specularExponent);
      float specularAlpha = specular * specularIntensity * edge;
      float shadowAlpha = edgeShadow.a * edge;
      float alpha = specularAlpha + shadowAlpha * (1.0 - specularAlpha);
      vec3 color = vec3(specularAlpha) + edgeShadow.rgb * shadowAlpha * (1.0 - specularAlpha);
      return alpha > 0.0 ? vec4(color, alpha) * coverage : vec4(0.0);
    }
  """

  /** Fills the source with each merged member's background colour, blended across bridges. */
  fun buildMergedBackground(): String = """
    uniform float2 materialOrigin;
    layout(color) uniform vec4 memberBackground0;
    layout(color) uniform vec4 memberBackground1;
    layout(color) uniform vec4 memberBackground2;
    layout(color) uniform vec4 memberBackground3;

    ${sdfShapeHelpers()}
    $MERGED_FIELD_HELPERS

    vec4 main(vec2 coord) {
      vec4 color = blendMemberColors(
        memberWeights(coord - materialOrigin),
        memberBackground0,
        memberBackground1,
        memberBackground2,
        memberBackground3
      );
      return vec4(color.rgb * color.a, color.a);
    }
  """

  private fun sdfHelpers(): String = """
    ${sdfShapeHelpers()}

    ${sdfGradientHelpers()}
  """

  private fun sdfShapeHelpers(): String = """
    float shapeCoverage(float sd, float pixelWidth) {
      // Pixel coverage is independent of the authored optical edge softness and density.
      // sampleStep is two physical pixels expressed in the current working coordinates.
      // Keep the transition inside the existing silhouette, including caller-applied clips.
      return clamp(-sd / max(pixelWidth, 0.0001), 0.0, 1.0);
    }

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

  private fun surfaceAndDisplacementHelpers(merged: Boolean = false): String = """
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

    float opticalFieldWeight(vec2 size, float height) {
      float shortestSide = max(min(size.x, size.y), 0.0001);
      float aspectRatio = max(size.x, size.y) / shortestSide;
      float elongation = smootherstep(clamp((aspectRatio - 1.5) / 1.5, 0.0, 1.0));
      float overlap = smootherstep(clamp((height / (shortestSide * 0.5) - 0.75) / 0.25, 0.0, 1.0));
      return elongation * overlap;
    }

    float opticalFieldWeight() { return opticalFieldWeight(materialSize, refractionHeight); }

    vec2 normalizedMaterialCoord(vec2 localCoord, vec2 size) {
      vec2 halfSize = max(size * 0.5, vec2(0.0001));
      return (localCoord - halfSize) / halfSize;
    }

    float domeDistance(vec2 localCoord, vec2 size, float height) {
      vec2 normalized = normalizedMaterialCoord(localCoord, size);
      vec2 squared = normalized * normalized;
      float radius = sqrt(clamp(squared.x + squared.y - squared.x * squared.y, 0.0, 1.0));
      return max((1.0 - radius) * height, 0.0);
    }

    float opticalDistance(vec2 p, float sd, float weight, vec2 size, float height) {
      float edge = max(-sd, 0.0);
      if (weight <= 0.0) return edge;
      return mix(edge, domeDistance(p, size, height), weight);
    }

    float opticalDistanceFromSignedDistance(vec2 p, float sd, float weight) {
      return opticalDistance(p, sd, weight, materialSize, refractionHeight);
    }

    float surfaceHeightFromDistance(float distance, float height) {
      float zone = max(height, 0.0001);
      return evaluateProfile(clamp(distance / zone, 0.0, 1.0)) * zone;
    }

    float surfaceHeightFromOpticalDistance(float distance) {
      return surfaceHeightFromDistance(distance, refractionHeight);
    }

    float rectSurfaceHeight(vec2 p, vec2 size, vec4 radii, float height, float weight) {
      if (weight >= 1.0) return surfaceHeightFromDistance(domeDistance(p, size, height), height);
      float sd = sdRoundedRect(p, size, radii);
      return surfaceHeightFromDistance(opticalDistance(p, sd, weight, size, height), height);
    }

    float surfaceHeight(vec2 p, float weight) {
      if (weight >= 1.0) return surfaceHeightFromOpticalDistance(domeDistance(p, materialSize, refractionHeight));
      float sd = ${if (merged) "shapeSd(p)" else "sdRoundedRect(p, materialSize, cornerRadii)"};
      return surfaceHeightFromOpticalDistance(opticalDistanceFromSignedDistance(p, sd, weight));
    }

    float surfaceHeightNormFromOpticalDistance(float distance) {
      return clamp(surfaceHeightFromOpticalDistance(distance) / max(refractionHeight, 0.0001), -1.0, 1.0);
    }

    vec2 rectOpticalGradient(vec2 p, vec2 size, vec4 radii, float height, float weight) {
      vec2 normalized = normalizedMaterialCoord(p, size);
      vec2 squared = normalized * normalized;
      vec2 dome = vec2(normalized.x * (1.0 - squared.y), normalized.y * (1.0 - squared.x));
      if (weight >= 1.0) return dome;
      vec2 boundary = gradSdRoundedRect(p, size, radii, max(height, 1.0));
      return mix(boundary, dome, weight);
    }

    vec2 opticalSurfaceGradient(vec2 p, float weight) {
      return ${if (merged) "shapeGradient(p)" else "rectOpticalGradient(p, materialSize, cornerRadii, refractionHeight, weight)"};
    }

    float refractionFoldEnvelope(float opticalDistance, float height) {
      float foldWidth = max(
        edgeRefractionWidth >= 0.0 ? edgeRefractionWidth : height, sampleStep
      );
      float foldT = clamp(opticalDistance / foldWidth, 0.0, 1.0);
      float foldRise = smootherstep(clamp(foldT / 0.2, 0.0, 1.0));
      float foldFall = 1.0 - smootherstep(clamp((foldT - 0.25) / 0.35, 0.0, 1.0));
      return foldRise * foldFall;
    }

    float foldedRefractionHeightNorm(float heightNorm, float opticalDistance, float height) {
      if (refractionFoldStrength <= 0.0) return heightNorm;
      float foldEnvelope = refractionFoldEnvelope(opticalDistance, height);
      float foldWeight = clamp(refractionFoldStrength * foldEnvelope, 0.0, 1.0);
      // Reverse the sampling derivative without reversing displacement through zero.
      float foldDirection = edgeRefractionWidth < 0.0 && surfaceProfile == 2 ? -1.0 : 1.0;
      float foldTarget = foldDirection * abs(heightNorm) * 0.02;
      return mix(heightNorm, foldTarget, foldWeight);
    }

    vec2 edgeRefractionGradient(vec2 localCoord, vec2 size, vec4 customRadii) {
      // The cap keeps the optical corner regions disjoint, allowing one analytic arc normal.
      vec4 radii = min(customRadii * 1.5, vec4(min(size.x, size.y) * 0.5));
      vec2 delta = vec2(0.0);
      bool corner = false;
      if (localCoord.x < radii.x && localCoord.y < radii.x) {
        delta = localCoord - vec2(radii.x);
        corner = true;
      } else if (localCoord.x > size.x - radii.y && localCoord.y < radii.y) {
        delta = localCoord - vec2(size.x - radii.y, radii.y);
        corner = true;
      } else if (localCoord.x > size.x - radii.z &&
          localCoord.y > size.y - radii.z) {
        delta = localCoord - vec2(size.x - radii.z, size.y - radii.z);
        corner = true;
      } else if (localCoord.x < radii.w && localCoord.y > size.y - radii.w) {
        delta = localCoord - vec2(radii.w, size.y - radii.w);
        corner = true;
      }
      float blendWidth = max(sampleStep * 0.5, 0.01);
      if (!corner) return gradSdRectangle(localCoord, size, blendWidth);
      float cornerDistance = length(delta);
      float axisDistance = min(abs(delta.x), abs(delta.y));
      if (axisDistance >= blendWidth) return delta / cornerDistance;
      // Join the straight-edge field at both corner axes, including their undefined arc centre.
      // Away from this one-sample transition the normal is the exact analytic radial direction.
      float cornerWeight = smootherstep(clamp(axisDistance / blendWidth, 0.0, 1.0));
      return mix(
        gradSdRectangle(localCoord, size, blendWidth),
        delta / max(cornerDistance, 0.0001),
        cornerWeight
      );
    }

    ${if (merged) MERGED_REFRACTION_GRADIENT else ""}vec2 evaluateRefraction(
      vec2 localCoord,
      float heightNorm,
      float opticalDistance,
      float refractionMultiplier,
      float fieldWeight,
      vec2 size,
      vec4 radii,
      float height,
      bool mergedField
    ) {
      float effectiveRefractionStrength =
        clamp(refractionStrength * refractionMultiplier, 0.0, 1.0);
      if (edgeRefractionWidth >= 0.0) {
        if (edgeRefractionWidth <= 0.0 || opticalDistance >= edgeRefractionWidth) return vec2(0.0);
        float t = clamp(opticalDistance / edgeRefractionWidth, 0.0, 1.0);
        float cutoff = max(1.0 - t * t, 0.0);
        // Empirical Clear fit: rapid boundary decay with a smooth finite-width cutoff.
        float profile = exp(-t / 0.225) * cutoff * cutoff;
        float magnitude = foldedRefractionHeightNorm(profile, opticalDistance, height) * min(
          effectiveRefractionStrength * refractionScale,
          ${if (merged) "mergedField ? mergeMaxDisplacement(localCoord) : min(size.x, size.y) * 0.5" else "min(size.x, size.y) * 0.5"}
        );
        vec2 gradient = edgeRefractionGradient(localCoord, size, radii);
        ${if (merged) {
    """
        if (mergedField) {
          gradient = shapeRefractionGradient(localCoord);
          magnitude *= shapeThicknessWeight(localCoord, gradient);
        }
        """
  } else {
    ""
  }}
        float gradientLength = length(gradient);
        float centerFade = smootherstep(clamp(gradientLength / 0.5, 0.0, 1.0));
        return -gradient / max(gradientLength, 0.0001) * centerFade * magnitude;
      }
      float effectiveHeightNorm =
        foldedRefractionHeightNorm(heightNorm, opticalDistance, height);
      float displacementMagnitude =
        effectiveHeightNorm * effectiveRefractionStrength * refractionScale;
      vec2 opticalGradient = ${if (merged) "mergedField ? shapeGradient(localCoord) :" else ""}
        rectOpticalGradient(localCoord, size, radii, height, fieldWeight);
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
    vec2 refractionDisplacement(vec2 p, float heightNorm, float distance, float multiplier, float weight) {
      vec2 mergedResult = evaluateRefraction(
        p, heightNorm, distance, multiplier, weight, materialSize, cornerRadii, refractionHeight, $merged
      );
      ${if (merged) {
    """
      if (dot(memberStandaloneWeights, vec4(1.0)) <= 0.0) return mergedResult;
      vec4 shares = memberWeights(p) * memberStandaloneWeights;
      vec2 result = mergedResult;
      for (int i = 0; i < 4; i++) {
        if (float(i) >= memberCount) break;
        if (shares[i] <= 0.0) continue;
        vec4 rect = memberRects[i];
        vec2 size = memberLayoutSizes[i];
        vec2 scale = rect.zw / size;
        // Continue each standalone profile from its boundary into the emerging bridge.
        vec2 local = clamp((p - rect.xy) / scale, vec2(0.0), size);
        vec4 radii = memberRadii[i] / min(scale.x, scale.y);
        float field = opticalFieldWeight(size, refractionHeight);
        float sd = sdRoundedRect(local, size, radii);
        float optical = opticalDistance(local, sd, field, size, refractionHeight);
        float height = surfaceHeightNormFromOpticalDistance(optical);
        vec2 standalone = evaluateRefraction(
          local, height, edgeRefractionWidth >= 0.0 ? max(-sd, 0.0) : optical,
          multiplier, field, size, radii, refractionHeight, false
        ) * scale;
        result += shares[i] * (standalone - mergedResult);
      }
      return result;
      """
  } else {
    "return mergedResult;"
  }}
    }
  """

  private fun opticalHelpers(merged: Boolean): String = """
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

    vec2 mergedSurfaceLightingGradient(vec2 localCoord, float sd, float fieldWeight) {
      if (surfaceProfile == 1) {
        float refractionZone = max(refractionHeight, 0.0001);
        float opticalDistance = opticalDistanceFromSignedDistance(localCoord, sd, fieldWeight);
        float t = clamp(opticalDistance / refractionZone, 0.0, 1.0);
        float lightingSlope = 2.0 * (1.0 - smootherstep(t));
        return opticalSurfaceGradient(localCoord, fieldWeight) * lightingSlope;
      }
      return surfaceGradient(localCoord, fieldWeight);
    }

    vec2 surfaceLightingGradient(vec2 p, float sd, float weight) {
      vec2 result = mergedSurfaceLightingGradient(p, sd, weight);
      ${if (merged) {
    """
      if (dot(memberStandaloneWeights, vec4(1.0)) <= 0.0) return result;
      vec4 shares = memberWeights(p) * memberStandaloneWeights;
      vec2 blended = result;
      for (int i = 0; i < 4; i++) {
        if (float(i) >= memberCount) break;
        if (shares[i] <= 0.0) continue;
        vec4 rect = memberRects[i];
        vec2 size = memberLayoutSizes[i];
        vec2 scale = rect.zw / size;
        vec2 local = clamp((p - rect.xy) / scale, vec2(0.0), size);
        vec4 radii = memberRadii[i] / min(scale.x, scale.y);
        float height = refractionHeight;
        float field = opticalFieldWeight(size, height);
        vec2 standalone;
        if (surfaceProfile == 1) {
          float sd = sdRoundedRect(local, size, radii);
          float distance = opticalDistance(local, sd, field, size, height);
          float slope = 2.0 * (1.0 - smootherstep(clamp(distance / max(height, 0.0001), 0.0, 1.0)));
          standalone = rectOpticalGradient(local, size, radii, height, field) * slope;
        } else {
          vec2 dx = vec2(sampleStep, 0.0);
          vec2 dy = vec2(0.0, sampleStep);
          standalone = vec2(
            rectSurfaceHeight(clamp(local + dx, vec2(0.0), size), size, radii, height, field) -
              rectSurfaceHeight(clamp(local - dx, vec2(0.0), size), size, radii, height, field),
            rectSurfaceHeight(clamp(local + dy, vec2(0.0), size), size, radii, height, field) -
              rectSurfaceHeight(clamp(local - dy, vec2(0.0), size), size, radii, height, field)
          ) * (0.5 / max(sampleStep, 0.0001));
        }
        blended += shares[i] * (standalone - result);
      }
      return blended;
      """
  } else {
    "return result;"
  }}
    }

    vec3 unpremultiply(vec4 color) {
      return color.a > 0.0001 ? color.rgb / color.a : vec3(0.0);
    }

    vec3 applyAmbient(vec3 color, float ambient) {
      // Bound generated SDR highlights before coverage/alpha, while preserving the existing
      // response of colour-managed tints with legitimate extended-range components.
      bool inputIsSdr = min(color.r, min(color.g, color.b)) >= 0.0 &&
        max(color.r, max(color.g, color.b)) <= 1.0;
      vec3 litColor = color * ambient;
      return inputIsSdr ? clamp(litColor, 0.0, 1.0) : litColor;
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

    vec3 applyColorGrading(vec3 color, float appliedWhitePoint) {
      if (chromaMultiplier != 1.0) {
        vec3 linearColor = toLinearSrgb(color);
        float luminance = dot(linearColor, vec3(0.2126, 0.7152, 0.0722));
        float chromaScale = chromaMultiplier;
        if (chromaMultiplier > 1.0) {
          bool sourceIsInSrgbGamut =
            min(linearColor.r, min(linearColor.g, linearColor.b)) >= 0.0 &&
              max(linearColor.r, max(linearColor.g, linearColor.b)) <= 1.0;
          if (sourceIsInSrgbGamut) {
            vec3 delta = linearColor - vec3(luminance);
            if (delta.r > 0.0) chromaScale = min(chromaScale, (1.0 - luminance) / delta.r);
            if (delta.r < 0.0) chromaScale = min(chromaScale, -luminance / delta.r);
            if (delta.g > 0.0) chromaScale = min(chromaScale, (1.0 - luminance) / delta.g);
            if (delta.g < 0.0) chromaScale = min(chromaScale, -luminance / delta.g);
            if (delta.b > 0.0) chromaScale = min(chromaScale, (1.0 - luminance) / delta.b);
            if (delta.b < 0.0) chromaScale = min(chromaScale, -luminance / delta.b);
            chromaScale = max(1.0, chromaScale);
          } else {
            chromaScale = 1.0;
          }
        }
        color = fromLinearSrgb(mix(vec3(luminance), linearColor, chromaScale));
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

  private fun shapeSd(merged: Boolean, coord: String): String =
    if (merged) "shapeSd($coord)" else "sdRoundedRect($coord, materialSize, cornerRadii)"

  // Chroma is strongest at a member's corners, so merged fields weight by the nearest member.
  private fun cornerWeight(merged: Boolean): String = if (merged) {
    "memberCornerWeight(localCoord)"
  } else {
    "abs(\n        (centeredCoord.x * centeredCoord.y) / max(halfSize.x * halfSize.y, 0.001)\n      )"
  }

  // The merged response uses the SDF; member-local standalone optics fade into it before bridging.
  private fun fieldWeight(merged: Boolean): String =
    if (merged) "0.0" else "opticalFieldWeight()"

  // The container contains the merged field, so merged gradients sample without clamping.
  private fun clampMaterial(merged: Boolean, coord: String): String =
    if (merged) coord else "clampMaterial($coord)"

  // A central difference over half the refraction width smooths the merged refraction direction
  // where it turns across a bridge's saddle.
  private const val MERGED_REFRACTION_GRADIENT = """vec2 shapeRefractionGradient(vec2 localCoord) {
      return shapeGradientOver(localCoord, max(sampleStep, edgeRefractionWidth * 0.5));
    }

    // 1 where the glass is at least a refraction width deep along the inward normal, falling to 0
    // where a thin bridge would carry the sample across to its opposite edge.
    float shapeThicknessWeight(vec2 localCoord, vec2 gradient) {
      float width = max(edgeRefractionWidth, 0.0001);
      vec2 inward = -safeNormalize(gradient, vec2(0.0));
      float depthAhead = -shapeSd(localCoord + inward * width);
      return smootherstep(clamp(depthAhead / width, 0.0, 1.0));
    }

    """

  private fun tintedColor(merged: Boolean): String = if (merged) {
    "vec4 mergedTint = blendMemberColors(memberWeights(localCoord), memberTint0, memberTint1, memberTint2, memberTint3);\n" +
      "      vec3 tintedColor = mix(gradedColor, mergedTint.rgb, mergedTint.a);"
  } else {
    "vec3 tintedColor = mix(gradedColor, tintColor.rgb, tintColor.a);"
  }

  private fun mergedShapeHelpers(merged: Boolean, tint: Boolean = false): String = when {
    !merged -> ""
    tint -> MERGED_MEMBER_TINTS + MERGED_FIELD_HELPERS + MERGED_OPTICS_HELPERS
    else -> MERGED_FIELD_HELPERS + MERGED_OPTICS_HELPERS
  }

  private const val MERGED_MEMBER_TINTS = """
    layout(color) uniform vec4 memberTint0;
    layout(color) uniform vec4 memberTint1;
    layout(color) uniform vec4 memberTint2;
    layout(color) uniform vec4 memberTint3;
    """

  /**
   * Up to four rounded members joined by a polynomial smooth minimum (ADR-0013). The midpoint
   * field of two members is `gap / 2 - k / 4`, so `k = spacing` bridges while the gap is under half
   * the spacing. `memberSmoothing[i]` already fades to zero as member i overlaps an earlier member.
   */
  private const val MERGED_FIELD_HELPERS = """
    uniform float4 memberRects[4];
    uniform float4 memberRadii[4];
    uniform float memberSmoothing[4];
    // Declared as float because AGSL does not support int uniforms.
    uniform float memberCount;

    float smoothMin(float a, float b, float k) {
      if (k <= 0.0) return min(a, b);
      float h = max(k - abs(a - b), 0.0) / k;
      return min(a, b) - h * h * k * 0.25;
    }

    float memberSd(vec2 localCoord, float4 rect, float4 radii) {
      return sdRoundedRect(localCoord - rect.xy, rect.zw, radii);
    }

    float shapeSd(vec2 localCoord) {
      float d = memberSd(localCoord, memberRects[0], memberRadii[0]);
      for (int i = 1; i < 4; i++) {
        if (float(i) >= memberCount) break;
        d = smoothMin(d, memberSd(localCoord, memberRects[i], memberRadii[i]), memberSmoothing[i]);
      }
      return d;
    }

    // Each member's share of the smooth minimum, independent of member order: members within the
    // largest smoothing radius of the nearest one share it, and otherwise the nearest one wins.
    vec4 memberWeights(vec2 localCoord) {
      vec4 d = vec4(0.0);
      float nearest = memberSd(localCoord, memberRects[0], memberRadii[0]);
      float k = 0.001;
      d[0] = nearest;
      for (int i = 1; i < 4; i++) {
        if (float(i) >= memberCount) break;
        d[i] = memberSd(localCoord, memberRects[i], memberRadii[i]);
        nearest = min(nearest, d[i]);
        k = max(k, memberSmoothing[i]);
      }
      vec4 w = vec4(0.0);
      float total = 0.0;
      for (int i = 0; i < 4; i++) {
        if (float(i) >= memberCount) break;
        float share = max(k - (d[i] - nearest), 0.0);
        w[i] = share * share;
        total += w[i];
      }
      return w / max(total, 0.000001);
    }

    // Blends unpremultiplied colours in premultiplied space, so a transparent member fades out.
    vec4 blendMemberColors(vec4 w, vec4 c0, vec4 c1, vec4 c2, vec4 c3) {
      vec4 p = w.x * vec4(c0.rgb * c0.a, c0.a) + w.y * vec4(c1.rgb * c1.a, c1.a) +
        w.z * vec4(c2.rgb * c2.a, c2.a) + w.w * vec4(c3.rgb * c3.a, c3.a);
      return p.a > 0.0001 ? vec4(p.rgb / p.a, p.a) : vec4(0.0);
    }

    float4 nearestMemberRect(vec2 localCoord) {
      float4 rect = memberRects[0];
      float nearest = memberSd(localCoord, memberRects[0], memberRadii[0]);
      for (int i = 1; i < 4; i++) {
        if (float(i) >= memberCount) break;
        float d = memberSd(localCoord, memberRects[i], memberRadii[i]);
        if (d < nearest) {
          nearest = d;
          rect = memberRects[i];
        }
      }
      return rect;
    }
    """

  /**
   * Each member's light, weighted by that member's share of the field, so a highlight stays on its
   * own member and blends across a bridge. Positions and radii are in material-local pixels.
   */
  private const val MERGED_LIGHTING_HELPERS = """
    uniform float2 memberLightPositions[4];
    uniform float memberLightRadii[4];
    uniform float memberLightIntensities[4];

    float memberLight(vec2 localCoord) {
      vec4 w = memberWeights(localCoord);
      float light = 0.0;
      for (int i = 0; i < 4; i++) {
        if (float(i) >= memberCount) break;
        float normalized = distance(localCoord, memberLightPositions[i]) / max(memberLightRadii[i], 0.0001);
        light += w[i] * memberLightIntensities[i] * (1.0 - smootherstep(clamp(normalized, 0.0, 1.0)));
      }
      return light;
    }
    """

  /** Member-local optical response, weighted by field ownership and the existing radial falloff. */
  private const val MERGED_OPTICAL_HELPERS = """
    uniform float2 memberOpticalPositions[4];
    uniform float memberOpticalRadii[4];
    uniform float memberRefractionMultipliers[4];
    uniform float memberWhitePointDeltas[4];

    uniform float2 memberLayoutSizes[4];
    uniform float4 memberStandaloneWeights;

    vec2 mergedOpticalValues(vec2 localCoord) {
      vec4 w = memberWeights(localCoord);
      float multiplierDelta = 0.0;
      float whitePointDelta = 0.0;
      for (int i = 0; i < 4; i++) {
        if (float(i) >= memberCount) break;
        float normalized = distance(localCoord, memberOpticalPositions[i]) /
          max(memberOpticalRadii[i], 0.0001);
        float interactionWeight = 1.0 - smootherstep(clamp(normalized, 0.0, 1.0));
        float weight = w[i] * interactionWeight;
        multiplierDelta += weight * (memberRefractionMultipliers[i] - 1.0);
        whitePointDelta += weight * memberWhitePointDeltas[i];
      }
      return vec2(clamp(1.0 + multiplierDelta, 0.0, 2.0), whitePointDelta);
    }
    """

  private const val MERGED_OPTICS_HELPERS = """
    vec2 shapeGradientOver(vec2 localCoord, float step) {
      float delta = max(step, 0.0001);
      vec2 dx = vec2(delta, 0.0);
      vec2 dy = vec2(0.0, delta);
      return vec2(
        shapeSd(localCoord + dx) - shapeSd(localCoord - dx),
        shapeSd(localCoord + dy) - shapeSd(localCoord - dy)
      ) * (0.5 / delta);
    }

    vec2 shapeGradient(vec2 localCoord) {
      return shapeGradientOver(localCoord, sampleStep);
    }

    float memberCornerWeight(vec2 localCoord) {
      float4 member = nearestMemberRect(localCoord);
      vec2 memberHalfSize = member.zw * 0.5;
      vec2 memberCentered = localCoord - member.xy - memberHalfSize;
      return abs(
        (memberCentered.x * memberCentered.y) / max(memberHalfSize.x * memberHalfSize.y, 0.001)
      );
    }

    // Each member caps displacement at half its own shortest side. Blending those caps by the
    // members' field weights keeps a large member's cap away from a small one, and stays
    // continuous across a bridge.
    float mergeMaxDisplacement(vec2 localCoord) {
      vec4 w = memberWeights(localCoord);
      float cap = 0.0;
      for (int i = 0; i < 4; i++) {
        if (float(i) >= memberCount) break;
        cap += w[i] * min(memberRects[i].z, memberRects[i].w) * 0.5;
      }
      return cap;
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
