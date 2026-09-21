// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.unit.IntSize
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.math.roundToInt
import kotlin.test.Test

class GlassRenderBudgetTest {

  @Test
  fun backdropWithoutForegroundLayers_hasAValidZeroRetentionPlan() {
    val plan = buildGlassBackdropLayerPlan(
      sampleSize = IntSize(5000, 5000),
      rimActive = false,
      interactionPatchSize = IntSize.Zero,
      interactionLightingActive = false,
    )

    assertThat(plan.layers).isEqualTo(emptyList())
    assertThat(plan.fitsGlassRenderBudget()).isTrue()
  }

  @Test
  fun backdropPlan_countsOnlyLocalForegroundLayers() {
    val sampleSize = IntSize(1000, 600)
    val patchSize = IntSize(240, 240)

    val plan = buildGlassBackdropLayerPlan(
      sampleSize = sampleSize,
      rimActive = true,
      interactionPatchSize = patchSize,
      interactionLightingActive = true,
    )

    assertThat(plan.layers).containsExactly(
      GlassRetainedLayer(GlassRetainedLayerKind.Rim, sampleSize),
      GlassRetainedLayer(GlassRetainedLayerKind.InteractionLighting, patchSize),
    )
  }

  @Test
  fun configuredInteractionBudget_usesLocalPatchSize() {
    val opticsPatchSize = IntSize(120, 120)
    val lightingPatchSize = IntSize(240, 240)
    val plan = buildGlassBudgetLayerPlan(
      sampleSize = IntSize(1000, 600),
      blurRadiusPx = 0f,
      depth = 0f,
      allowNativeWideBlur = true,
      refractionDetailActive = true,
      rimSize = null,
      interactionOpticsPatchSize = opticsPatchSize,
      interactionLightingPatchSize = lightingPatchSize,
      interactionOpticsActive = true,
      interactionLightingActive = true,
    )

    val interactionLayers = plan.layers.filter { it.kind.name.startsWith("Interaction") }
    if (supportsFusedGlassRenderEffect) {
      assertThat(interactionLayers.map { it.size }).containsExactly(lightingPatchSize)
    } else {
      assertThat(interactionLayers.map { it.size }).containsExactly(
        *List(4) { opticsPatchSize }.toTypedArray(),
        lightingPatchSize,
      )
    }
  }

  @Test
  fun fusedInteractionOptics_doesNotAddGroupCompositeToBudget() {
    val outputSize = IntSize(1000, 600)
    val result = resolveGlassGroupCompositeSize(
      outputSize = outputSize,
      scaleFactor = 1f,
      alpha = 1f,
      interactionLayersActive = true,
      interactionTopology = GlassInteractionTopology(
        hasOptics = true,
        hasLighting = false,
        maxRefractionMultiplier = 1.1f,
      ),
    )

    if (supportsFusedGlassRenderEffect) {
      assertThat(result).isNull()
    } else {
      assertThat(result).isEqualTo(outputSize)
    }
  }

  @Test
  fun reducedSources_addOneOutputSizedFinalizerToBudget() {
    val outputSize = IntSize(1000, 600)

    assertThat(
      resolveGlassGroupCompositeSize(
        outputSize = outputSize,
        scaleFactor = 0.5f,
        alpha = 1f,
        interactionLayersActive = false,
        interactionTopology = GlassInteractionTopology(false, false, 1f),
      ),
    ).isEqualTo(outputSize)

    assertThat(
      resolveGlassGroupCompositeSize(
        outputSize = outputSize,
        scaleFactor = 1f,
        alpha = 1f,
        interactionLayersActive = false,
        interactionTopology = GlassInteractionTopology(false, false, 1f),
      ),
    ).isNull()
  }

  @Test
  fun reducedSources_budgetFullResolutionForegroundSeparatelyFromOptics() {
    val plan = buildGlassBudgetLayerPlan(
      sampleSize = IntSize(500, 300),
      groupCompositeSize = IntSize(1000, 600),
      blurRadiusPx = 0f,
      depth = 0f,
      allowNativeWideBlur = true,
      refractionDetailActive = false,
      rimSize = IntSize(1200, 800),
      interactionOpticsPatchSize = IntSize(120, 120),
      interactionLightingPatchSize = IntSize(240, 240),
      interactionOpticsActive = true,
      interactionLightingActive = true,
    )

    assertThat(plan.layers.filter { it.kind == GlassRetainedLayerKind.Rim })
      .containsExactly(GlassRetainedLayer(GlassRetainedLayerKind.Rim, IntSize(1200, 800)))
    assertThat(plan.layers.filter { it.kind == GlassRetainedLayerKind.InteractionLighting })
      .containsExactly(
        GlassRetainedLayer(GlassRetainedLayerKind.InteractionLighting, IntSize(240, 240)),
      )
    assertThat(plan.layers.filter { it.kind == GlassRetainedLayerKind.GroupComposite })
      .containsExactly(
        GlassRetainedLayer(GlassRetainedLayerKind.GroupComposite, IntSize(1000, 600)),
      )
  }

