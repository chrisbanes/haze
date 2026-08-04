// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import dev.chrisbanes.haze.HazeProgressive
import kotlin.test.Test

class GlassRenderEffectKeysTest {

  @Test
  fun blurKey_ignoresUnrelatedOpticalDepthAndRimChanges() {
    val base = params()
    val unrelated = base.copy(
      depth = 0.25f,
      tint = Color.Magenta,
      specularIntensity = 0.9f,
      refractionStrength = 0.8f,
    )

    assertThat(base.blurEffectKey()).isEqualTo(unrelated.blurEffectKey())
  }

  @Test
  fun blurKey_changesForRadiusAndSizeButIgnoresUnusedCoordinates() {
    val base = params()

    assertThat(base.blurEffectKey()).isNotEqualTo(
      base.copy(
        blurRadiusPx = 30f,
        blurSigmaPx = SemanticBlurKernel.radiusToSigma(30f),
      ).blurEffectKey(),
    )
    assertThat(base.blurEffectKey()).isNotEqualTo(
      base.copy(coordinates = base.coordinates.copy(sampleSize = Size(800f, 600f))).blurEffectKey(),
    )
    assertThat(base.blurEffectKey()).isEqualTo(
      base.copy(
        coordinates = base.coordinates.copy(materialOrigin = Offset(12f, 8f)),
      ).blurEffectKey(),
    )
  }

  @Test
  fun blurKey_isUnchangedWhenOnlyOneSquareCornerBecomesRounded() {
    val base = params()
    fun calibratedParams(radii: CornerRadii): GlassRenderParams {
      val response = calculateAdaptiveGeometryResponse(
        materialSizePx = base.coordinates.materialSize,
        density = androidx.compose.ui.unit.Density(1f),
        cornerRadiiPx = radii,
      )
      val resolved = resolveAdaptiveGeometryOptics(
        response = response,
        refractionStrength = .7f,
        shortestSidePx = base.coordinates.materialSize.minDimension,
        blurRadiusPx = base.blurRadiusPx,
        refractionScalePx = base.refractionScalePx,
        refractionHeight = .5f,
      )
      return base.copy(
        blurRadiusPx = resolved.blurRadiusPx,
        blurSigmaPx = resolved.blurSigmaPx,
        geometryToneGain = resolved.toneGain,
        geometryNeutralLift = resolved.neutralLiftWeight,
      )
    }
    val square = calibratedParams(CornerRadii.zero)
    val oneRoundedCorner = calibratedParams(CornerRadii(100f, 0f, 0f, 0f))

    assertThat(oneRoundedCorner.geometryToneGain).isEqualTo(square.geometryToneGain)
    assertThat(oneRoundedCorner.geometryNeutralLift).isEqualTo(square.geometryNeutralLift)
    assertThat(oneRoundedCorner.blurEffectKey()).isEqualTo(square.blurEffectKey())
  }

  @Test
  fun progressivePresence_selectsFullResolutionPlanAndChangesKey() {
    val uniform = params().copy(
      blurRadiusPx = 38.5f,
      blurSigmaPx = SemanticBlurKernel.radiusToSigma(38.5f),
    ).blurEffectKey()
    val progressive = params().copy(
      blurRadiusPx = 38.5f,
      blurSigmaPx = SemanticBlurKernel.radiusToSigma(38.5f),
      progressive = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f),
    ).blurEffectKey()

