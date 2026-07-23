// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shader
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import kotlin.test.Test

class GlassShadersTest {

  @Test
  fun blurShaders_shareSeparablePremultipliedClampContract() {
    val kernel = SemanticBlurKernel.create(38.5f)
    val horizontal = GlassShaders.buildBlur(horizontal = true)
    val vertical = GlassShaders.buildBlur(horizontal = false)

    listOf(horizontal, vertical).forEach { shader ->
      assertThat(shader).contains("uniform shader content;")
      assertThat(shader).contains("uniform float2 sampleSize;")
      assertThat(shader).contains("clamp(coord, vec2(0.5), sampleSize - vec2(0.5))")
      assertThat(shader).contains("vec4 result = content.eval(clampSample(coord))")
      assertThat(shader).contains("return result.a > 0.0 ? result : vec4(0.0);")
      assertThat(shader).doesNotContain("unpremultiply")
    }
    assertThat(horizontal).contains("vec2(")
    assertThat(horizontal).contains(", 0.0)")
    assertThat(vertical).contains("vec2(0.0, ")
  }

  @Test
  fun blurShader_hasConservativeUniformAndEvaluationCounts() {
    val shader = GlassShaders.buildBlur(horizontal = true)
    val scalarUniforms = shader.lineSequence().count { it.trim().startsWith("uniform float ") }
    val evaluations = "content.eval".toRegex().findAll(shader).count()
    assertThat(scalarUniforms).isLessThanOrEqualTo(43)
    assertThat(evaluations).isLessThanOrEqualTo(41)
    assertThat(shader.length).isLessThanOrEqualTo(10_000)
    assertThat(
      2 * (1 + 2 * SemanticBlurKernel.MAX_TAP_PAIRS),
    ).isLessThanOrEqualTo(82)
  }

  @Test
  fun prefilter_isBoundedNineTapLowPass() {
    val shader = GlassShaders.buildDownsamplePrefilter()

    assertThat(Regex("content\\.eval").findAll(shader).count()).isEqualTo(9)
    assertThat(shader).contains("0.25")
    assertThat(shader).contains("clampSample")
  }

  @Test
  fun progressiveBlurShaders_scaleTheSameKernelWithTheMask() {
    val shader = GlassShaders.buildBlur(horizontal = true, progressive = true)

    assertThat(shader).contains("uniform shader mask;")
    assertThat(shader).contains("uniform float maskCoordinateScale;")
    assertThat(shader).contains(
      "mask.eval(max(coord - materialOrigin, vec2(0.0)) * maskCoordinateScale).a",
    )
    assertThat(shader).contains("if (blurScale <= 0.0001)")
    assertThat(shader).contains("offset0 * blurScale")
  }

  @Test
  fun opticalShader_hasOneInputAndMaterialCoordinates() {
    val shader = GlassShaders.buildOptical()

    assertThat(shader).contains("uniform shader content;")
    assertThat(shader).contains("uniform float2 sampleSize;")
    assertThat(shader).contains("uniform float2 materialOrigin;")
    assertThat(shader).contains("uniform float2 materialSize;")
    assertThat(shader).doesNotContain("uniform float depth;")
  }

  @Test
  fun opticalShader_hasNoFlatInteriorBranch() {
    assertThat(GlassShaders.buildOptical())
      .doesNotContain("if (distToEdge >= refractionZone)")
  }

  @Test
  fun opticalShader_hardClipsAndClearsTransparentRgb() {
    val shader = GlassShaders.buildOptical()
    val main = shader.substringAfter("vec4 main(vec2 coord)")

    assertThat(shader).contains("if (sd > 0.0) return vec4(0.0);")
    assertThat(shader)
      .contains("return composedColor.a > 0.0 ? composedColor : vec4(0.0);")
    assertThat(main.substringBefore("content.eval("))
      .contains("if (sd > 0.0) return vec4(0.0);")
  }

  @Test
  fun opticalShader_avoidsSkiaReservedOutputName() {
    assertThat(GlassShaders.buildOptical()).doesNotContain("vec4 output")
  }

