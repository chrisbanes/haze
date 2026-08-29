// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import kotlin.math.sqrt
import kotlin.test.Test

class GlassRenderParamsTest {

  @Test
  fun lightAlignment_defaultCenterPreservesFractionalPixelsForOddSize() {
    val materialSize = Size(101f, 81f)
    val effect = GlassRuntimeEffect()

    LayoutDirection.entries.forEach { layoutDirection ->
      val resolved = resolveGlassStyle(
        effect = effect,
        materialSizePx = materialSize,
        density = Density(1f),
        layoutDirection = layoutDirection,
      )

      assertThat(resolved.lightPosition, name = layoutDirection.name)
        .isEqualTo(Offset(50.5f, 40.5f))
    }
  }

  @Test
  fun lightAlignment_fractionalBiasPreservesSubpixelPositionAndLayoutDirection() {
    val materialSize = Size(101f, 81f)
    val effect = GlassRuntimeEffect().apply {
      style = GlassStyle {
        lightPosition(BiasAlignment(horizontalBias = 0.25f, verticalBias = -0.5f))
      }
    }
    val expectedPositions = mapOf(
      LayoutDirection.Ltr to Offset(63.125f, 20.25f),
      LayoutDirection.Rtl to Offset(37.875f, 20.25f),
    )

    expectedPositions.forEach { (layoutDirection, expected) ->
      val resolved = resolveGlassStyle(
        effect = effect,
        materialSizePx = materialSize,
        density = Density(1f),
        layoutDirection = layoutDirection,
      )

      assertThat(resolved.lightPosition, name = layoutDirection.name).isEqualTo(expected)
    }
  }

  @Test
  fun lightAlignment_fractionalAbsoluteBiasPreservesSubpixelPosition() {
    val materialSize = Size(101f, 81f)
    val effect = GlassRuntimeEffect().apply {
      style = GlassStyle {
        lightPosition(BiasAbsoluteAlignment(horizontalBias = 0.25f, verticalBias = -0.5f))
      }
    }

    LayoutDirection.entries.forEach { layoutDirection ->
      val resolved = resolveGlassStyle(
        effect = effect,
        materialSizePx = materialSize,
        density = Density(1f),
        layoutDirection = layoutDirection,
      )

      assertThat(resolved.lightPosition, name = layoutDirection.name)
        .isEqualTo(Offset(63.125f, 20.25f))
    }
  }

  @Test
  fun lightAlignment_arbitraryAlignmentUsesComposeAlignmentContract() {
    val alignment = object : Alignment {
      override fun align(
        size: IntSize,
        space: IntSize,
        layoutDirection: LayoutDirection,
      ): IntOffset = IntOffset(space.width - 7, space.height - 9)
    }
    val effect = GlassRuntimeEffect().apply {
      style = GlassStyle { lightPosition(alignment) }
    }

    val resolved = resolveGlassStyle(
      effect = effect,
      materialSizePx = Size(101f, 81f),
      density = Density(1f),
      layoutDirection = LayoutDirection.Rtl,
    )

    assertThat(resolved.lightPosition).isEqualTo(Offset(94f, 72f))
  }

  @Test
  fun lightAlignment_resolvesForEachSizeAndLayoutDirectionAndScalesOnce() {
    val alignments = listOf(
      LightAlignmentCase(Alignment.Center, 0.5f, 0.5f, 0.5f),
      LightAlignmentCase(Alignment.TopStart, 0f, 1f, 0f),
      LightAlignmentCase(Alignment.TopEnd, 1f, 0f, 0f),
      LightAlignmentCase(Alignment.BottomStart, 0f, 1f, 1f),
      LightAlignmentCase(Alignment.BottomEnd, 1f, 0f, 1f),
      LightAlignmentCase(Alignment.CenterStart, 0f, 1f, 0.5f),
      LightAlignmentCase(Alignment.CenterEnd, 1f, 0f, 0.5f),
    )
    val sizes = listOf(Size(100f, 80f), Size(240f, 120f))

    sizes.forEach { size ->
      LayoutDirection.entries.forEach { layoutDirection ->
        alignments.forEach { case ->
          val effect = GlassRuntimeEffect().apply {
            style = GlassStyle { lightPosition(case.alignment) }
          }
          val resolved = resolveGlassStyle(
            effect = effect,
            materialSizePx = size,
            density = Density(1f),
            layoutDirection = layoutDirection,
          )
          val expected = Offset(
            x = size.width * case.xFraction(layoutDirection),
            y = size.height * case.yFraction,
          )
          val rendered = buildGlassRenderParams(
            style = resolved,
            coordinates = GlassCoordinates(
              sampleSize = size * 0.5f,
              materialOrigin = Offset.Zero,
              materialSize = size * 0.5f,
              scaleFactor = 0.5f,
            ),
          )

          assertThat(
            resolved.lightPosition,
            name = "$size/$layoutDirection/${case.alignment}",
          ).isEqualTo(expected)
          assertThat(
            rendered.lightPosition,
            name = "$size/$layoutDirection/${case.alignment}/scaled",
          ).isEqualTo(expected * 0.5f)
        }
      }
    }
  }