    assertThat(uniform.plan.scaleFactor).isEqualTo(0.5f)
    assertThat(progressive.plan.scaleFactor).isEqualTo(1f)
    assertThat(progressive.plan.requiresPrefilter).isEqualTo(false)
    assertThat(progressive).isNotEqualTo(uniform)
  }

  @Test
  fun progressiveKey_convertsWorkingCoordinatesBackToLogicalMaskCoordinates() {
    val base = params()
    val key = base.copy(
      coordinates = GlassCoordinates(
        sampleSize = Size(480f, 360f),
        materialOrigin = Offset(6f, 3f),
        materialSize = Size(240f, 180f),
        scaleFactor = 0.75f,
      ),
      progressive = HazeProgressive.verticalGradient(
        startY = 40f,
        endY = 80f,
      ),
    ).blurEffectKey()

    assertThat(key.maskOrigin).isEqualTo(Offset(6f, 3f))
    assertThat(key.maskSize).isEqualTo(Size(320f, 240f))
    assertThat(key.maskCoordinateScale).isEqualTo(1f / 0.75f)
  }

  @Test
  fun opticalAndRimKeys_onlyTrackConsumedUniforms() {
    val base = params()

    assertThat(base.opticalEffectKey()).isEqualTo(
      base.copy(depth = 0.2f, blurRadiusPx = 80f, specularIntensity = 1f).opticalEffectKey(),
    )
    assertThat(base.rimEffectKey()).isEqualTo(
      base.copy(depth = 0.2f, blurRadiusPx = 80f, tint = Color.Red).rimEffectKey(),
    )
    assertThat(base.opticalEffectKey()).isNotEqualTo(base.copy(tint = Color.Red).opticalEffectKey())
    assertThat(base.rimEffectKey()).isNotEqualTo(base.copy(specularIntensity = 1f).rimEffectKey())
  }

  @Test
  fun interactionValues_doNotChangeBaseStageKeys() {
    val params = params()
    val idle = params.interactionUniforms(
      GlassInteractionRenderState(position = Offset(20f, 20f)),
      radiusFraction = 0.7f,
    )
    val pressed = params.interactionUniforms(
      GlassInteractionRenderState(
        position = Offset(80f, 60f),
        lightingIntensity = 1f,
        refractionMultiplier = 1.08f,
        whitePointDelta = 0.04f,
        scaleX = 0.98f,
        scaleY = 0.98f,
      ),
      radiusFraction = 0.7f,
    )

    assertThat(params.blurEffectKey()).isEqualTo(params.blurEffectKey())
    assertThat(params.opticalEffectKey()).isEqualTo(params.opticalEffectKey())
    assertThat(params.refractionDetailEffectKey())
      .isEqualTo(params.refractionDetailEffectKey())
    assertThat(params.rimEffectKey()).isEqualTo(params.rimEffectKey())
    assertThat(idle).isNotEqualTo(pressed)
  }

  @Test
  fun refractionDetailKey_onlyTracksConsumedUniforms() {
    val base = params()
    val key = base.refractionDetailEffectKey()

    assertThat(key).isEqualTo(
      base.copy(
        blurRadiusPx = 80f,
        blurSigmaPx = 20f,
        progressive = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f),
        depth = 0.2f,
        tint = Color.Red,
        ambientResponse = 0.8f,
        chromaticAberrationStrength = 0.9f,
        chromaticAberrationMode = 1f,
        contrast = 0.4f,
        whitePoint = 0.3f,
        chromaMultiplier = 0.5f,
        contentNormalBlend = 0.7f,
        fresnelExponent = 8f,
        geometryToneGain = 1.1f,
        geometryNeutralLift = 0.1f,
        specularIntensity = 1f,
        specularExponent = 32f,
        lightPosition = Offset(100f, 80f),
      ).refractionDetailEffectKey(),
    )

    listOf(
      base.copy(coordinates = base.coordinates.copy(materialOrigin = Offset(12f, 8f))),
      base.copy(refractionStrength = 0.8f),
      base.copy(refractionHeightPx = 30f),
      base.copy(refractionScalePx = 24f),
      base.copy(surfaceProfile = 1f),
      base.copy(edgeSoftnessPx = 6f),
      base.copy(cornerRadii = CornerRadii(8f, 4f, 2f, 1f)),
    ).forEach { changed ->
      assertThat(key).isNotEqualTo(changed.refractionDetailEffectKey())
    }
    assertThat(key).isNotEqualTo(key.copy(detailIntensity = key.detailIntensity * 0.5f))
    assertThat(key).isNotEqualTo(key.copy(detailVisibility = key.detailVisibility * 0.5f))
  }

  @Test
  fun refractionDetailKey_ignoresCoordinateScaleFactor() {
    val base = params()

    assertThat(base.refractionDetailEffectKey()).isEqualTo(
      base.copy(
        coordinates = base.coordinates.copy(scaleFactor = 0.5f),
      ).refractionDetailEffectKey(),
    )
  }

  @Test
  fun refractionDetailKey_hasProfileIndependentDetailWidth() {
    val base = params().copy(
      refractionHeightPx = 20f,
      edgeSoftnessPx = 3f,
      sampleStepPx = 2f,
    )
    val keys = SurfaceProfile.entries.map { profile ->
      base.copy(surfaceProfile = profile.ordinal.toFloat()).refractionDetailEffectKey()
    }

    assertThat(keys.map { it.detailWidthPx }.toSet()).isEqualTo(setOf(20f))
    assertThat(keys.map { it.surfaceProfile }.toSet().size).isEqualTo(SurfaceProfile.entries.size)
  }

  @Test
  fun refractionDetailKey_tracksSampleStepThroughDetailWidthAndVisibility() {
    val base = params().copy(refractionHeightPx = 100f, edgeSoftnessPx = 20f)

    assertThat(base.refractionDetailEffectKey())
      .isEqualTo(base.copy(sampleStepPx = 1f).refractionDetailEffectKey())
    assertThat(base.refractionDetailEffectKey())
      .isNotEqualTo(base.copy(sampleStepPx = 11f).refractionDetailEffectKey())

    val weak = base.copy(refractionStrength = .05f)
    val onePixelStep = weak.copy(sampleStepPx = 1f).refractionDetailEffectKey()
    val twoPixelStep = weak.copy(sampleStepPx = 2f).refractionDetailEffectKey()
    assertThat(onePixelStep.detailWidthPx).isEqualTo(twoPixelStep.detailWidthPx)
    assertThat(onePixelStep.detailVisibility).isNotEqualTo(twoPixelStep.detailVisibility)
    assertThat(onePixelStep).isNotEqualTo(twoPixelStep)
  }

  @Test
  fun activeRefractionDetailKey_requiresStrengthScaleWidthAndIntensity() {
    val base = params()

    assertThat(base.activeRefractionDetailEffectKey()).isNotNull()
    assertThat(base.copy(refractionStrength = 0f).activeRefractionDetailEffectKey()).isNull()
    assertThat(base.copy(refractionScalePx = 0f).activeRefractionDetailEffectKey()).isNull()
    assertThat(base.copy(refractionHeightPx = 0f).activeRefractionDetailEffectKey()).isNull()
    assertThat(base.activeRefractionDetailEffectKey(detailIntensity = 0f)).isNull()
  }

  @Test
  fun refractionDetailVisibility_isZeroMonotonicAndSaturatesAtOneSampleStep() {
    val zero = calculateRefractionDetailVisibility(0f, refractionScalePx = 20f, sampleStepPx = 2f)
    val quarter = calculateRefractionDetailVisibility(.025f, refractionScalePx = 20f, sampleStepPx = 2f)
    val half = calculateRefractionDetailVisibility(.05f, refractionScalePx = 20f, sampleStepPx = 2f)
    val full = calculateRefractionDetailVisibility(.1f, refractionScalePx = 20f, sampleStepPx = 2f)
    val beyond = calculateRefractionDetailVisibility(1f, refractionScalePx = 20f, sampleStepPx = 2f)

    assertThat(zero).isEqualTo(0f)
    assertThat(quarter).isGreaterThan(zero)
    assertThat(quarter).isLessThan(half)
    assertThat(half).isLessThan(full)
    assertThat(full).isEqualTo(1f)
    assertThat(beyond).isEqualTo(1f)
    assertThat(calculateRefractionDetailVisibility(-.05f, 20f, 2f)).isEqualTo(half)
    assertThat(calculateRefractionDetailVisibility(.05f, 20f, 0f))
      .isEqualTo(calculateRefractionDetailVisibility(.05f, 20f, 1f))
  }

  @Test
  fun activeRefractionDetailKey_skipsImperceptibleContributionContinuously() {
    val base = params()
    val below = base.copy(refractionStrength = .008f).refractionDetailEffectKey()
    val above = base.copy(refractionStrength = .009f).refractionDetailEffectKey()
    val halfVisible = base.copy(refractionStrength = .05f)
    val exactThresholdIntensity = 2f / 255f

    assertThat(base.copy(refractionStrength = 0f).activeRefractionDetailEffectKey()).isNull()
    assertThat(base.copy(refractionStrength = 1e-6f).activeRefractionDetailEffectKey()).isNull()
    assertThat(base.copy(refractionStrength = .008f).activeRefractionDetailEffectKey()).isNull()
    assertThat(base.copy(refractionStrength = .009f).activeRefractionDetailEffectKey()).isNotNull()
    assertThat(halfVisible.activeRefractionDetailEffectKey(exactThresholdIntensity)).isNull()
    assertThat(halfVisible.activeRefractionDetailEffectKey(exactThresholdIntensity + 1e-5f)).isNotNull()
    assertThat(above.detailVisibility).isGreaterThan(below.detailVisibility)
    assertThat(
      above.detailIntensity * above.detailVisibility - below.detailIntensity * below.detailVisibility,
    ).isLessThan(1f / 255f)
  }

  @Test
  fun defaultRefractionDetailVisibility_isFullySaturated() {
    val regularBaseline = GlassOptics.Fixed()
    assertThat(
      calculateRefractionDetailVisibility(
        refractionStrength = regularBaseline.refractionStrength,
        refractionScalePx = regularBaseline.refractionDisplacement.value,
        sampleStepPx = 2f,
      ),
    ).isEqualTo(1f)
  }

  private fun params() = GlassRenderParams(
    coordinates = GlassCoordinates(Size(640f, 480f), Offset(8f, 4f), Size(320f, 240f), 1f),
    refractionStrength = 0.5f,
    specularIntensity = 0.5f,
    depth = 1f,
    ambientResponse = 0.5f,
    tint = Color.Transparent,
    edgeSoftnessPx = 4f,
    blurRadiusPx = 20f,
    blurSigmaPx = SemanticBlurKernel.radiusToSigma(20f),
    progressive = null,
    refractionHeightPx = 20f,
    chromaticAberrationStrength = 0.2f,
    surfaceProfile = 0f,
    chromaticAberrationMode = 0f,
    contrast = 0f,
    whitePoint = 0f,
    chromaMultiplier = 1f,
    refractionScalePx = 20f,
    contentNormalBlend = 0f,
    specularExponent = 16f,
    fresnelExponent = 2f,
    geometryToneGain = 1f,
    geometryNeutralLift = 0f,
    cornerRadii = CornerRadii.zero,
    lightPosition = Offset.Zero,
    sampleStepPx = 2f,
  )
}