  @Test
  fun opticalShaders_softEdgeInterpolatePremultipliedReplacement() {
    listOf(
      GlassShaders.buildOptical(),
      GlassShaders.buildOptical(interactive = true),
    ).forEach { shader ->
      assertThat(shader).contains(
        "vec4 composedColor = mix(baseSample, processedColor, shapeMask);",
      )
      assertThat(shader).doesNotContain(
        "coveredColor + baseSample * (1.0 - coveredColor.a)",
      )
    }
  }

  @Test
  fun opticalShaders_gammaConversionNeverRaisesNegativeLinearChannelsToFractionalPower() {
    listOf(
      GlassShaders.buildOptical(),
      GlassShaders.buildOptical(interactive = true),
    ).forEach { shader ->
      assertThat(shader).contains("vec3 nonNegative = max(color, vec3(0.0));")
      assertThat(shader).contains("pow(nonNegative, vec3(1.0 / 2.4))")
      assertThat(shader).doesNotContain("pow(color, vec3(1.0 / 2.4))")
    }
  }

  @Test
  fun opticalShader_usesMaterialCoordinatesForGeometryAndSampleCoordinatesForContent() {
    val shader = GlassShaders.buildOptical()

    assertThat(shader).contains("vec2 materialCoord(vec2 coord) { return coord - materialOrigin; }")
    assertThat(shader).contains("vec2 localCoord = materialCoord(coord);")
    assertThat(shader).contains("vec2 centeredCoord = localCoord - halfSize;")
    assertThat(shader).contains("content.eval(clampSample(")
    assertThat(shader).doesNotContain("content.eval(materialCoord(")
    shader.lineSequence()
      .filter { "content.eval(" in it }
      .forEach { line -> assertThat(line).contains("content.eval(clampSample(") }
  }

  @Test
  fun opticalShader_usesConfiguredSampleStepForDerivativeAndNormalSamples() {
    val shader = GlassShaders.buildOptical()

    assertThat(shader).contains("uniform float sampleStep;")
    assertThat(shader).contains("localCoord - vec2(sampleStep, 0.0)")
    assertThat(shader).contains("localCoord + vec2(0.0, sampleStep)")
    assertThat(shader).contains("coord - vec2(sampleStep, 0.0)")
    assertThat(shader).contains("coord + vec2(0.0, sampleStep)")
    assertThat(shader).contains("* (0.5 / max(sampleStep, 0.0001))")
    assertThat(shader).doesNotContain("float sampleStep = 2.0;")
  }

  @Test
  fun opticalShader_unpremultipliesSamplesAndRepremultipliesWithRefractedCenterAlpha() {
    val shader = GlassShaders.buildOptical()

    assertThat(shader).contains("vec3 unpremultiply(vec4 color)")
    assertThat(shader).contains("vec4 premultiply(vec3 color, float alpha)")
    assertThat(shader).contains("unpremultiply(content.eval(")
    assertThat(shader).contains("vec4 baseSample = content.eval(clampSample(coord));")
    assertThat(shader).contains(
      "vec4 refractedCenterSample = content.eval(clampSample(refractCoord));",
    )
    assertThat(shader).contains(
      "sampleChroma(refractCoord, chromaOffset, refractedCenterSample)",
    )
    assertThat(shader).contains(
      "premultiply(finalStraightColor, refractedCenterSample.a)",
    )
    assertThat(shader).doesNotContain("premultiply(finalStraightColor, baseSample.a)")
  }

  @Test
  fun opticalShader_simpleAndFullChromaReuseTheRefractedCenterSampleAtZeroOffset() {
    val shader = GlassShaders.buildOptical()
    val simpleChroma = shader.substringAfter("vec3 sampleChromaSimple")
      .substringBefore("vec3 sampleChromaFull")
    val fullChroma = shader.substringAfter("vec3 sampleChromaFull")
      .substringBefore("vec3 sampleChroma(")

    assertThat(simpleChroma).contains("if (length(chromaOffset) < 0.0001)")
    assertThat(simpleChroma).contains("return unpremultiply(centerSample);")
    assertThat(fullChroma).contains("if (length(chromaOffset) < 0.0001)")
    assertThat(fullChroma).contains("return unpremultiply(centerSample);")
  }