  @Test
  fun interactionTopology_usesConfiguredWorstCaseInsteadOfAnimatedValues() {
    val effect = GlassRuntimeEffect().apply {
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
    val effect = GlassRuntimeEffect().apply {
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
    val topology = GlassInteractionTopology(true, true, 1.2f)
    val expectedSize = calculateGlassInteractionPatchSize(
      params = params,
      radiusFraction = 30f / params.coordinates.materialSize.minDimension,
      topology = topology,
    )
    val patch = checkNotNull(
      resolveGlassInteractionPatch(
        params,
        GlassInteractionUniforms(Offset(0f, 0f), 30f, 1f, 1.2f, 0.04f),
        topology,
      ),
    )

    assertThat(patch.bounds.left).isEqualTo(0)
    assertThat(patch.bounds.top).isEqualTo(0)
    assertThat(patch.bounds.size).isEqualTo(expectedSize)
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
  fun interactionOutputFeather_scalesWithRadiusAndKeepsSampleStepMinimum() {
    assertThat(
      calculateGlassInteractionOutputFeatherWidth(radiusPx = 200f, sampleStepPx = 1f),
      name = "radius-scaled feather",
    ).isEqualTo(50f)
    assertThat(
      calculateGlassInteractionOutputFeatherWidth(radiusPx = 2f, sampleStepPx = 1f),
      name = "sample-step minimum",
    ).isEqualTo(1f)
  }

  @Test
  fun interactionPatch_isIndependentOfPrecomputedBlur() {
    val coordinates = GlassCoordinates(
      Size(1000f, 600f),
      Offset.Zero,
      Size(1000f, 600f),
      1f,
    )
    val topology = GlassInteractionTopology(true, true, 1.2f)
    val uniforms = GlassInteractionUniforms(Offset(500f, 300f), 60f, 1f, 1.1f, 0.04f)
    val sharp = checkNotNull(
      resolveGlassInteractionPatch(
        testRenderParams(coordinates = coordinates),
        uniforms,
        topology,
      ),
    )
    val blurred = checkNotNull(
      resolveGlassInteractionPatch(
        testRenderParams(
          coordinates = coordinates,
          depth = 1f,
          blurRadiusPx = 38.5f,
        ),
        uniforms,
        topology,
      ),
    )

    assertThat(blurred.compositeBounds).isEqualTo(sharp.compositeBounds)
    assertThat(blurred.bounds).isEqualTo(sharp.bounds)
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

    assertThat(reserved.width).isGreaterThanOrEqualTo(runtime.bounds.width)
    assertThat(reserved.height).isGreaterThanOrEqualTo(runtime.bounds.height)
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
  fun resolvedStyle_propagatesValidBoundariesEquallyAcrossPrecedenceLevels() {
    val inheritedStyle = GlassStyle {
      specularIntensity(1f)
      ambientResponse(0f)
      specularExponent(Float.MAX_VALUE)
      fresnelExponent(0f)
      alpha(1f)
      contrast(1f)
      whitePoint(-1f)
      chromaMultiplier(2f)
      edgeSoftness(Float.MAX_VALUE.dp)
      contentNormalBlend(1f)
      chromaticAberrationStrength(0f)
    }
    val effects = listOf(
      GlassRuntimeEffect().apply { style = inheritedStyle },
      GlassRuntimeEffect().apply { compositionLocalStyle = inheritedStyle },
    )
    val size = Size(100f, 80f)
    val density = Density(1f)
    val resolved = effects.map {
      resolveGlassStyle(it, size, density, LayoutDirection.Ltr)
    }

    assertThat(resolved[1]).isEqualTo(resolved[0])
    assertThat(resolved[0].specularIntensity).isEqualTo(1f)
    assertThat(resolved[0].ambientResponse).isEqualTo(0f)
    assertThat(resolved[0].specularExponent).isEqualTo(Float.MAX_VALUE)
    assertThat(resolved[0].fresnelExponent).isEqualTo(0f)
    assertThat(resolved[0].alpha).isEqualTo(1f)
    assertThat(resolved[0].contrast).isEqualTo(1f)
    assertThat(resolved[0].whitePoint).isEqualTo(-1f)
    assertThat(resolved[0].chromaMultiplier).isEqualTo(2f)
    assertThat(resolved[0].edgeSoftnessPx).isEqualTo(Float.MAX_VALUE)
    assertThat(resolved[0].contentNormalBlend).isEqualTo(1f)
    assertThat(resolved[0].chromaticAberrationStrength).isEqualTo(0f)

    val rect = Rect(0f, 0f, size.width, size.height)
    val bounds = effects.map { it.calculateLayerBounds(rect, density) }
    assertThat(bounds[1]).isEqualTo(bounds[0])
  }

  @Test
  fun resolvedStyle_densityOverflowUsesSafeDefaultEdgeSoftness() {
    val effect = GlassRuntimeEffect().apply {
      style = GlassStyle { edgeSoftness(Float.MAX_VALUE.dp) }
    }
    val density = Density(2f)

    val resolved = resolveGlassStyle(
      effect = effect,
      materialSizePx = Size(100f, 80f),
      density = density,
      layoutDirection = LayoutDirection.Ltr,
    )

    assertThat(resolved.edgeSoftnessPx)
      .isEqualTo(with(density) { GlassDefaults.edgeSoftness.toPx() })
  }

  @Test
  fun resolvedStyle_nonFiniteOrNegativeCornerRadiiUseSafeDefaultRadii() {
    val defaultRadii = GlassDefaults.shape.toCornerRadiiPx(
      layerSize = Size(100f, 80f),
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
    )

    listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f).forEach { invalidRadius ->
      val effect = GlassRuntimeEffect().apply {
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
    val effect = GlassRuntimeEffect().apply {
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
  fun preparedRender_carriesValidatedAlphaForRuntimeDrawing() {
    val effect = GlassRuntimeEffect().apply {
      style = GlassStyle { alpha(1f) }
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

    assertThat(prepared.alpha).isEqualTo(1f)
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
  fun fallbackInteractionLighting_preservesRadiusAndCanonicalizesAnimatedState() {
    val uniforms = resolveFallbackGlassInteraction(
      state = GlassInteractionRenderState(
        position = Offset(Float.NaN, Float.POSITIVE_INFINITY),
        lightingIntensity = 1.5f,
        refractionMultiplier = Float.POSITIVE_INFINITY,
        whitePointDelta = Float.NaN,
      ),
      radiusFraction = 2f,
      size = Size(100f, 80f),
    )

    assertThat(uniforms.position).isEqualTo(Offset(50f, 40f))
    assertThat(uniforms.radiusPx).isEqualTo(160f)
    assertThat(uniforms.lightingIntensity).isEqualTo(1f)
    assertThat(uniforms.refractionMultiplier).isEqualTo(1f)
    assertThat(uniforms.whitePointDelta).isEqualTo(0f)
  }

  @Test
  fun fixedSizeValues_resolveSemanticValuesAcrossDensities() {
    val progressive = dev.chrisbanes.haze.HazeProgressive.verticalGradient(
      startIntensity = 0f,
      endIntensity = 1f,
    )
    val optics = GlassOptics(
      refractionStrength = 0.4f,
      refractionHeightFraction = 0.25f,
      refractionDisplacement = 15.dp,
      depth = SizeValue.Fixed(0.6f),
      blurRadius = SizeValue.Fixed(10.dp),
      progressive = progressive,
      refractionFoldStrength = 0.4f,
    )
    val cases = listOf(
      Pair(Size(80f, 240f), Density(1f)),
      Pair(Size(1_200f, 600f), Density(3f)),
    )

    cases.forEach { (size, density) ->
      val resolved = resolveGlassOptics(optics, size, density)

      assertThat(resolved.refractionStrength).isEqualTo(optics.refractionStrength)
      assertThat(resolved.refractionFoldStrength).isEqualTo(optics.refractionFoldStrength)
      assertThat(resolved.refractionHeightPx / size.minDimension)
        .isEqualTo(optics.refractionHeightFraction)
      assertThat(resolved.refractionScalePx)
        .isEqualTo(with(density) { optics.refractionDisplacement.toPx() })
      assertThat(resolved.depth)
        .isEqualTo((optics.depth as SizeValue.Fixed).value)
      assertThat(resolved.blurRadiusPx / density.density)
        .isEqualTo((optics.blurRadius as SizeValue.Fixed).value.value)
      assertThat(resolved.progressive).isEqualTo(progressive)
      assertThat(resolved.refractionDetailIntensity).isGreaterThan(0f)
    }
  }

  @Test
  fun builtInStyles_lerpBlurAndDepthByShortestSide() {
    fun resolve(optics: GlassOptics, shortestSide: Dp): ResolvedGlassOptics =
      resolveGlassOptics(
        optics = optics,
        materialSizePx = Size(280f, shortestSide.value),
        density = Density(1f),
      )

    val clearSmall = resolve(GlassStyle.clearOptics, 64.dp)
    val clearMedium = resolve(GlassStyle.clearOptics, 176.dp)
    val clearLarge = resolve(GlassStyle.clearOptics, 220.dp)
    assertThat(clearSmall.depth).isEqualTo(0.1f)
    assertThat(clearSmall.blurRadiusPx).isEqualTo(2f)
    assertThat(clearMedium.depth).isEqualTo(0.32f)
    assertThat(clearMedium.blurRadiusPx).isEqualTo(6f)
    assertThat(clearLarge.depth).isEqualTo(0.52f)
    assertThat(clearLarge.blurRadiusPx).isEqualTo(8f)

    val regularSmall = resolve(GlassDefaults.optics, 64.dp)
    val regularMedium = resolve(GlassDefaults.optics, 176.dp)
    val regularLarge = resolve(GlassDefaults.optics, 220.dp)
    assertThat(regularSmall.depth).isEqualTo(0f)
    assertThat(regularSmall.blurRadiusPx).isCloseTo(4f, 0.000001f)
    assertThat(regularMedium.depth).isEqualTo(0.4f)
    assertThat(regularMedium.blurRadiusPx).isCloseTo(10f, 0.000001f)
    assertThat(regularLarge.depth).isEqualTo(0.56f)
    assertThat(regularLarge.blurRadiusPx).isCloseTo(15f, 0.000001f)
  }

  @Test
  fun builtInStyles_areDensityAndRotationInvariant() {
    listOf(GlassDefaults.optics, GlassStyle.clearOptics).forEach { optics ->
      val first = resolveGlassOptics(
        optics = optics,
        materialSizePx = Size(280f, 176f),
        density = Density(1f),
      )
      val second = resolveGlassOptics(
        optics = optics,
        materialSizePx = Size(352f, 560f),
        density = Density(2f),
      )

      assertThat(second.depth).isEqualTo(first.depth)
      assertThat(second.blurRadiusPx / 2f).isCloseTo(first.blurRadiusPx, 0.000001f)
    }
  }

  @Test
  fun responsiveOptics_clampAndSmoothstepEachValueIndependently() {
    val optics = GlassOptics(
      depth = SizeValue.Responsive(
        SizePoint(64.dp, 0f),
        SizePoint(176.dp, 1f),
      ),
      blurRadius = SizeValue.Responsive(
        SizePoint(100.dp, 2.dp),
        SizePoint(200.dp, 12.dp),
      ),
    )
    fun resolve(side: Dp) = resolveGlassOptics(optics, Size(side.value, side.value), Density(1f))

    assertThat(resolve(32.dp).depth).isEqualTo(0f)
    assertThat(resolve(64.dp).depth).isEqualTo(0f)
    assertThat(resolve(92.dp).depth).isEqualTo(0.15625f)
    assertThat(resolve(120.dp).depth).isEqualTo(0.5f)
    assertThat(resolve(176.dp).depth).isEqualTo(1f)
    assertThat(resolve(240.dp).depth).isEqualTo(1f)
    assertThat(resolve(50.dp).blurRadiusPx).isEqualTo(2f)
    assertThat(resolve(150.dp).blurRadiusPx).isEqualTo(7f)
    assertThat(resolve(250.dp).blurRadiusPx).isEqualTo(12f)
  }

  @Test
  fun responsiveOptics_invalidGeometryUsesFirstPoints() {
    val optics = GlassOptics(
      depth = SizeValue.Responsive(
        SizePoint(64.dp, 0.2f),
        SizePoint(176.dp, 0.8f),
      ),
      blurRadius = SizeValue.Responsive(
        SizePoint(64.dp, 4.dp),
        SizePoint(176.dp, 12.dp),
      ),
    )
    val invalidSizes = listOf(
      Size.Zero,
      Size(-1f, 100f),
      Size(100f, -1f),
      Size(100f, 0f),
      Size(Float.NaN, 100f),
      Size(Float.POSITIVE_INFINITY, 100f),
    )
    invalidSizes.forEach { size ->
      val resolved = resolveGlassOptics(optics, size, Density(1f))
      assertThat(resolved.depth).isEqualTo(0.2f)
      assertThat(resolved.blurRadiusPx).isEqualTo(4f)
    }
    listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { densityValue ->
      val invalidDensity = resolveGlassOptics(
        optics,
        Size(200f, 100f),
        Density(densityValue),
      )
      assertThat(invalidDensity.depth).isEqualTo(0.2f)
      // The first authored point is 4.dp, which resolves to zero physical pixels
      // when density is invalid.
      assertThat(invalidDensity.blurRadiusPx).isEqualTo(0f)
    }
  }

  @Test
  fun builtInStyles_preserveAuthoredOpticalValues() {
    val regular = GlassDefaults.optics
    assertThat(regular.refractionStrength).isEqualTo(0.7f)
    assertThat(regular.refractionDisplacement).isEqualTo(48.dp)
    assertThat(regular.refractionHeightFraction).isEqualTo(0.6f)
    assertThat(regular.refractionFoldStrength).isEqualTo(0.65f)
    assertThat(regular.refractionDetailIntensity).isEqualTo(0f)

    val clear = GlassStyle.clearOptics
    assertThat(clear.refractionStrength).isEqualTo(0.85f)
    assertThat(clear.refractionDisplacement).isEqualTo(18.dp)
    assertThat(clear.refractionHeightFraction).isEqualTo(0.22f)
    assertThat(clear.refractionFoldStrength).isEqualTo(0f)
    assertThat(clear.refractionDetailIntensity).isEqualTo(0.76f)
  }

  @Test
  fun equalCallerOptics_resolveLikeBuiltInClear() {
    val callerCopy = GlassStyle.clearOptics.copy()
    val materialSize = Size(320f, 220f)
    val density = Density(1f)
    val builtIn = resolveGlassOptics(
      GlassStyle.clearOptics,
      materialSize,
      density,
    )
    val caller = resolveGlassOptics(callerCopy, materialSize, density)

    assertThat(callerCopy).isEqualTo(GlassStyle.clearOptics)
    assertThat(caller.depth).isEqualTo(0.52f)
    assertThat(caller.blurRadiusPx).isEqualTo(8f)
    assertThat(builtIn.depth).isEqualTo(0.52f)
    assertThat(builtIn.blurRadiusPx).isEqualTo(8f)
  }

  @Test
  fun fixedBlurRadius_usesCurrentPhysicalPixelCapAcrossDensities() {
    val cases = listOf(
      Triple(20.dp, Density(1f), 20f),
      Triple(14.dp, Density(2.75f), 38.5f),
      Triple(20.dp, Density(2f), 38.5f),
      Triple(100.dp, Density(4f), 38.5f),
    )

    cases.forEach { (radius, density, expectedRadiusPx) ->
      val resolved = resolveGlassOptics(
        optics = GlassOptics(blurRadius = SizeValue.Fixed(radius)),
        materialSizePx = Size(200f, 100f),
        density = density,
      )

      assertThat(resolved.blurRadiusPx).isEqualTo(expectedRadiusPx)
    }
  }

  @Test
  fun fixedLayerPadding_usesLiteralValues() {
    val fixed = GlassOptics(
      blurRadius = SizeValue.Fixed(32.dp),
      refractionDisplacement = 15.dp,
    )
    val effect = GlassRuntimeEffect().apply {
      optics = fixed
    }
    val rect = Rect(0f, 0f, 200f, 100f)

    assertThat(effect.calculateLayerBounds(rect, Density(1f))).isEqualTo(
      rect.inflate(32f + 15f * fixed.refractionStrength + 2f),
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
  fun renderParams_deriveBlurSigmaFromScaledRadius() {
    val effect = GlassRuntimeEffect().apply {
      optics = GlassOptics(blurRadius = SizeValue.Fixed(20.dp))
    }
    val style = resolveGlassStyle(
      effect = effect,
      materialSizePx = Size(100f, 80f),
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
    )

    listOf(1f, 0.75f, 0.25f).forEach { scaleFactor ->
      val params = buildGlassRenderParams(
        style = style,
        coordinates = resolveGlassCoordinates(
          layerSize = Size(100f, 80f),
          layerOffset = Offset.Zero,
          materialSize = Size(100f, 80f),
          scaleFactor = scaleFactor,
        ),
      )

      assertThat(params.blurRadiusPx).isEqualTo(20f * scaleFactor)
      assertThat(params.blurSigmaPx).isEqualTo(
        SemanticBlurKernel.radiusToSigma(params.blurRadiusPx),
      )
    }
  }

  @Test
  fun renderParams_scaleResolvedOpticalDistancesExactlyOnce() {
    val effect = GlassRuntimeEffect().apply {
      optics = GlassOptics(
        refractionHeightFraction = 0.25f,
        refractionDisplacement = 12.dp,
        blurRadius = SizeValue.Fixed(10.dp),
        refractionFoldStrength = 0.4f,
      )
    }
    val style = resolveGlassStyle(
      effect = effect,
      materialSizePx = Size(200f, 100f),
      density = Density(2f),
      layoutDirection = LayoutDirection.Ltr,
    )

    val params = buildGlassRenderParams(
      style = style,
      coordinates = resolveGlassCoordinates(
        layerSize = Size(200f, 100f),
        layerOffset = Offset.Zero,
        materialSize = Size(200f, 100f),
        scaleFactor = 0.5f,
      ),
    )

    assertThat(params.refractionScalePx).isEqualTo(12f)
    assertThat(params.refractionHeightPx).isEqualTo(12.5f)
    assertThat(params.refractionFoldStrength).isEqualTo(0.4f)
    assertThat(params.blurRadiusPx).isEqualTo(10f)
    assertThat(params.opticalEffectKey().refractionScalePx).isEqualTo(12f)
    assertThat(params.opticalEffectKey().refractionHeightPx).isEqualTo(12.5f)
    assertThat(params.opticalEffectKey().refractionFoldStrength).isEqualTo(0.4f)
    assertThat(params.refractionDetailEffectKey().refractionFoldStrength).isEqualTo(0.4f)
  }

  @Test
  fun renderParams_applyInputScaleAfterInternalOpticalClamping() {
    val effect = GlassRuntimeEffect().apply {
      optics = GlassOptics(refractionDisplacement = Float.MAX_VALUE.dp)
    }
    val style = resolveGlassStyle(
      effect = effect,
      materialSizePx = Size(200f, 100f),
      density = Density(2f),
      layoutDirection = LayoutDirection.Ltr,
    )

    val params = buildGlassRenderParams(
      style = style,
      coordinates = resolveGlassCoordinates(
        layerSize = Size(200f, 100f),
        layerOffset = Offset.Zero,
        materialSize = Size(200f, 100f),
        scaleFactor = 0.5f,
      ),
    )

    assertThat(style.resolvedOptics.refractionScalePx).isEqualTo(16_384f)
    assertThat(params.refractionScalePx).isEqualTo(8_192f)
  }

  @Test
  fun renderParams_zeroBlurHasZeroRadiusAndSigma() {
    val effect = GlassRuntimeEffect().apply {
      optics = GlassOptics(blurRadius = SizeValue.Fixed(0.dp))
    }
    val style = resolveGlassStyle(
      effect = effect,
      materialSizePx = Size(100f, 80f),
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
    )

    val params = buildGlassRenderParams(
      style = style,
      coordinates = resolveGlassCoordinates(
        layerSize = Size(100f, 80f),
        layerOffset = Offset.Zero,
        materialSize = Size(100f, 80f),
        scaleFactor = 0.25f,
      ),
    )

    assertThat(params.blurRadiusPx).isEqualTo(0f)
    assertThat(params.blurSigmaPx).isEqualTo(0f)
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
  fun blurEffectKey_roundsUnroundedSampleSizeLikeRetainedLayers() {
    val params = testRenderParams(
      coordinates = GlassCoordinates(
        sampleSize = Size(140.8f, 100.8f),
        materialOrigin = Offset.Zero,
        materialSize = Size(100f, 80f),
        scaleFactor = 0.75f,
      ),
      blurRadiusPx = 12f,
    )

    assertThat(params.blurEffectKey().plan.sampleSize).isEqualTo(
      params.coordinates.sampleSize.roundToIntSize(),
    )
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
    val regularBaseline = GlassOptics()
    val detailWidthPx = calculateRefractionDetailWidthPx(
      refractionHeightPx = 100f,
      edgeSoftnessPx = 3f,
      sampleStepPx = 2f,
    )
    val outputDistancePx = 30f
    val profileX = 1f - outputDistancePx / 100f
    val heightNorm = 1f - sqrt(1f - profileX * profileX)
    val sourceDistancePx = outputDistancePx +
      heightNorm * regularBaseline.refractionStrength * regularBaseline.refractionDisplacement.value

    assertThat(detailWidthPx).isEqualTo(40f)
    assertThat(sourceDistancePx).isGreaterThan(outputDistancePx)
    assertThat(sourceDistancePx).isGreaterThan(detailWidthPx * .5f)
    assertThat(sourceDistancePx).isLessThan(detailWidthPx)
    assertThat(outputDistancePx).isLessThan(
      detailWidthPx +
        regularBaseline.refractionStrength * regularBaseline.refractionDisplacement.value,
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
  fun calculateLayerBounds_largeSurfaceUsesResponsiveBlur() {
    val effect = GlassRuntimeEffect().apply {
      edgeSoftness = 0.dp
      shape = RoundedCornerShape(0.dp)
    }
    val rect = Rect(0f, 0f, 440f, 220f)
    val density = Density(1f)
    val padding = -effect.calculateLayerBounds(rect, density).left
    val resolved = resolveGlassOptics(
      optics = effect.optics,
      materialSizePx = rect.size,
      density = density,
    )

    assertThat(resolved.blurRadiusPx).isGreaterThan(14f)
    assertThat(padding).isEqualTo(expectedLayerPadding(effect, rect, density))
  }

  @Test
  fun calculateLayerBounds_zeroRefractionUsesEffectiveSemanticBlurRadiusExactly() {
    val effect = GlassRuntimeEffect().apply {
      optics = GlassOptics(
        blurRadius = SizeValue.Fixed(32.dp),
        refractionStrength = 0f,
        refractionDisplacement = 0.dp,
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
  fun calculateLayerBounds_shortSurfaceUsesResponsiveBlur() {
    val effect = GlassRuntimeEffect().apply {
      edgeSoftness = 0.dp
      shape = RoundedCornerShape(40.dp)
    }
    val rect = Rect(0f, 0f, 240f, 80f)
    val density = Density(1f)
    val effectiveBlurRadius = effectiveSemanticBlurRadiusPx(14f)
    val expectedPadding = expectedLayerPadding(effect, rect, density)
    val resolved = resolveGlassOptics(
      optics = effect.optics,
      materialSizePx = rect.size,
      density = density,
    )

    assertThat(resolved.blurRadiusPx).isLessThan(effectiveBlurRadius)
    assertThat(-effect.calculateLayerBounds(rect, density).left).isEqualTo(expectedPadding)
  }

  @Test
  fun calculateLayerBounds_cornerPermutationDoesNotChangePadding() {
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
    val firstEffect = GlassRuntimeEffect().apply {
      edgeSoftness = 0.dp
      shape = firstShape
    }
    val secondEffect = GlassRuntimeEffect().apply {
      edgeSoftness = 0.dp
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
    val effect = GlassRuntimeEffect().apply {
      optics = GlassOptics(
        blurRadius = SizeValue.Fixed(32.dp),
        refractionStrength = 1f,
        refractionDisplacement = 0.dp,
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
    effect: GlassRuntimeEffect,
    rect: Rect,
    density: Density,
  ): Float {
    val resolved = resolveGlassOptics(
      optics = effect.optics,
      materialSizePx = rect.size,
      density = density,
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
    refractionFoldStrength = 0f,
    specularIntensity = 1f,
    depth = depth,
    ambientResponse = 1f,
    backgroundColor = GlassDefaults.backgroundColor,
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
}

private class LightAlignmentCase(
  val alignment: Alignment,
  val ltrXFraction: Float,
  val rtlXFraction: Float,
  val yFraction: Float,
) {
  fun xFraction(layoutDirection: LayoutDirection): Float = when (layoutDirection) {
    LayoutDirection.Ltr -> ltrXFraction
    LayoutDirection.Rtl -> rtlXFraction
  }
}