  @Test
  fun fractionalAlpha_addsMaterialSizedGroupCompositeToBudget() {
    val plan = buildGlassBudgetLayerPlan(
      sampleSize = IntSize(100, 100),
      groupCompositeSize = IntSize(200, 300),
      blurRadiusPx = 0f,
      depth = 0f,
      allowNativeWideBlur = true,
      refractionDetailActive = false,
      rimSize = null,
      interactionOpticsActive = false,
      interactionLightingActive = false,
    )

    assertThat(plan.layers.last()).isEqualTo(
      GlassRetainedLayer(GlassRetainedLayerKind.GroupComposite, IntSize(200, 300)),
    )
  }

  @Test
  fun exactLimits_fitWithoutChangingRequestedScale() {
    val plan = GlassRetainedLayerPlan(
      listOf(GlassRetainedLayer(GlassRetainedLayerKind.Source, IntSize(4096, 4096))),
    )

    assertThat(resolveGlassRenderBudget(1f) { plan }).isEqualTo(
      GlassRenderBudgetDecision.Runtime(scaleFactor = 1f, plan = plan),
    )
  }

  @Test
  fun suppliedRequestedPlan_isNotRebuilt() {
    val plan = GlassRetainedLayerPlan(
      listOf(GlassRetainedLayer(GlassRetainedLayerKind.Source, IntSize(100, 100))),
    )
    var buildCount = 0

    assertThat(
      resolveGlassRenderBudget(
        requestedScale = 0.5f,
        requestedPlan = plan,
      ) {
        buildCount += 1
        plan
      },
    ).isEqualTo(GlassRenderBudgetDecision.Runtime(scaleFactor = 0.5f, plan = plan))
    assertThat(buildCount).isEqualTo(0)
  }

  @Test
  fun everyActiveStage_contributesItsActualPixelCount() {
    val plan = GlassRetainedLayerPlan(
      listOf(
        GlassRetainedLayer(GlassRetainedLayerKind.Source, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.BlurHorizontal, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.Blurred, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.DepthMixed, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.Optical, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.RefractionDetail, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.Rim, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.InteractionOptical, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.InteractionDetail, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.InteractionLighting, IntSize(1000, 1000)),
      ),
    )

    assertThat(plan.retainedPixelCountOrNull()).isEqualTo(10_000_000L)
    assertThat(plan.fitsGlassRenderBudget()).isTrue()
  }

  @Test
  fun wideNativeBlur_budgetsOnlyItsFullSizeOutput() {
    val plan = buildGlassBudgetLayerPlan(
      sampleSize = IntSize(1_080, 1_920),
      blurRadiusPx = 84f,
      depth = 1f,
      allowNativeWideBlur = true,
      refractionDetailActive = false,
      rimActive = false,
      interactionOpticsActive = false,
      interactionLightingActive = false,
    )

    if (!supportsFusedGlassRenderEffect) {
      assertThat(plan.layers.filter { it.kind.name.startsWith("Blur") }).containsExactly(
        GlassRetainedLayer(GlassRetainedLayerKind.Blurred, IntSize(1_080, 1_920)),
      )
    }
  }

  @Test
  fun dimensionOnePixelOver_doesNotFit() {
    val plan = GlassRetainedLayerPlan(
      listOf(GlassRetainedLayer(GlassRetainedLayerKind.Source, IntSize(4097, 1))),
    )

    assertThat(plan.fitsGlassRenderBudget()).isEqualTo(false)
  }

  @Test
  fun combinedPixelsOneOver_doesNotFit() {
    val plan = GlassRetainedLayerPlan(
      listOf(
        GlassRetainedLayer(GlassRetainedLayerKind.Source, IntSize(4096, 4096)),
        GlassRetainedLayer(GlassRetainedLayerKind.Rim, IntSize(1, 1)),
      ),
    )

    assertThat(plan.retainedPixelCountOrNull()).isEqualTo(16_777_217L)
    assertThat(plan.fitsGlassRenderBudget()).isEqualTo(false)
  }

  @Test
  fun invalidAndOverflowingDimensions_returnInvalidFallback() {
    assertThat(
      resolveGlassRenderBudget(1f) {
        GlassRetainedLayerPlan(
          listOf(GlassRetainedLayer(GlassRetainedLayerKind.Source, IntSize(0, 1))),
        )
      },
    ).isEqualTo(GlassRenderBudgetDecision.Fallback(GlassRenderBudgetFallbackReason.InvalidGeometry))

    assertThat(
      resolveGlassRenderBudget(1f) {
        GlassRetainedLayerPlan(
          List(3) {
            GlassRetainedLayer(GlassRetainedLayerKind.Source, IntSize(Int.MAX_VALUE, Int.MAX_VALUE))
          },
        )
      },
    ).isEqualTo(GlassRenderBudgetDecision.Fallback(GlassRenderBudgetFallbackReason.InvalidGeometry))
  }