  @Test
  fun opticalShader_retainsContinuousOpticalControls() {
    val shader = GlassShaders.buildOptical()

    assertThat(shader).contains("uniform float refractionStrength;")
    assertThat(shader).contains("uniform float refractionHeight;")
    assertThat(shader).contains("uniform float chromaticAberrationStrength;")
    assertThat(shader).contains("uniform float surfaceProfile;")
    assertThat(shader).contains("uniform float contrast;")
    assertThat(shader).contains("uniform float whitePoint;")
    assertThat(shader).contains("uniform float chromaMultiplier;")
  }

  @Test
  fun opticalShader_appliesRefractionStrengthOnlyToDisplacement() {
    val shader = GlassShaders.buildOptical()

    assertThat(shader).contains(
      "heightNorm * effectiveRefractionStrength * refractionScale;",
    )
    assertThat(shader).contains("vec2 refractCoord = clampSample(coord + displacement);")
    assertThat(shader).contains("vec3 opticalColor = refractedStraightColor;")
    assertThat(shader).doesNotContain(
      "mix(baseStraightColor, refractedStraightColor, refractionStrength)",
    )
  }

  @Test
  fun interactiveRefractionShaders_clampLocalizedEffectiveStrengthBeforeDisplacement() {
    val expectedClamp = "clamp(refractionStrength * refractionMultiplier, 0.0, 1.0)"

    listOf(
      GlassShaders.buildOptical(interactive = true),
      GlassShaders.buildRefractionDetail(interactive = true),
    ).forEach { shader ->
      assertThat(shader).contains(expectedClamp)
      assertThat(shader).contains("heightNorm * effectiveRefractionStrength * refractionScale;")
    }
  }

  @Test
  fun sharedRefractionDisplacement_samplesInwardForOutwardVisualWarp() {
    val optical = GlassShaders.buildOptical()
    val detail = GlassShaders.buildRefractionDetail()
    val inwardSampleDirection =
      "return -refractionDir * displacementMagnitude * centerFade;"

    assertThat(optical).contains(inwardSampleDirection)
    assertThat(detail).contains(inwardSampleDirection)
    assertThat(optical).doesNotContain("return refractionDir * displacementMagnitude;")
    assertThat(detail).doesNotContain("return refractionDir * displacementMagnitude;")
  }

  @Test
  fun refractionShaders_blendContinuousRoundedCornerNormals() {
    listOf(
      GlassShaders.buildOptical(),
      GlassShaders.buildOptical(interactive = true),
      GlassShaders.buildRefractionDetail(),
      GlassShaders.buildRefractionDetail(interactive = true),
    ).forEach { shader ->
      assertThat(shader).contains("vec4 edgeDistance = vec4(")
      assertThat(shader).contains("vec4 reversedSmootherstep(vec4 t)")
      assertThat(shader).contains(
        "return vec4(1.0) - t * t * t * (t * (t * 6.0 - 15.0) + 10.0);",
      )
      assertThat(shader).contains("vec4 weights = reversedSmootherstep(")
      assertThat(shader).doesNotContain("exp((edgeDistance")
      assertThat(shader).contains(
        "vec2 gradSdRectangle(vec2 localCoord, vec2 size, float blendWidth)",
      )
      assertThat(shader).contains("float normalBlendWidth = max(refractionHeight, 1.0);")
      assertThat(shader).contains(
        "gradSdRoundedRect(localCoord, materialSize, cornerRadii, normalBlendWidth)",
      )
      assertThat(shader).contains("vec2 gradSdRoundedRect(")
      assertThat(shader).contains(
        "vec2 rectangleGradient = gradSdRectangle(localCoord, size, blendWidth);",
      )
      assertThat(shader).contains("vec2 topLeftDelta = min(")
      assertThat(shader).contains("vec2 topRightDelta = vec2(")
      assertThat(shader).contains("vec2 bottomRightDelta = max(")
      assertThat(shader).contains("vec2 bottomLeftDelta = vec2(")
      assertThat(shader).contains("vec4 cornerWeights = vec4(")
      assertThat(shader).doesNotContain("float xSide = step(")
      assertThat(shader).contains(
        "return mix(rectangleGradient, cornerGradient, cornerWeight);",
      )
      assertThat(shader).doesNotContain("vec2 blendSdfGradients(")
      assertThat(shader).doesNotContain("clamp(min(size.x, size.y) * 0.01, 1.0, 4.0)")
      assertThat(shader).doesNotContain("float cornerSd")
      assertThat(shader).contains("float centerFade = smootherstep(")
      assertThat(shader).doesNotContain("edgeDistance.x > edgeDistance.y")
      assertThat(shader).doesNotContain("vec2 centerFallbackDir")
    }
  }

