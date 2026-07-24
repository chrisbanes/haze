// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test

class GlassRenderParamsTest {

  @Test
  fun interactionTopology_usesConfiguredWorstCaseInsteadOfAnimatedValues() {
    val effect = GlassVisualEffect().apply {
      hovered { lightingIntensity(0.4f) }
      pressed {
        refractionMultiplier(1.2f)
        whitePointDelta(0.1f)
      }
    }

    assertThat(effect.interactionSlots.resolveInteractionTopology()).isEqualTo(
      GlassInteractionTopology(
        hasOptics = true,
        hasLighting = true,
        maxRefractionMultiplier = 1.2f,
      ),
    )
  }

  @Test
  fun interactionTopology_ignoresIdentityOnlyDeclarations() {
    val effect = GlassVisualEffect().apply {
      hovered {
        lightingIntensity(0f)
        refractionMultiplier(1f)
        whitePointDelta(0f)
      }
    }

    assertThat(effect.interactionSlots.resolveInteractionTopology()).isEqualTo(
      GlassInteractionTopology(false, false, 1f),
    )
  }

  @Test
  fun interactionPatch_tracksPositionAndIncludesOpticalPadding() {
    val params = testRenderParams(
      coordinates = GlassCoordinates(
        sampleSize = Size(1000f, 600f),
        materialOrigin = Offset.Zero,
        materialSize = Size(1000f, 600f),
        scaleFactor = 1f,
      ),
      refractionStrength = 0.5f,
      refractionScalePx = 20f,
    )
    val topology = GlassInteractionTopology(true, true, 1.2f)
    val patch = checkNotNull(
      resolveGlassInteractionPatch(
        params = params,
        uniforms = GlassInteractionUniforms(Offset(500f, 300f), 60f, 1f, 1.1f, 0.04f),
        topology = topology,
      ),
    )

    assertThat(patch.bounds.width).isLessThan(1000)
    assertThat(patch.bounds.height).isLessThan(600)
    assertThat(patch.bounds.left).isLessThan(500 - 60)
    assertThat(patch.bounds.right).isGreaterThan(500 + 60)
    assertThat(patch.coordinates.sampleSize).isEqualTo(
      Size(patch.bounds.width.toFloat(), patch.bounds.height.toFloat()),
    )
  }

  @Test
  fun interactionPatch_clipsAtSampleEdge() {
    val params = testRenderParams(
      coordinates = GlassCoordinates(Size(200f, 100f), Offset.Zero, Size(200f, 100f), 1f),
      refractionStrength = 0.5f,
      refractionScalePx = 20f,
    )
    val patch = checkNotNull(
      resolveGlassInteractionPatch(
        params,
        GlassInteractionUniforms(Offset(0f, 0f), 30f, 1f, 1.2f, 0.04f),
        GlassInteractionTopology(true, true, 1.2f),
      ),
    )

    assertThat(patch.bounds.left).isEqualTo(0)
    assertThat(patch.bounds.top).isEqualTo(0)
    assertThat(patch.uniforms.position).isEqualTo(Offset.Zero)
  }

  @Test
  fun interactionPatchSize_isSmallerThanLargeSample() {
    val params = testRenderParams(
      coordinates = GlassCoordinates(Size(2000f, 1200f), Offset.Zero, Size(2000f, 1200f), 1f),
      refractionStrength = 0.5f,
      refractionScalePx = 20f,
    )

    val size = calculateGlassInteractionPatchSize(
      params,
      radiusFraction = 0.1f,
      topology = GlassInteractionTopology(true, true, 1.2f),
    )

    assertThat(size.width).isLessThan(2000)
    assertThat(size.height).isLessThan(1200)
  }

  @Test
  fun interactionPatchSize_coversFractionallyPositionedRuntimePatch() {
    val params = testRenderParams(
      coordinates = GlassCoordinates(Size(1000f, 600f), Offset.Zero, Size(1000f, 600f), 1f),
      refractionStrength = 0.5f,
      refractionScalePx = 20f,
    )
    val topology = GlassInteractionTopology(true, true, 1.2f)
    val reserved = calculateGlassInteractionPatchSize(params, radiusFraction = 0.1f, topology)
    val runtime = checkNotNull(
      resolveGlassInteractionPatch(
        params,
        GlassInteractionUniforms(Offset(500.25f, 300.75f), 60f, 1f, 1.2f, 0.04f),
        topology,
      ),
    )

    assertThat(reserved.width >= runtime.bounds.width).isEqualTo(true)
    assertThat(reserved.height >= runtime.bounds.height).isEqualTo(true)
  }

  @Test
  fun interactionPatch_clampsOvershootingRefractionToConfiguredTopology() {
    val topology = GlassInteractionTopology(true, false, 1.1f)
    val patch = checkNotNull(
      resolveGlassInteractionPatch(
        testRenderParams(),
        GlassInteractionUniforms(Offset(50f, 40f), 20f, 1f, 1.5f, 0f),
        topology,
      ),
    )

    assertThat(patch.uniforms.refractionMultiplier).isEqualTo(1.1f)
  }

