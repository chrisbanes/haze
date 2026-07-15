// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GlassRenderParamsTest {

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
    val detailWidthPx = calculateRefractionDetailWidthPx(
      refractionHeightPx = 100f,
      edgeSoftnessPx = 3f,
      sampleStepPx = 2f,
    )
    val outputDistancePx = 30f
    val profileX = 1f - outputDistancePx / 100f
    val heightNorm = 1f - sqrt(1f - profileX * profileX)
    val sourceDistancePx = outputDistancePx +
      heightNorm * GlassDefaults.refractionStrength * GlassDefaults.refractionScale

    assertThat(detailWidthPx).isEqualTo(40f)
    assertThat(sourceDistancePx).isGreaterThan(outputDistancePx)
    assertThat(sourceDistancePx).isGreaterThan(detailWidthPx * .5f)
    assertThat(sourceDistancePx).isLessThan(detailWidthPx)
    assertThat(outputDistancePx).isLessThan(
      detailWidthPx + GlassDefaults.refractionStrength * GlassDefaults.refractionScale,
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
  fun calculateLayerBounds_reservesGeometryCalibratedBlurSupportForSquareEffect() {
    val effect = GlassVisualEffect().apply {
      blurRadius = 100.dp
      refractionStrength = 1f
      refractionScale = 0f
      edgeSoftness = 0.dp
      shape = RoundedCornerShape(0.dp)
    }
    val rect = Rect(0f, 0f, 400f, 200f)
    val density = Density(1f)
    val padding = -effect.calculateLayerBounds(rect, density).left
    val resolvedBlurScale = calculateRegularGeometryProfile(
      materialSize = rect.size,
      cornerRadii = CornerRadii.zero,
      blurRadiusPx = effectiveSemanticBlurRadiusPx(100f),
      refractionHeight = effect.refractionHeight,
    ).resolve(1f).blurScale

    assertThat(resolvedBlurScale).isEqualTo(MAX_REGULAR_GEOMETRY_BLUR_SCALE)
    assertThat(padding).isEqualTo(expectedLayerPadding(effect, rect, density))
  }

  @Test
  fun calculateLayerBounds_zeroRefractionUsesEffectiveSemanticBlurRadiusExactly() {
    val effect = GlassVisualEffect().apply {
      blurRadius = 32.dp
      refractionStrength = 0f
      refractionScale = 0f
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
      blurRadius = 32.dp
      refractionStrength = 1f
      refractionScale = 0f
      edgeSoftness = 0.dp
      shape = RoundedCornerShape(40.dp)
    }
    val rect = Rect(0f, 0f, 240f, 80f)
    val density = Density(1f)
    val effectiveBlurRadius = effectiveSemanticBlurRadiusPx(32f)
    val expectedPadding = expectedLayerPadding(effect, rect, density)

    assertThat(expectedPadding).isLessThan(effectiveBlurRadius * MAX_REGULAR_GEOMETRY_BLUR_SCALE)
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
      blurRadius = 32.dp
      refractionStrength = 1f
      refractionScale = 0f
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
      blurRadius = 32.dp
      refractionStrength = 1f
      refractionScale = 0f
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

  @Test
  fun regularGeometryProfile_representativeProfilesStayWithinConservativeBlurScale() {
    val profiles = listOf(
      CornerRadii.zero,
      CornerRadii(80f, 0f, 40f, 20f),
      CornerRadii(100f, 100f, 100f, 100f),
    )

    profiles.forEach { cornerRadii ->
      val profile = calculateRegularGeometryProfile(
        materialSize = Size(400f, 200f),
        cornerRadii = cornerRadii,
        blurRadiusPx = 100f,
        refractionHeight = 1f,
      )
      listOf(0f, .25f, .5f, 1f).forEach { refractionStrength ->
        assertThat(profile.resolve(refractionStrength).blurScale)
          .isLessThanOrEqualTo(MAX_REGULAR_GEOMETRY_BLUR_SCALE)
      }
    }
  }

  @Test
  fun regularGeometryProfile_isContinuousAndRejectsInvalidGeometry() {
    fun profile(width: Float) = calculateRegularGeometryProfile(
      materialSize = Size(width, 192f),
      cornerRadii = CornerRadii(96f, 96f, 96f, 96f),
      blurRadiusPx = 38.5f,
      refractionHeight = 1f,
    )
    val before = profile(719.9f)
    val after = profile(720.1f)

    assertThat(abs(before.blurScale - after.blurScale)).isLessThan(.001f)
    assertThat(abs(before.profileReachPx - after.profileReachPx)).isLessThan(.1f)
    assertThat(abs(before.toneGain - after.toneGain)).isLessThan(.001f)
    assertFailsWith<IllegalArgumentException> {
      calculateRegularGeometryProfile(Size.Zero, CornerRadii.zero, 0f, 0f)
    }
  }

  @Test
  fun regularGeometryProfile_preservesExactStrengthAndHeightAcrossCornerRadii() {
    fun profile(radii: CornerRadii) = calculateRegularGeometryProfile(
      materialSize = Size(400f, 200f),
      cornerRadii = radii,
      blurRadiusPx = 24f,
      refractionHeight = .5f,
    )
    val square = profile(CornerRadii.zero)
    val lowRadius = profile(CornerRadii(12f, 12f, 12f, 12f))
    val asymmetric = profile(CornerRadii(100f, 0f, 0f, 0f))
    val rounded = profile(CornerRadii(100f, 100f, 100f, 100f))
    val fixedMinimum = profile(CornerRadii(20f, 20f, 20f, 20f))
    val changedDistantCorner = profile(CornerRadii(100f, 20f, 20f, 20f))

    assertThat(asymmetric).isEqualTo(square)
    assertThat(changedDistantCorner).isEqualTo(fixedMinimum)
    assertThat(lowRadius.toneGain).isGreaterThan(square.toneGain)
    assertThat(rounded.toneGain).isGreaterThan(lowRadius.toneGain)
    listOf(square, lowRadius, asymmetric, rounded).forEach { value ->
      assertThat(value.profileReachPx).isEqualTo(100f)
      assertThat(value.resolve(.37f).refractionStrength).isEqualTo(.37f)
    }
    listOf(square, lowRadius, asymmetric, rounded).forEach { value ->
      assertThat(value.neutralLiftWeight.isFinite()).isEqualTo(true)
      assertThat(value.neutralLiftWeight).isLessThanOrEqualTo(.12f)
    }
  }

  @Test
  fun regularGeometryProfile_isScaleInvariantAndHeightMonotonic() {
    val base = calculateRegularGeometryProfile(
      Size(400f, 200f),
      CornerRadii(80f, 40f, 20f, 0f),
      24f,
      .5f,
    )
    val scaled = calculateRegularGeometryProfile(
      Size(800f, 400f),
      CornerRadii(160f, 80f, 40f, 0f),
      48f,
      .5f,
    )
    val zeroHeight = calculateRegularGeometryProfile(Size(400f, 200f), CornerRadii.zero, 24f, 0f)
    val fullHeight = calculateRegularGeometryProfile(Size(400f, 200f), CornerRadii.zero, 24f, 1f)
    val belowHeight = calculateRegularGeometryProfile(Size(400f, 200f), CornerRadii.zero, 24f, -1f)
    val aboveHeight = calculateRegularGeometryProfile(Size(400f, 200f), CornerRadii.zero, 24f, 2f)

    assertThat(abs(base.blurScale - scaled.blurScale)).isLessThan(.0001f)
    assertThat(abs(base.toneGain - scaled.toneGain)).isLessThan(.0001f)
    assertThat(base.profileReachPx).isEqualTo(100f)
    assertThat(scaled.profileReachPx).isEqualTo(200f)
    assertThat(zeroHeight.profileReachPx).isEqualTo(0f)
    assertThat(fullHeight.profileReachPx).isEqualTo(200f)
    assertThat(belowHeight.profileReachPx).isEqualTo(0f)
    assertThat(aboveHeight.profileReachPx).isEqualTo(200f)
  }

  @Test
  fun resolvedRegularGeometryOptics_preserveStrengthEndpointsAndMonotonicity() {
    val profile = calculateRegularGeometryProfile(
      Size(400f, 200f),
      CornerRadii(40f, 40f, 40f, 40f),
      24f,
      .5f,
    )
    val zero = profile.resolve(refractionStrength = 0f)
    val middle = profile.resolve(refractionStrength = .5f)
    val full = profile.resolve(refractionStrength = 1f)

    assertThat(zero.refractionStrength).isEqualTo(0f)
    assertThat(zero.blurScale).isEqualTo(1f)
    assertThat(zero.toneGain).isEqualTo(1f)
    assertThat(zero.neutralLiftWeight).isEqualTo(0f)
    assertThat(middle.refractionStrength).isEqualTo(.5f)
    assertThat(full.refractionStrength).isEqualTo(1f)
    assertThat(profile.resolve(-1f).refractionStrength).isEqualTo(0f)
    assertThat(profile.resolve(2f).refractionStrength).isEqualTo(1f)
    assertThat(full.neutralLiftWeight).isGreaterThan(middle.neutralLiftWeight)
  }

  private fun expectedLayerPadding(
    effect: GlassVisualEffect,
    rect: Rect,
    density: Density,
  ): Float {
    val effectiveBlurRadius = effectiveSemanticBlurRadiusPx(with(density) { effect.blurRadius.toPx() })
    val resolvedBlurScale = calculateRegularGeometryProfile(
      materialSize = rect.size,
      cornerRadii = effect.shape.toCornerRadiiPx(rect.size, density, LayoutDirection.Ltr),
      blurRadiusPx = effectiveBlurRadius,
      refractionHeight = effect.refractionHeight,
    ).resolve(effect.refractionStrength).blurScale
    return calculateGlassSamplePaddingPx(
      blurRadiusPx = effectiveBlurRadius * resolvedBlurScale,
      refractionScale = effect.refractionScale,
      refractionStrength = effect.refractionStrength,
      chromaticAberrationStrength = effect.chromaticAberrationStrength,
      edgeSoftnessPx = with(density) { effect.edgeSoftness.toPx() },
      foregroundOutsetPx = 0f,
    )
  }

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