  @Test
  fun refractionShaders_taperOnlyTheSquircleTerminalQuarter() {
    listOf(
      GlassShaders.buildOptical(),
      GlassShaders.buildRefractionDetail(),
    ).forEach { shader ->
      assertThat(shader).contains("float squircleMap(float t)")
      assertThat(shader).contains("float terminalT = clamp((t - 0.75) / 0.25, 0.0, 1.0);")
      assertThat(shader).contains("float terminalTaper = 1.0 - smootherstep(terminalT);")
      assertThat(shader).contains("return profile * terminalTaper;")
      assertThat(shader).contains("return squircleMap(t);")
    }
  }

  @Test
  fun interactionShader_usesLocalizedDynamicUniforms() {
    val optical = GlassShaders.buildOptical(interactive = true)
    val detail = GlassShaders.buildRefractionDetail(interactive = true)
    val lighting = GlassShaders.buildInteractionLighting()

    listOf(optical, detail, lighting).forEach { shader ->
      assertThat(shader).contains("uniform float2 interactionPosition;")
      assertThat(shader).contains("uniform float interactionRadius;")
      assertThat(shader).contains("interactionFalloff")
    }
    assertThat(optical).contains("uniform float interactionRefractionMultiplier;")
    assertThat(optical).contains("uniform float interactionWhitePointDelta;")
    assertThat(optical).contains("localizedRefractionMultiplier")
    assertThat(optical).contains("localizedWhitePoint")
    assertThat(optical).contains("heightNorm,\n        localizedRefractionMultiplier")
    assertThat(detail).contains("uniform float interactionRefractionMultiplier;")
    assertThat(detail).contains("heightNorm,\n        localizedRefractionMultiplier")
    assertThat(detail).contains(
      "abs(refractionScale * refractionStrength) * max(1.0, localizedRefractionMultiplier),",
    )
    assertThat(lighting).contains("uniform float interactionLightingIntensity;")
  }

  @Test
  fun defaultOpticalAndDetailShaders_remainInteractionFree() {
    assertThat(GlassShaders.buildOptical()).doesNotContain("interactionFalloff")
    assertThat(GlassShaders.buildRefractionDetail()).doesNotContain("interactionFalloff")
  }

  @Test
  fun refractionDetailShader_isSharpPremultipliedShapeMaskedEdgeDetail() {
    val shader = GlassShaders.buildRefractionDetail()

    assertThat(shader).contains("uniform shader content;")
    assertThat(shader).contains("uniform float detailWidth;")
    assertThat(shader).contains("uniform float detailIntensity;")
    assertThat(shader).contains("uniform float detailVisibility;")
    assertThat(shader).doesNotContain("uniform float sampleStep;")
    assertThat(shader).contains("clamp(coord, vec2(0.5), sampleSize - vec2(0.5))")
    assertThat(shader).contains(
      "heightNorm * effectiveRefractionStrength * refractionScale;",
    )
    assertThat(shader).contains("vec2 refractCoord = clampSample(coord + displacement);")
    assertThat(shader).contains("vec4 sharpSample = content.eval(refractCoord);")
    assertThat(shader).contains("float heightNorm = surfaceHeightNorm(localCoord);")
    assertThat(shader).contains("float refractionMultiplier")
    assertThat(shader).contains("heightNorm,\n        1.0")
    assertThat(shader).contains("if (surfaceProfile == 1)")
    assertThat(shader).contains("else if (surfaceProfile == 2)")
    assertThat(shader).contains("else if (surfaceProfile == 3)")
    assertThat(shader).contains("float detailRamp = max(detailWidth * 0.25, 0.0001);")
    assertThat(shader).contains("float innerEnvelope = smootherstep(")
    assertThat(shader).contains("float outerEnvelope = 1.0 - smootherstep(")
    assertThat(shader).contains(
      "clamp(sourceDistToEdge / max(detailWidth, 0.0001), 0.0, 1.0)",
    )
    assertThat(shader).doesNotContain("abs(heightNorm)")
    assertThat(shader).doesNotContain("float envelopeWidth")
    assertThat(shader).contains(
      "float detailAlpha = sourceShapeMask * innerEnvelope * outerEnvelope * detailIntensity * detailVisibility;",
    )
    assertThat(shader).doesNotContain("detailIntensity * refractionStrength")
    assertThat(shader).doesNotContain("mix(baseStraightColor, refractedStraightColor, refractionStrength)")
    val transparentRejection = "if (detailAlpha <= 0.0) return vec4(0.0);"
    assertThat(shader).contains(transparentRejection)
    assertThat(shader.indexOf(transparentRejection))
      .isLessThan(shader.indexOf("vec4 sharpSample = content.eval(refractCoord);"))
    assertThat(shader).contains("vec4 detailColor = sharpSample * detailAlpha;")
    assertThat(shader).contains("if (outputSd > 0.0) return vec4(0.0);")
    assertThat(shader).contains("return detailColor.a > 0.0 ? detailColor : vec4(0.0);")
    assertThat(Regex("content\\.eval").findAll(shader).count()).isEqualTo(1)
    assertThat(shader).doesNotContain("chromaticAberration")
    assertThat(shader).doesNotContain("tintColor")
    assertThat(shader).doesNotContain("blur")
  }