  @Test
  fun interactionPatch_localizesLightingUniformsForNonOriginPatch() {
    val position = Offset(500f, 300f)
    val patch = checkNotNull(
      resolveGlassInteractionPatch(
        testRenderParams(
          coordinates = GlassCoordinates(Size(1000f, 600f), Offset.Zero, Size(1000f, 600f), 1f),
        ),
        GlassInteractionUniforms(position, 60f, 1f, 1f, 0f),
        GlassInteractionTopology(false, true, 1f),
      ),
    )

    assertThat(patch.uniforms.position).isEqualTo(
      position - Offset(patch.bounds.left.toFloat(), patch.bounds.top.toFloat()),
    )
  }

  @Test
  fun resolvedStyle_canonicalizesAllScalarsEquallyAcrossPrecedenceLevels() {
    val lighting = GlassLighting(
      specularIntensity = 2f,
      ambientResponse = -1f,
      specularExponent = -1f,
      fresnelExponent = -1f,
    )
    val color = GlassColor(
      alpha = 2f,
      contrast = 2f,
      whitePoint = -2f,
      chromaMultiplier = 3f,
    )
    val rendering = GlassRendering(
      edgeSoftness = Float.POSITIVE_INFINITY.dp,
      contentNormalBlend = 2f,
      chromaticAberrationStrength = -1f,
    )
    val inheritedStyle = GlassStyle(
      lighting = lighting,
      color = color,
      rendering = rendering,
    )
    val effects = listOf(
      GlassVisualEffect().apply {
        specularIntensity = lighting.specularIntensity
        ambientResponse = lighting.ambientResponse
        specularExponent = lighting.specularExponent
        fresnelExponent = lighting.fresnelExponent
        alpha = color.alpha
        contrast = color.contrast
        whitePoint = color.whitePoint
        chromaMultiplier = color.chromaMultiplier
        edgeSoftness = rendering.edgeSoftness
        contentNormalBlend = rendering.contentNormalBlend
        chromaticAberrationStrength = rendering.chromaticAberrationStrength
      },
      GlassVisualEffect().apply { style = inheritedStyle },
      GlassVisualEffect().apply { compositionLocalStyle = inheritedStyle },
    )
    val size = Size(100f, 80f)
    val density = Density(1f)
    val resolved = effects.map {
      resolveGlassStyle(it, size, density, LayoutDirection.Ltr)
    }

    assertThat(resolved[1]).isEqualTo(resolved[0])
    assertThat(resolved[2]).isEqualTo(resolved[0])
    assertThat(resolved[0].chromaticAberrationStrength).isEqualTo(0f)
    assertThat(resolved[0].edgeSoftnessPx).isEqualTo(GlassDefaults.edgeSoftness.value)

    val rect = Rect(0f, 0f, size.width, size.height)
    val bounds = effects.map { it.calculateLayerBounds(rect, density) }
    assertThat(bounds[1]).isEqualTo(bounds[0])
    assertThat(bounds[2]).isEqualTo(bounds[0])
  }