  @Test
  fun overBudgetRequest_selectsLargestSafeScale() {
    val result = resolveGlassRenderBudget(1f) { squarePlan(it, 8192) }
    val runtime = result.assertRuntime()

    assertThat(runtime.scaleFactor).isGreaterThanOrEqualTo(0.5f)
    assertThat(runtime.scaleFactor).isLessThanOrEqualTo(4096.5f / 8192f)
    assertThat(runtime.plan.layers.single().size).isEqualTo(IntSize(4096, 4096))
    assertThat(runtime.plan.fitsGlassRenderBudget()).isTrue()
  }

  @Test
  fun automaticScaleFloor_thatStillDoesNotFit_usesFallback() {
    assertThat(resolveGlassRenderBudget(1f) { squarePlan(it, 20_000) })
      .isEqualTo(GlassRenderBudgetDecision.Fallback(GlassRenderBudgetFallbackReason.ExceedsLimits))
  }

  @Test
  fun explicitlyRequestedSubFloorScale_isNeverIncreased() {
    val result = resolveGlassRenderBudget(0.125f) { squarePlan(it, 8192) }.assertRuntime()

    assertThat(result.scaleFactor).isEqualTo(0.125f)
  }

  @Test
  fun nativeBlurThreshold_findsSafeIntervalAboveUnsafeAutomaticFloor() {
    val result = resolveGlassRenderBudget(1f) { scale ->
      blurThresholdPlan(scale = scale, sideAtFullScale = 7_370, blurRadiusAtFullScale = 15.384615f)
    }
    val runtime = result.assertRuntime()

    assertThat(runtime.scaleFactor).isGreaterThanOrEqualTo(0.261f)
    assertThat(checkNotNull(runtime.plan.retainedPixelCountOrNull())).isLessThanOrEqualTo(
      MAX_GLASS_RETAINED_PIXELS,
    )
  }

  @Test
  fun fusedPlan_doesNotBudgetRetainedBlurTopology() {
    val result = resolveGlassRenderBudget(1f) { scale ->
      val side = (2_191 * scale).roundToInt().coerceAtLeast(1)
      buildGlassBudgetLayerPlan(
        sampleSize = IntSize(side, side),
        blurRadiusPx = 22.24324f * scale,
        depth = 1f,
        allowNativeWideBlur = true,
        refractionDetailActive = false,
        rimActive = false,
        interactionOpticsActive = false,
        interactionLightingActive = false,
      )
    }
    val runtime = result.assertRuntime()

    if (supportsFusedGlassRenderEffect) {
      assertThat(runtime.scaleFactor).isEqualTo(1f)
      assertThat(runtime.plan.layers.map { it.kind }).containsExactly(
        GlassRetainedLayerKind.Source,
        GlassRetainedLayerKind.Optical,
      )
    } else {
      assertThat(runtime.scaleFactor).isEqualTo(1f)
      assertThat(runtime.plan.layers.any { it.kind == GlassRetainedLayerKind.BlurHorizontal })
        .isFalse()
    }
    assertThat(runtime.plan.fitsGlassRenderBudget()).isTrue()
  }

  private fun squarePlan(scale: Float, sideAtFullScale: Int): GlassRetainedLayerPlan {
    val side = (sideAtFullScale * scale).roundToInt().coerceAtLeast(1)
    return GlassRetainedLayerPlan(
      listOf(GlassRetainedLayer(GlassRetainedLayerKind.Source, IntSize(side, side))),
    )
  }

  private fun blurThresholdPlan(
    scale: Float,
    sideAtFullScale: Int,
    blurRadiusAtFullScale: Float,
  ): GlassRetainedLayerPlan {
    val side = (sideAtFullScale * scale).roundToInt().coerceAtLeast(1)
    val sampleSize = IntSize(side, side)
    val blurRadius = blurRadiusAtFullScale * scale
    val blurPlan = SemanticBlurPlan.createForSigma(
      sampleWidth = side,
      sampleHeight = side,
      effectiveRadiusPx = blurRadius,
      sigmaPx = SemanticBlurKernel.radiusToSigma(blurRadius),
    )
    return GlassRetainedLayerPlan(
      buildList {
        add(GlassRetainedLayer(GlassRetainedLayerKind.Source, sampleSize))
        if (blurPlan.usesNativeWideBlur) {
          add(GlassRetainedLayer(GlassRetainedLayerKind.Blurred, sampleSize))
        } else {
          add(GlassRetainedLayer(GlassRetainedLayerKind.BlurHorizontal, sampleSize))
          add(GlassRetainedLayer(GlassRetainedLayerKind.Blurred, sampleSize))
        }
        add(GlassRetainedLayer(GlassRetainedLayerKind.Optical, sampleSize))
        add(GlassRetainedLayer(GlassRetainedLayerKind.Rim, sampleSize))
      },
    )
  }
}

private fun GlassRenderBudgetDecision.assertRuntime(): GlassRenderBudgetDecision.Runtime {
  assertThat(this).isInstanceOf<GlassRenderBudgetDecision.Runtime>()
  return this as GlassRenderBudgetDecision.Runtime
}