  @Test
  fun refractionDetailShader_conservativelyRejectsBeforeDisplacementAndSampling() {
    val shader = GlassShaders.buildRefractionDetail()
    val conservativeRejection =
      "if (outputDistToEdge > detailWidth + maxPossibleDisplacement) return vec4(0.0);"

    assertThat(shader).contains("float sampleDiagonal = length(sampleSize);")
    assertThat(shader).contains("abs(refractionScale * refractionStrength)")
    assertThat(shader).contains("float maxPossibleDisplacement = min(")
    assertThat(shader).contains(conservativeRejection)
    assertThat(shader.indexOf(conservativeRejection))
      .isLessThan(shader.indexOf("float heightNorm = surfaceHeightNorm(localCoord);"))
    assertThat(shader.indexOf(conservativeRejection))
      .isLessThan(shader.indexOf("vec4 sharpSample = content.eval(refractCoord);"))
  }

  @Test
  fun refractionDetailShader_usesRefractedSourceCoordinatesForPreciseEnvelope() {
    val shader = GlassShaders.buildRefractionDetail()
    val preciseRejection = "if (detailAlpha <= 0.0) return vec4(0.0);"

    assertThat(shader).contains("vec2 refractedLocalCoord = localCoord + displacement;")
    assertThat(shader).contains(
      "float refractedSd = sdRoundedRect(refractedLocalCoord, materialSize, cornerRadii);",
    )
    assertThat(shader).contains("float sourceDistToEdge = max(-refractedSd, 0.0);")
    assertThat(shader.indexOf("float sourceDistToEdge = max(-refractedSd, 0.0);"))
      .isLessThan(shader.indexOf(preciseRejection))
    assertThat(shader.indexOf(preciseRejection))
      .isLessThan(shader.indexOf("vec4 sharpSample = content.eval(refractCoord);"))
  }

  @Test
  fun refractionDetailUniforms_bindOnlyTheShaderContract() {
    val uniforms = RecordingUniformProvider()
    uniforms.setRefractionDetailUniforms(
      key = GlassRefractionDetailEffectKey(
        sampleSize = Size(640f, 480f),
        materialOrigin = Offset(8f, 4f),
        materialSize = Size(320f, 240f),
        refractionStrength = 0.5f,
        refractionHeightPx = 20f,
        refractionScalePx = 18f,
        surfaceProfile = 2f,
        edgeSoftnessPx = 4f,
        cornerRadii = CornerRadii(1f, 2f, 3f, 4f),
        detailWidthPx = 8f,
        detailIntensity = 0.08f,
        detailVisibility = 0.5f,
      ),
    )

    assertThat(uniforms.values).isEqualTo(
      mapOf(
        "sampleSize" to listOf(640f, 480f),
        "materialOrigin" to listOf(8f, 4f),
        "materialSize" to listOf(320f, 240f),
        "edgeSoftness" to listOf(4f),
        "cornerRadii" to listOf(1f, 2f, 3f, 4f),
        "refractionStrength" to listOf(0.5f),
        "refractionHeight" to listOf(20f),
        "refractionScale" to listOf(18f),
        "surfaceProfile" to listOf(2f),
        "detailWidth" to listOf(8f),
        "detailIntensity" to listOf(0.08f),
        "detailVisibility" to listOf(0.5f),
      ),
    )
  }