  @Test
  fun resolvedStyle_nonFiniteOrNegativeCornerRadiiUseSafeDefaultRadii() {
    val defaultRadii = GlassDefaults.shape.toCornerRadiiPx(
      layerSize = Size(100f, 80f),
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
    )

    listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f).forEach { invalidRadius ->
      val effect = GlassVisualEffect().apply {
        shape = RoundedCornerShape(
          object : CornerSize {
            override fun toPx(shapeSize: Size, density: Density): Float = invalidRadius
          },
        )
      }

      val resolved = resolveGlassStyle(
        effect = effect,
        materialSizePx = Size(100f, 80f),
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
      )

      assertThat(resolved.cornerRadii).isEqualTo(defaultRadii)
    }
  }

  @Test
  fun resolvedStyle_propagatesCornerSizeConversionFailures() {
    val effect = GlassVisualEffect().apply {
      shape = RoundedCornerShape(
        object : CornerSize {
          override fun toPx(shapeSize: Size, density: Density): Float {
            throw IllegalArgumentException("custom corner conversion failed")
          }
        },
      )
    }

    assertFailure {
      resolveGlassStyle(
        effect = effect,
        materialSizePx = Size(100f, 80f),
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
      )
    }.isInstanceOf<IllegalArgumentException>()
      .hasMessage("custom corner conversion failed")
  }

  @Test
  fun preparedRender_carriesCanonicalAlphaForRuntimeDrawing() {
    val effect = GlassVisualEffect().apply {
      style = GlassStyle(color = GlassColor(alpha = Float.POSITIVE_INFINITY))
    }
    val style = resolveGlassStyle(
      effect = effect,
      materialSizePx = Size(100f, 80f),
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
    )
    val coordinates = GlassCoordinates(
      sampleSize = Size(100f, 80f),
      materialOrigin = Offset.Zero,
      materialSize = Size(100f, 80f),
      scaleFactor = 1f,
    )

    val prepared = buildGlassPreparedRender(
      params = buildGlassRenderParams(style, coordinates),
      interactionUniforms = resolveGlassInteraction(
        state = GlassInteractionRenderState(Offset.Zero),
        radiusFraction = 0f,
      ).uniforms(coordinates),
      alpha = style.alpha,
      outputSize = coordinates.materialSize.roundToIntSize(),
    )

    assertThat(prepared.alpha).isEqualTo(GlassDefaults.alpha)
  }

  @Test
  fun retainedPlan_depthZeroOmitsBlurAndDepthLayers() {
    val plan = buildGlassRetainedLayerPlan(
      params = testRenderParams(depth = 0f, blurRadiusPx = 0f),
      interaction = GlassInteractionUniforms(Offset.Zero, 0f, 0f, 1f, 0f),
    )

    assertThat(plan.layers.map { it.kind }).isEqualTo(
      listOf(GlassRetainedLayerKind.Source, GlassRetainedLayerKind.Optical, GlassRetainedLayerKind.Rim),
    )
  }

  @Test
  fun retainedPlan_partialDepthCountsBlurAndDepthLayers() {
    val params = testRenderParams(depth = 0.5f, blurRadiusPx = 12f)
    val plan = buildGlassRetainedLayerPlan(
      params = params,
      interaction = GlassInteractionUniforms(Offset.Zero, 0f, 0f, 1f, 0f),
    )

    assertThat(plan.layers.map { it.kind }).isEqualTo(
      listOf(
        GlassRetainedLayerKind.Source,
        GlassRetainedLayerKind.BlurHorizontal,
        GlassRetainedLayerKind.Blurred,
        GlassRetainedLayerKind.DepthMixed,
        GlassRetainedLayerKind.Optical,
        GlassRetainedLayerKind.Rim,
      ),
    )
  }

  @Test
  fun retainedPlan_interactionCountsCompositeGraph() {
    val plan = buildGlassRetainedLayerPlan(
      params = testRenderParams(
        blurRadiusPx = 0f,
        refractionStrength = 0.5f,
        refractionScalePx = 20f,
      ),
      interaction = GlassInteractionUniforms(Offset.Zero, 20f, 1f, 1.1f, 0f),
    )

    assertThat(plan.layers.map { it.kind }).containsExactly(
      GlassRetainedLayerKind.Source,
      GlassRetainedLayerKind.Optical,
      GlassRetainedLayerKind.RefractionDetail,
      GlassRetainedLayerKind.RefractionDetailCoverage,
      GlassRetainedLayerKind.RefractionComposite,
      GlassRetainedLayerKind.Rim,
      GlassRetainedLayerKind.InteractionOptical,
      GlassRetainedLayerKind.InteractionDetail,
      GlassRetainedLayerKind.InteractionDetailCoverage,
      GlassRetainedLayerKind.InteractionComposite,
      GlassRetainedLayerKind.InteractionLighting,
    )
  }

  @Test
  fun retainedPlan_usesBlurWorkingSizeForHorizontalAndVerticalLayers() {
    val params = testRenderParams(
      coordinates = GlassCoordinates(
        sampleSize = Size(1_000f, 500f),
        materialOrigin = Offset.Zero,
        materialSize = Size(100f, 100f),
        scaleFactor = 1f,
      ),
      depth = 1f,
      blurRadiusPx = 100f,
    )
    val plan = buildGlassRetainedLayerPlan(
      params = params,
      interaction = GlassInteractionUniforms(Offset.Zero, 0f, 0f, 1f, 0f),
    )
    val workingSize = params.blurEffectKey().plan.workingSize

    assertThat(
      plan.layers.filter {
        it.kind == GlassRetainedLayerKind.BlurHorizontal || it.kind == GlassRetainedLayerKind.Blurred
      }.map { it.size },
    ).isEqualTo(listOf(workingSize, workingSize))
  }

  @Test
  fun samplePadding_nonFiniteInputsReturnsFiniteNonNegativeValue() {
    val padding = calculateGlassSamplePaddingPx(
      blurRadiusPx = Float.NaN,
      refractionScale = Float.POSITIVE_INFINITY,
      refractionStrength = Float.NEGATIVE_INFINITY,
      chromaticAberrationStrength = Float.NaN,
      edgeSoftnessPx = Float.POSITIVE_INFINITY,
      foregroundOutsetPx = Float.NaN,
    )

    assertThat(padding.isFinite()).isEqualTo(true)
    assertThat(padding >= 0f).isEqualTo(true)
  }

  @Test
  fun interactionUniforms_invalidPositionUsesMaterialCenterInExpandedSampleCoordinates() {
    val uniforms = testRenderParams(
      coordinates = GlassCoordinates(
        sampleSize = Size(300f, 220f),
        materialOrigin = Offset(40f, 60f),
        materialSize = Size(200f, 100f),
        scaleFactor = 2f,
      ),
    ).interactionUniforms(
      state = GlassInteractionRenderState(position = Offset.Unspecified),
      radiusFraction = 0f,
    )

    assertThat(uniforms.position).isEqualTo(Offset(140f, 110f))
  }

  @Test
  fun fallbackInteractionLighting_usesRuntimeCanonicalization() {
    val uniforms = resolveFallbackGlassInteraction(
      state = GlassInteractionRenderState(
        position = Offset(Float.NaN, Float.POSITIVE_INFINITY),
        lightingIntensity = 1.5f,
        refractionMultiplier = Float.POSITIVE_INFINITY,
        whitePointDelta = Float.NaN,
      ),
      radiusFraction = Float.NaN,
      size = Size(100f, 80f),
    )

    assertThat(uniforms.position).isEqualTo(Offset(50f, 40f))
    assertThat(uniforms.radiusPx).isEqualTo(
      80f * GlassDefaults.interactionLightRadiusFraction,
    )
    assertThat(uniforms.lightingIntensity).isEqualTo(1f)
    assertThat(uniforms.refractionMultiplier).isEqualTo(1f)
    assertThat(uniforms.whitePointDelta).isEqualTo(0f)
  }

  @Test
  fun absoluteOptics_useLiteralValuesRegardlessOfGeometry() {
    val progressive = dev.chrisbanes.haze.HazeProgressive.verticalGradient(
      startIntensity = 0f,
      endIntensity = 1f,
    )
    val optics = GlassOptics.Absolute(
      refractionStrength = 0.4f,
      refractionHeight = 0.25f,
      refractionScale = 15f,
      depth = 0.6f,
      blurRadius = 10.dp,
      progressive = progressive,
    )
    val cases = listOf(
      Triple(Size(80f, 240f), Density(1f), CornerRadii(40f, 40f, 40f, 40f)),
      Triple(Size(1_200f, 600f), Density(3f), CornerRadii.zero),
    )

    cases.forEach { (size, density, radii) ->
      val resolved = resolveGlassOptics(optics, size, density, radii)

      assertThat(resolved.refractionStrength).isEqualTo(optics.refractionStrength)
      assertThat(resolved.refractionHeightPx / size.minDimension)
        .isEqualTo(optics.refractionHeight)
      assertThat(resolved.refractionScalePx).isEqualTo(optics.refractionScale)
      assertThat(resolved.depth).isEqualTo(optics.depth)
      assertThat(resolved.blurRadiusPx / density.density).isEqualTo(optics.blurRadius.value)
      assertThat(resolved.progressive).isEqualTo(progressive)
      assertThat(resolved.toneGain).isEqualTo(1f)
      assertThat(resolved.neutralLiftWeight).isEqualTo(0f)
    }
  }

  @Test
  fun absoluteOptics_blurRadiusUsesCurrentPhysicalPixelCapAcrossDensities() {
    val cases = listOf(
      Triple(20.dp, Density(1f), 20f),
      Triple(14.dp, Density(2.75f), 38.5f),
      Triple(20.dp, Density(2f), 38.5f),
      Triple(100.dp, Density(4f), 38.5f),
    )

    cases.forEach { (radius, density, expectedRadiusPx) ->
      val resolved = resolveGlassOptics(
        optics = GlassOptics.Absolute(blurRadius = radius),
        materialSizePx = Size(200f, 100f),
        density = density,
        cornerRadiiPx = CornerRadii.zero,
      )

      assertThat(resolved.blurRadiusPx).isEqualTo(expectedRadiusPx)
    }
  }

  @Test
  fun adaptiveOptics_areDensityAndRotationInvariant() {
    val first = calculateAdaptiveGeometryResponse(
      materialSizePx = Size(480f, 240f),
      density = Density(2f),
      cornerRadiiPx = CornerRadii(48f, 48f, 48f, 48f),
    )
    val second = calculateAdaptiveGeometryResponse(
      materialSizePx = Size(720f, 1440f),
      density = Density(6f),
      cornerRadiiPx = CornerRadii(144f, 144f, 144f, 144f),
    )

    assertThat(first).isEqualTo(second)
  }

  @Test
  fun adaptiveOptics_clampToSupportedDomainAndAreContinuous() {
    fun response(shortSideDp: Float) = calculateAdaptiveGeometryResponseForLogicalGeometry(
      shortestSideDp = shortSideDp,
      aspectRatio = 1.5f,
      symmetricRoundness = 0.5f,
    )

    assertThat(response(1f)).isEqualTo(response(48f))
    assertThat(response(1_000f)).isEqualTo(response(176f))
    assertResponseDeltaBelow(response(175.99f), response(176.01f), 0.001f)
  }

  @Test
  fun adaptiveOptics_clampAspectAndRoundness() {
    fun response(aspect: Float, roundness: Float) =
      calculateAdaptiveGeometryResponseForLogicalGeometry(
        shortestSideDp = 112f,
        aspectRatio = aspect,
        symmetricRoundness = roundness,
      )

    assertThat(response(0.5f, 0.5f)).isEqualTo(response(1f, 0.5f))
    assertThat(response(10f, 0.5f)).isEqualTo(response(3.5f, 0.5f))
    assertThat(response(1.5f, -1f)).isEqualTo(response(1.5f, 0f))
    assertThat(response(1.5f, 2f)).isEqualTo(response(1.5f, 1f))
  }

  @Test
  fun adaptiveOptics_useMinimumCornerRadiusIndependentOfPermutation() {
    val first = calculateAdaptiveGeometryResponse(
      materialSizePx = Size(480f, 240f),
      density = Density(2f),
      cornerRadiiPx = CornerRadii(12f, 24f, 36f, 48f),
    )
    val second = calculateAdaptiveGeometryResponse(
      materialSizePx = Size(240f, 480f),
      density = Density(2f),
      cornerRadiiPx = CornerRadii(48f, 36f, 24f, 12f),
    )

    assertThat(first).isEqualTo(second)
  }

  @Test
  fun adaptiveOptics_invalidGeometryReturnsIdentity() {
    val invalidResponses = listOf(
      calculateAdaptiveGeometryResponse(
        Size.Zero,
        Density(1f),
        CornerRadii.zero,
      ),
      calculateAdaptiveGeometryResponse(
        Size(Float.NaN, 100f),
        Density(1f),
        CornerRadii.zero,
      ),
      calculateAdaptiveGeometryResponse(
        Size(100f, 100f),
        Density(0f),
        CornerRadii.zero,
      ),
      calculateAdaptiveGeometryResponse(
        Size(100f, 100f),
        Density(1f),
        CornerRadii(Float.NaN, 0f, 0f, 0f),
      ),
      calculateAdaptiveGeometryResponse(
        Size(100f, 100f),
        Density(1f),
        CornerRadii(0f, 0f, 0f, Float.POSITIVE_INFINITY),
      ),
      calculateAdaptiveGeometryResponseForLogicalGeometry(
        shortestSideDp = Float.POSITIVE_INFINITY,
        aspectRatio = 1.5f,
        symmetricRoundness = 0.5f,
      ),
    )

    invalidResponses.forEach { response ->
      assertThat(response).isEqualTo(AdaptiveGeometryResponse.Identity)
    }
  }

  @Test
  fun adaptiveOptics_defaultStrengthOutputsAreFiniteAndBounded() {
    val responses = listOf(
      calculateAdaptiveGeometryResponseForLogicalGeometry(48f, 1f, 0f),
      calculateAdaptiveGeometryResponseForLogicalGeometry(112f, 1.5f, 0.5f),
      calculateAdaptiveGeometryResponseForLogicalGeometry(176f, 3.5f, 1f),
    ).map { it.resolve(refractionStrength = 0.7f) }

    responses.forEach { response ->
      response.values().forEach { value ->
        assertThat(value.isFinite()).isEqualTo(true)
      }
      assertThat(response.blurScale in 0.51f..1.07f).isEqualTo(true)
      assertThat(response.displacementScale in 3.1f..3.38f).isEqualTo(true)
      assertThat(response.reachScale in 2.295f..2.505f).isEqualTo(true)
      assertThat(response.toneGain).isEqualTo(1f)
      assertThat(response.neutralLiftWeight).isEqualTo(0f)
    }
  }

  @Test
  fun adaptiveOptics_useIndependentGeometryFeatures() {
    val small = calculateAdaptiveGeometryResponseForLogicalGeometry(48f, 1f, 0f)
    val large = calculateAdaptiveGeometryResponseForLogicalGeometry(176f, 1f, 0f)
    val elongated = calculateAdaptiveGeometryResponseForLogicalGeometry(48f, 3.5f, 0f)
    val rounded = calculateAdaptiveGeometryResponseForLogicalGeometry(48f, 1f, 1f)

    assertThat(small).isEqualTo(AdaptiveGeometryResponse(0.3f, 4f, 3f * 0.95f, 1f, 0f))
    assertThat(large).isEqualTo(AdaptiveGeometryResponse(1.1f, 4f, 3f * 0.95f, 1f, 0f))
    assertThat(elongated).isEqualTo(
      AdaptiveGeometryResponse(0.3f, 4f * 1.1f, 3f * 0.95f, 1f, 0f),
    )
    assertThat(rounded).isEqualTo(AdaptiveGeometryResponse(0.3f, 4f, 3f * 1.05f, 1f, 0f))
  }

  @Test
  fun adaptiveOptics_areContinuousAcrossSupportedSizes() {
    val responses = (48..240).map { shortestSideDp ->
      calculateAdaptiveGeometryResponseForLogicalGeometry(shortestSideDp.toFloat(), 1.5f, 0.5f)
    }

    responses.zipWithNext().forEach { (first, second) ->
      assertResponseDeltaBelow(first, second, maximumDelta = 0.02f)
    }
  }

  @Test
  fun adaptiveGeometryResponse_resolvesRefractionStrength() {
    val response = AdaptiveGeometryResponse(
      blurScale = 0.5f,
      displacementScale = 2f,
      reachScale = 1.5f,
      toneGain = 1.1f,
      neutralLiftWeight = 0.1f,
    )

    assertThat(response.resolve(0f)).isEqualTo(AdaptiveGeometryResponse.Identity)
    assertThat(response.resolve(1f)).isEqualTo(response)
    assertThat(response.resolve(-1f)).isEqualTo(AdaptiveGeometryResponse.Identity)
    assertThat(response.resolve(2f)).isEqualTo(response)
    assertResponseDeltaBelow(
      response.resolve(0.499f),
      response.resolve(0.501f),
      0.01f,
    )
  }

  @Test
  fun resolvedAdaptiveOptics_preserveZeroBaselinesAndClampReach() {
    val resolved = resolveAdaptiveGeometryOptics(
      response = AdaptiveGeometryResponse(
        blurScale = 0.5f,
        displacementScale = 2f,
        reachScale = 1.5f,
        toneGain = 1.05f,
        neutralLiftWeight = 0.1f,
      ),
      refractionStrength = 1f,
      shortestSidePx = 100f,
      blurRadiusPx = 0f,
      refractionScalePx = 0f,
      refractionHeight = 1f,
    )

    assertThat(resolved.blurRadiusPx).isEqualTo(0f)
    assertThat(resolved.blurSigmaPx).isEqualTo(0f)
    assertThat(resolved.refractionScalePx).isEqualTo(0f)
    assertThat(resolved.refractionHeightPx).isEqualTo(100f)
    assertThat(resolved.toneGain).isEqualTo(1.05f)
    assertThat(resolved.neutralLiftWeight).isEqualTo(0.1f)
  }

  @Test
  fun absoluteLayerPadding_usesLiteralValues() {
    val absolute = GlassOptics.Absolute(
      blurRadius = 32.dp,
      refractionScale = 15f,
    )
    val effect = GlassVisualEffect().apply {
      optics = absolute
    }
    val rect = Rect(0f, 0f, 200f, 100f)

    assertThat(effect.calculateLayerBounds(rect, Density(1f))).isEqualTo(
      rect.inflate(32f + 15f * absolute.refractionStrength + 2f),
    )
  }

  @Test
  fun coordinates_keepMaterialSeparateFromSampleBounds_atFullScale() {
    val coordinates = resolveGlassCoordinates(
      layerSize = Size(140f, 100f),
      layerOffset = Offset(20f, 10f),
      materialSize = Size(100f, 80f),
      scaleFactor = 1f,
    )

    assertThat(coordinates.sampleSize).isEqualTo(Size(140f, 100f))
    assertThat(coordinates.materialOrigin).isEqualTo(Offset(20f, 10f))
    assertThat(coordinates.materialSize).isEqualTo(Size(100f, 80f))
  }

  @Test
  fun coordinates_keepMaterialSeparateFromSampleBounds_atReducedScale() {
    val coordinates = resolveGlassCoordinates(
      layerSize = Size(140f, 100f),
      layerOffset = Offset(20f, 10f),
      materialSize = Size(100f, 80f),
      scaleFactor = 0.75f,
    )

    assertThat(coordinates.sampleSize).isEqualTo(Size(105f, 75f))
    assertThat(coordinates.materialOrigin).isEqualTo(Offset(15f, 7.5f))
    assertThat(coordinates.materialSize).isEqualTo(Size(75f, 60f))
  }

  @Test
  fun roundedSampleSize_matchesRetainedLayerDimensions() {
    val coordinates = resolveGlassCoordinates(
      layerSize = Size(140.8f, 100.8f),
      layerOffset = Offset.Zero,
      materialSize = Size(100f, 80f),
      scaleFactor = 0.75f,
    ).withRoundedSampleSize()

    assertThat(coordinates.sampleSize).isEqualTo(Size(106f, 76f))
  }

  @Test
  fun samplePadding_addsSerialStageSupport() {
    assertThat(
      calculateGlassSamplePaddingPx(
        blurRadiusPx = 8f,
        refractionScale = 12f,
        refractionStrength = 0.5f,
        chromaticAberrationStrength = 0.4f,
        edgeSoftnessPx = 4f,
        foregroundOutsetPx = 0f,
      ),
    ).isEqualTo(19.2f)
  }

  @Test
  fun samplePadding_isIdenticalAtAndAboveSemanticRadiusCap() {
    fun padding(radius: Float) = calculateGlassSamplePaddingPx(
      blurRadiusPx = effectiveSemanticBlurRadiusPx(radius),
      refractionScale = 12f,
      refractionStrength = 0.5f,
      chromaticAberrationStrength = 0.4f,
      edgeSoftnessPx = 4f,
      foregroundOutsetPx = 0f,
    )

    val sourceBounds = Rect(20f, 30f, 120f, 230f)
    val cappedBounds = sourceBounds.inflate(padding(SemanticBlurKernel.MAX_SUPPORTED_RADIUS_PX))
    val overCapBounds = sourceBounds.inflate(
      padding(SemanticBlurKernel.MAX_SUPPORTED_RADIUS_PX + 100f),
    )

    assertThat(overCapBounds).isEqualTo(cappedBounds)
  }

  @Test
  fun refractionDetailWidth_isBoundedByProfileReach() {
    assertThat(
      calculateRefractionDetailWidthPx(
        refractionHeightPx = 20f,
        edgeSoftnessPx = 3f,
        sampleStepPx = 2f,
      ),
    ).isEqualTo(20f)
    assertThat(
      calculateRefractionDetailWidthPx(
        refractionHeightPx = 5f,
        edgeSoftnessPx = 3f,
        sampleStepPx = 2f,
      ),
    ).isEqualTo(5f)
    assertThat(
      calculateRefractionDetailWidthPx(
        refractionHeightPx = 100f,
        edgeSoftnessPx = 12f,
        sampleStepPx = 2f,
      ),
    ).isEqualTo(40f)
  }

  @Test
  fun defaultCircleProfile_sourceDetailSupportMapsToNarrowerInBoundsOutputBand() {
    val regularBaseline = GlassOptics.Absolute()
    val detailWidthPx = calculateRefractionDetailWidthPx(
      refractionHeightPx = 100f,
      edgeSoftnessPx = 3f,
      sampleStepPx = 2f,
    )
    val outputDistancePx = 30f
    val profileX = 1f - outputDistancePx / 100f
    val heightNorm = 1f - sqrt(1f - profileX * profileX)
    val sourceDistancePx = outputDistancePx +
      heightNorm * regularBaseline.refractionStrength * regularBaseline.refractionScale

    assertThat(detailWidthPx).isEqualTo(40f)
    assertThat(sourceDistancePx).isGreaterThan(outputDistancePx)
    assertThat(sourceDistancePx).isGreaterThan(detailWidthPx * .5f)
    assertThat(sourceDistancePx).isLessThan(detailWidthPx)
    assertThat(outputDistancePx).isLessThan(
      detailWidthPx + regularBaseline.refractionStrength * regularBaseline.refractionScale,
    )
  }

  @Test
  fun refractionDetailEdgeWeight_isProfileIndependentAndPeaksInsideItsWidth() {
    val edgeSoftnessPx = 3f
    val detailWidthPx = calculateRefractionDetailWidthPx(
      refractionHeightPx = 20f,
      edgeSoftnessPx = edgeSoftnessPx,
      sampleStepPx = 2f,
    )
    val distances = (0..16).map { detailWidthPx * it / 16f }
    val weights = distances.map { distance ->
      refractionDetailEdgeWeight(distance, edgeSoftnessPx, detailWidthPx)
    }
    val peakDistance = distances[weights.indices.maxBy { weights[it] }]

    assertThat(weights.max()).isGreaterThan(0f)
    assertThat(peakDistance).isGreaterThan(0f)
    assertThat(peakDistance).isLessThan(detailWidthPx)
    assertThat(refractionDetailEdgeWeight(detailWidthPx, edgeSoftnessPx, detailWidthPx))
      .isEqualTo(0f)
    assertThat(refractionDetailEdgeWeight(detailWidthPx + 1f, edgeSoftnessPx, detailWidthPx))
      .isEqualTo(0f)
  }

  @Test
  fun calculateLayerBounds_usesAdaptiveGeometryBlurSupportForSquareEffect() {
    val effect = GlassVisualEffect().apply {
      edgeSoftness = 0.dp
      shape = RoundedCornerShape(0.dp)
    }
    val rect = Rect(0f, 0f, 400f, 200f)
    val density = Density(1f)
    val padding = -effect.calculateLayerBounds(rect, density).left
    val resolved = resolveGlassOptics(
      optics = effect.optics,
      materialSizePx = rect.size,
      density = density,
      cornerRadiiPx = CornerRadii.zero,
    )

    assertThat(resolved.blurRadiusPx).isGreaterThan(GlassOptics.Absolute().blurRadius.value)
    assertThat(padding).isEqualTo(expectedLayerPadding(effect, rect, density))
  }

  @Test
  fun calculateLayerBounds_zeroRefractionUsesEffectiveSemanticBlurRadiusExactly() {
    val effect = GlassVisualEffect().apply {
      optics = GlassOptics.Absolute(
        blurRadius = 32.dp,
        refractionStrength = 0f,
        refractionScale = 0f,
      )
      edgeSoftness = 0.dp
      shape = RoundedCornerShape(0.dp)
    }
    val rect = Rect(10f, 20f, 210f, 100f)
    val density = Density(1f)
    val effectiveBlurRadius = effectiveSemanticBlurRadiusPx(32f)

    assertThat(expectedLayerPadding(effect, rect, density)).isEqualTo(effectiveBlurRadius)
    assertThat(effect.calculateLayerBounds(rect, density)).isEqualTo(rect.inflate(effectiveBlurRadius))
  }

  @Test
  fun calculateLayerBounds_capsuleUsesResolvedGeometryBlurScale() {
    val effect = GlassVisualEffect().apply {
      edgeSoftness = 0.dp
      shape = RoundedCornerShape(40.dp)
    }
    val rect = Rect(0f, 0f, 240f, 80f)
    val density = Density(1f)
    val effectiveBlurRadius = effectiveSemanticBlurRadiusPx(GlassOptics.Absolute().blurRadius.value)
    val expectedPadding = expectedLayerPadding(effect, rect, density)
    val cornerRadii = effect.shape.toCornerRadiiPx(
      rect.size,
      density,
      androidx.compose.ui.unit.LayoutDirection.Ltr,
    )
    val resolved = resolveGlassOptics(
      optics = effect.optics,
      materialSizePx = rect.size,
      density = density,
      cornerRadiiPx = cornerRadii,
    )

    assertThat(resolved.blurRadiusPx).isLessThan(effectiveBlurRadius)
    assertThat(-effect.calculateLayerBounds(rect, density).left).isEqualTo(expectedPadding)
  }

  @Test
  fun calculateLayerBounds_cornerPermutationPreservesMinimumRadiusPadding() {
    val firstShape = RoundedCornerShape(
      topStart = 24.dp,
      topEnd = 32.dp,
      bottomEnd = 40.dp,
      bottomStart = 48.dp,
    )
    val secondShape = RoundedCornerShape(
      topStart = 48.dp,
      topEnd = 40.dp,
      bottomEnd = 32.dp,
      bottomStart = 24.dp,
    )
    val firstEffect = GlassVisualEffect().apply {
      edgeSoftness = 0.dp
      shape = firstShape
    }
    val secondEffect = GlassVisualEffect(firstEffect).apply {
      shape = secondShape
    }
    val rect = Rect(0f, 0f, 240f, 100f)
    val density = Density(1f)

    val firstBounds = firstEffect.calculateLayerBounds(rect, density)
    val secondBounds = secondEffect.calculateLayerBounds(rect, density)

    assertThat(firstBounds).isEqualTo(secondBounds)
    assertThat(firstBounds).isEqualTo(rect.inflate(expectedLayerPadding(firstEffect, rect, density)))
  }

  @Test
  fun calculateLayerBounds_invalidGeometryProducesFiniteInflatedBounds() {
    val effect = GlassVisualEffect().apply {
      optics = GlassOptics.Absolute(
        blurRadius = 32.dp,
        refractionStrength = 1f,
        refractionScale = 0f,
      )
      edgeSoftness = 0.dp
      shape = RoundedCornerShape(0.dp)
    }
    val density = Density(1f)
    val effectiveBlurRadius = effectiveSemanticBlurRadiusPx(32f)
    val invalidRects = listOf(
      Rect(10f, 20f, 10f, 100f),
      Rect(10f, 20f, 210f, 20f),
      Rect.Zero,
    )

    invalidRects.forEach { rect ->
      val bounds = effect.calculateLayerBounds(rect, density)

      assertThat(bounds.left.isFinite()).isEqualTo(true)
      assertThat(bounds.top.isFinite()).isEqualTo(true)
      assertThat(bounds.right.isFinite()).isEqualTo(true)
      assertThat(bounds.bottom.isFinite()).isEqualTo(true)
      assertThat(bounds).isEqualTo(rect.inflate(effectiveBlurRadius))
    }
  }

  private fun expectedLayerPadding(
    effect: GlassVisualEffect,
    rect: Rect,
    density: Density,
  ): Float {
    val cornerRadii = effect.shape.toCornerRadiiPx(
      rect.size,
      density,
      androidx.compose.ui.unit.LayoutDirection.Ltr,
    )
    val resolved = resolveGlassOptics(
      optics = effect.optics,
      materialSizePx = rect.size,
      density = density,
      cornerRadiiPx = cornerRadii,
    )
    return calculateGlassSamplePaddingPx(
      blurRadiusPx = resolved.blurRadiusPx,
      refractionScale = resolved.refractionScalePx,
      refractionStrength = resolved.refractionStrength,
      chromaticAberrationStrength = effect.chromaticAberrationStrength,
      edgeSoftnessPx = with(density) { effect.edgeSoftness.toPx() },
      foregroundOutsetPx = 0f,
    )
  }

  private fun testRenderParams(
    coordinates: GlassCoordinates = GlassCoordinates(
      sampleSize = Size(100f, 100f),
      materialOrigin = Offset.Zero,
      materialSize = Size(100f, 100f),
      scaleFactor = 1f,
    ),
    refractionStrength: Float = 0f,
    depth: Float = 0f,
    blurRadiusPx: Float = 0f,
    refractionScalePx: Float = 0f,
  ) = GlassRenderParams(
    coordinates = coordinates,
    refractionStrength = refractionStrength,
    specularIntensity = 1f,
    depth = depth,
    ambientResponse = 1f,
    tint = GlassDefaults.tint,
    edgeSoftnessPx = 1f,
    blurRadiusPx = blurRadiusPx,
    blurSigmaPx = SemanticBlurKernel.radiusToSigma(blurRadiusPx),
    progressive = null,
    refractionHeightPx = 25f,
    chromaticAberrationStrength = 0f,
    surfaceProfile = 0.5f,
    chromaticAberrationMode = 0f,
    contrast = 1f,
    whitePoint = 1f,
    chromaMultiplier = 1f,
    refractionScalePx = refractionScalePx,
    contentNormalBlend = 0f,
    specularExponent = 1f,
    fresnelExponent = 1f,
    geometryToneGain = 1f,
    geometryNeutralLift = 0f,
    cornerRadii = CornerRadii.zero,
    lightPosition = Offset.Zero,
    sampleStepPx = 1f,
  )

  private fun refractionDetailEdgeWeight(
    distToEdgePx: Float,
    edgeSoftnessPx: Float,
    detailWidthPx: Float,
  ): Float {
    fun smootherstep(value: Float): Float {
      val t = value.coerceIn(0f, 1f)
      return t * t * t * (t * (t * 6f - 15f) + 10f)
    }
    val shapeMask = smootherstep(distToEdgePx / edgeSoftnessPx)
    val innerEnvelope = smootherstep(
      (distToEdgePx - detailWidthPx * 0.5f) / (detailWidthPx * 0.25f),
    )
    val outerEnvelope = 1f - smootherstep(distToEdgePx / detailWidthPx)
    return shapeMask * innerEnvelope * outerEnvelope
  }

  private fun AdaptiveGeometryResponse.values(): List<Float> = listOf(
    blurScale,
    displacementScale,
    reachScale,
    toneGain,
    neutralLiftWeight,
  )

  private fun assertResponseDeltaBelow(
    first: AdaptiveGeometryResponse,
    second: AdaptiveGeometryResponse,
    maximumDelta: Float,
  ) {
    first.values().zip(second.values()).forEach { (firstValue, secondValue) ->
      assertThat(abs(firstValue - secondValue)).isLessThan(maximumDelta)
    }
  }
}
