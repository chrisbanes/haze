// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.unit.IntSize
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertIs

class GlassRenderBudgetTest {

  @Test
  fun configuredInteractionBudget_usesLocalPatchSize() {
    val patchSize = IntSize(240, 240)
    val plan = buildGlassBudgetLayerPlan(
      sampleSize = IntSize(1000, 600),
      blurRadiusPx = 0f,
      depth = 0f,
      allowMultiscaleBlur = true,
      refractionDetailActive = true,
      rimActive = false,
      interactionPatchSize = patchSize,
      interactionOpticsActive = true,
      interactionLightingActive = true,
    )

    assertThat(
      plan.layers.filter { it.kind.name.startsWith("Interaction") }.map { it.size },
    ).containsExactly(patchSize, patchSize, patchSize)
  }

  @Test
  fun fractionalAlpha_addsMaterialSizedGroupCompositeToBudget() {
    val plan = buildGlassBudgetLayerPlan(
      sampleSize = IntSize(100, 100),
      groupCompositeSize = IntSize(200, 300),
      blurRadiusPx = 0f,
      depth = 0f,
      allowMultiscaleBlur = true,
      refractionDetailActive = false,
      rimActive = false,
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
  fun everyActiveStage_contributesItsActualPixelCount() {
    val plan = GlassRetainedLayerPlan(
      listOf(
        GlassRetainedLayer(GlassRetainedLayerKind.Source, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.BlurPrefilter, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.BlurHorizontal, IntSize(500, 500)),
        GlassRetainedLayer(GlassRetainedLayerKind.Blurred, IntSize(500, 500)),
        GlassRetainedLayer(GlassRetainedLayerKind.DepthMixed, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.Optical, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.RefractionDetail, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.Rim, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.InteractionOptical, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.InteractionDetail, IntSize(1000, 1000)),
        GlassRetainedLayer(GlassRetainedLayerKind.InteractionLighting, IntSize(1000, 1000)),
      ),
    )

    assertThat(plan.retainedPixelCountOrNull()).isEqualTo(9_500_000L)
    assertThat(plan.fitsGlassRenderBudget()).isTrue()
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
    val runtime = assertIs<GlassRenderBudgetDecision.Runtime>(result)

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
    val result = assertIs<GlassRenderBudgetDecision.Runtime>(
      resolveGlassRenderBudget(0.125f) { squarePlan(it, 8192) },
    )

    assertThat(result.scaleFactor).isEqualTo(0.125f)
  }

  @Test
  fun blurDownsampleThreshold_findsSafeIntervalAboveUnsafeAutomaticFloor() {
    val result = resolveGlassRenderBudget(1f) { scale ->
      blurThresholdPlan(scale = scale, sideAtFullScale = 7_370, blurRadiusAtFullScale = 85.49f)
    }
    val runtime = assertIs<GlassRenderBudgetDecision.Runtime>(result)

    assertThat(runtime.scaleFactor).isGreaterThanOrEqualTo(0.261f)
    assertThat(checkNotNull(runtime.plan.retainedPixelCountOrNull())).isLessThanOrEqualTo(
      MAX_GLASS_RETAINED_PIXELS,
    )
  }

  @Test
  fun narrowBlurPrefilterIsland_selectsFirstSafeScaleAboveTopologyTransition() {
    val result = resolveGlassRenderBudget(1f) { scale ->
      val side = (2_191 * scale).roundToInt().coerceAtLeast(1)
      buildGlassBudgetLayerPlan(
        sampleSize = IntSize(side, side),
        blurRadiusPx = 22.24324f * scale,
        depth = 1f,
        allowMultiscaleBlur = true,
        refractionDetailActive = false,
        rimActive = false,
        interactionOpticsActive = false,
        interactionLightingActive = false,
      )
    }
    val runtime = assertIs<GlassRenderBudgetDecision.Runtime>(result)

    assertThat(runtime.scaleFactor).isGreaterThanOrEqualTo(0.9993f)
    assertThat(runtime.scaleFactor).isLessThanOrEqualTo(0.9994f)
    assertThat(runtime.plan.layers.any { it.kind == GlassRetainedLayerKind.BlurPrefilter })
      .isTrue()
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
        if (blurPlan.requiresPrefilter) {
          add(GlassRetainedLayer(GlassRetainedLayerKind.BlurPrefilter, sampleSize))
        }
        add(GlassRetainedLayer(GlassRetainedLayerKind.BlurHorizontal, blurPlan.workingSize))
        add(GlassRetainedLayer(GlassRetainedLayerKind.Blurred, blurPlan.workingSize))
        add(GlassRetainedLayer(GlassRetainedLayerKind.Optical, sampleSize))
        add(GlassRetainedLayer(GlassRetainedLayerKind.Rim, sampleSize))
      },
    )
  }
}