  @Test
  fun rimShader_isSourceIndependent() {
    val shader = GlassShaders.buildRim()

    assertThat(shader).contains("uniform shader content;")
    assertThat(shader).doesNotContain("content.eval")
    assertThat(shader).contains("uniform float2 materialOrigin;")
    assertThat(shader).contains("specularIntensity")
  }

  @Test
  fun rimShader_usesMaterialCoordinatesAndConfiguredSampleStep() {
    val shader = GlassShaders.buildRim()

    assertThat(shader).contains("uniform float sampleStep;")
    assertThat(shader).contains("vec2 materialCoord(vec2 coord) { return coord - materialOrigin; }")
    assertThat(shader).contains("vec2 localCoord = materialCoord(coord);")
    assertThat(shader).contains("localCoord - vec2(sampleStep, 0.0)")
    assertThat(shader).contains("localCoord + vec2(0.0, sampleStep)")
    assertThat(shader).doesNotContain("float sampleStep = 2.0;")
  }

  @Test
  fun shaders_shareComposeNormalizedRoundedRectSdfContract() {
    val shaders = listOf(
      GlassShaders.buildOptical(),
      GlassShaders.buildOptical(interactive = true),
      GlassShaders.buildRefractionDetail(),
      GlassShaders.buildRefractionDetail(interactive = true),
      GlassShaders.buildRim(),
      GlassShaders.buildInteractionLighting(),
    )
    val sdfSignature = "float sdRoundedRect(vec2 localCoord, vec2 size, vec4 radii)"

    shaders.forEach { shader ->
      assertThat(shader).contains(sdfSignature)
      assertThat(shader).contains("localCoord.x < radii.x && localCoord.y < radii.x")
      assertThat(shader).contains("localCoord.x > size.x - radii.y && localCoord.y < radii.y")
      assertThat(shader).contains("localCoord.x > size.x - radii.z && localCoord.y > size.y - radii.z")
      assertThat(shader).contains("localCoord.x < radii.w && localCoord.y > size.y - radii.w")
      assertThat(shader).doesNotContain("float radiusAt(")
      assertThat(shader).doesNotContain(
        "float sdRoundedRect(vec2 coord, vec2 halfSize, float radius)",
      )
    }
  }

  @Test
  fun opticalAndDetailShaders_useRoundedSurfaceAndContinuousRoundedNormals() {
    listOf(
      GlassShaders.buildOptical(),
      GlassShaders.buildOptical(interactive = true),
      GlassShaders.buildRefractionDetail(),
      GlassShaders.buildRefractionDetail(interactive = true),
    ).forEach { shader ->
      assertThat(shader).contains(
        "float surfaceHeightAt(vec2 localCoord, vec4 customRadii)",
      )
      assertThat(shader).contains(
        "float sd = sdRoundedRect(localCoord, materialSize, customRadii);",
      )
      assertThat(shader).contains(
        "gradSdRoundedRect(localCoord, materialSize, cornerRadii, normalBlendWidth)",
      )
      assertThat(shader).contains("vec2 gradSdRoundedRect(")
      assertThat(shader).doesNotContain("min(smoothRadius, min(halfSize.x, halfSize.y))")
    }
  }

  private class RecordingUniformProvider : RuntimeShaderUniformProvider {
    val values = mutableMapOf<String, List<Float>>()

    override fun setFloatUniform(name: String, value: Float) {
      values[name] = listOf(value)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float) {
      values[name] = listOf(value1, value2)
    }

    override fun setFloatUniform(
      name: String,
      value1: Float,
      value2: Float,
      value3: Float,
      value4: Float,
    ) {
      values[name] = listOf(value1, value2, value3, value4)
    }

    override fun setIntUniform(name: String, value: Int) = error("Unexpected int uniform: $name")

    override fun setChildShader(name: String, shader: Shader) = error("Unexpected child: $name")
  }
}
