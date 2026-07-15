// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GlassImageMetricsTest {

  @Test
  fun pairedGridDisplacement_noDisplacementCarrierReturnsZero() {
    val fixture = signedDisplacementFixture(shift = 0, quantize = true)

    assertThat(
      pairedCarrierDisplacementDeltaPx(
        disabledCarrier = fixture.grid,
        disabledUniform = fixture.uniform,
        enabledCarrier = fixture.grid,
        enabledUniform = fixture.uniform,
        scanY = 1,
        range = 4..36,
        expected = 20f,
      ),
    ).isEqualTo(0f)
  }

  @Test
  fun pairedCarrierDisplacementDelta_recoversEnabledMinusDisabledShift() {
    val disabled = signedDisplacementFixture(shift = 0, quantize = true)
    val enabled = signedDisplacementFixture(shift = -5, quantize = true)

    assertThat(
      pairedCarrierDisplacementDeltaPx(
        disabledCarrier = disabled.grid,
        disabledUniform = disabled.uniform,
        enabledCarrier = enabled.grid,
        enabledUniform = enabled.uniform,
        scanY = 1,
        range = 4..36,
        expected = 20f,
      ),
    ).isEqualTo(-5f)
  }

  @Test
  fun pairedCarrierDisplacementDelta_recoversPositiveShift() {
    val disabled = signedDisplacementFixture(shift = 0, quantize = true)
    val enabled = signedDisplacementFixture(shift = 5, quantize = true)

    assertThat(
      pairedCarrierDisplacementDeltaPx(
        disabledCarrier = disabled.grid,
        disabledUniform = disabled.uniform,
        enabledCarrier = enabled.grid,
        enabledUniform = enabled.uniform,
        scanY = 1,
        range = 4..36,
        expected = 20f,
      ),
    ).isEqualTo(5f)
  }

  @Test
  fun pairedCarrierDisplacementDelta_sameCenterDifferentBlurAndContrastReturnsZero() {
    val uniform = PixelSnapshot(41, 1, List(41) { Color(.1f, .1f, .1f) })
    val disabled = PixelSnapshot(
      41,
      1,
      List(41) { x -> if (x == 19 || x == 21) Color(.8f, .8f, .8f) else Color(.1f, .1f, .1f) },
    )
    val enabled = PixelSnapshot(
      41,
      1,
      List(41) { x -> if (x == 20) Color(.9f, .9f, .9f) else Color(.1f, .1f, .1f) },
    )

    assertThat(
      pairedCarrierDisplacementDeltaPx(
        disabledCarrier = disabled,
        disabledUniform = uniform,
        enabledCarrier = enabled,
        enabledUniform = uniform,
        scanY = 0,
        range = 4..36,
        expected = 20f,
      ),
    ).isEqualTo(0f)
  }

  @Test
  fun residualChangedPixelRatio_cancelsGlobalUniformToneShift() {
    val disabledUniform = PixelSnapshot(6, 2, List(12) { Color(.2f, .2f, .2f) })
    val disabledGrid = PixelSnapshot(
      6,
      2,
      List(12) { index -> if (index == 1 || index == 10) Color(.6f, .6f, .6f) else Color(.2f, .2f, .2f) },
    )
    val enabledUniform = PixelSnapshot(6, 2, List(12) { Color(.3f, .3f, .3f) })
    val enabledGrid = PixelSnapshot(
      6,
      2,
      List(12) { index -> if (index == 1 || index == 10) Color(.7f, .7f, .7f) else Color(.3f, .3f, .3f) },
    )

    assertThat(
      residualChangedPixelRatio(
        disabledGrid,
        disabledUniform,
        enabledGrid,
        enabledUniform,
        IntRect(0, 0, 6, 2),
      ),
    ).isEqualTo(0f)
  }

  @Test
  fun residualChangedPixelRatio_detectsShiftedGridFeatureInEdgeBand() {
    val uniform = PixelSnapshot(6, 2, List(12) { Color.Black })
    val disabledGrid = PixelSnapshot(
      6,
      2,
      List(12) { index -> if (index == 1) Color.White else Color.Black },
    )
    val enabledGrid = PixelSnapshot(
      6,
      2,
      List(12) { index -> if (index == 2) Color.White else Color.Black },
    )

    assertThat(
      residualChangedPixelRatio(
        disabledGrid,
        uniform,
        enabledGrid,
        uniform,
        IntRect(0, 0, 3, 2),
      ),
    ).isEqualTo(1f / 3f)
  }

  @Test
  fun pairedGridDisplacement_treatsSubCodeMeanDifferenceAsZero() {
    val uniform = PixelSnapshot(9, 2, List(18) { Color(.1f, .1f, .1f) })
    val grid = PixelSnapshot(
      9,
      2,
      List(18) { index ->
        val x = index % 9
        val y = index / 9
        when {
          y == 0 && x == 4 -> Color(.8f, .8f, .8f)
          y == 1 -> Color(.104f, .104f, .104f)
          else -> Color(.1f, .1f, .1f)
        }
      },
    )

    assertThat(
      pairedGridDisplacementPx(grid, uniform, y = 1, range = 0..8, expected = 4f, referenceY = 0),
    )
      .isEqualTo(0f)
  }

  @Test
  fun pairedGridDisplacement_recoversSignedQuantizedShifts() {
    listOf(-5f, -2f, 3f, 6f).forEach { shift ->
      val fixture = signedDisplacementFixture(shift.toInt(), gamma = 2.2f, quantize = true)
      assertThat(
        abs(
          pairedGridDisplacementPx(
            fixture.grid,
            fixture.uniform,
            y = 1,
            range = 4..36,
            expected = 20f,
            referenceY = 0,
          ) - shift,
        ),
      ).isLessThanOrEqualTo(.01f)
    }
  }

  @Test
  fun signedDisplacementBand_rejectsOppositeDirection() {
    val key = GlassReferenceKey(GlassAppearance.Light, GlassSurface.Capsule)
    val expectedDirection = Ios26RegularReferenceBands.getValue(key).displacementPx

    assertThat(expectedDirection.contains(Ios26RegularReferenceMetrics.getValue(key).displacementPx))
      .isEqualTo(true)
    assertThat(expectedDirection.contains(0f)).isEqualTo(false)
    assertThat(expectedDirection.contains(1.6625061f)).isEqualTo(false)
  }

  @Test
  fun pairedGridDisplacement_rejectsExcessiveMissingAndAmbiguousFeatures() {
    val excessive = signedDisplacementFixture(18)
    val missing = PixelSnapshot(41, 2, List(82) { Color(.4f, .4f, .4f) })
    val ambiguous = signedDisplacementFixture(-6, secondShift = 6)

    listOf(excessive.grid to excessive.uniform, missing to missing, ambiguous.grid to ambiguous.uniform)
      .forEach { (grid, uniform) ->
        assertFailsWith<IllegalStateException> {
          pairedGridDisplacementPx(grid, uniform, y = 1, range = 4..36, expected = 20f, referenceY = 0)
        }
      }
  }

  @Test
  fun pairedGridDisplacement_rejectsDiffuseWeakResidual() {
    val source = signedDisplacementFixture(0)
    val diffuseGrid = PixelSnapshot(
      41,
      2,
      List(82) { index ->
        val x = index % 41
        val y = index / 41
        when {
          y == 0 && x == 20 -> Color(.8f, .8f, .8f)
          y == 1 -> Color(.42f, .42f, .42f)
          else -> Color(.4f, .4f, .4f)
        }
      },
    )

    assertFailsWith<IllegalStateException> {
      pairedGridDisplacementPx(diffuseGrid, source.uniform, y = 1, range = 4..36, expected = 20f, referenceY = 0)
    }
  }

  @Test
  fun horizontalCorrelation_recoversShiftAndAlignmentRejectsIt() {
    val reference = correlationSnapshot(shift = 0)
    val shifted = correlationSnapshot(shift = 3)

    assertThat(
      signedHorizontalCorrelationDisplacementPx(reference, shifted, y = 0, range = 8..55, maxShiftPx = 6),
    ).isEqualTo(3f)
    assertFailsWith<AssertionError> {
      assertContentAlignedAcrossInputScales(reference, shifted, y = 0, range = 8..55)
    }
    assertContentAlignedAcrossInputScales(reference, reference, y = 0, range = 8..55)
  }

  @Test
  fun opticalMetrics_ignoreContaminationOutsideMiddleQuarter() {
    val clean = opticalMetricFixture(contaminateOuterInterior = false)
    val contaminated = opticalMetricFixture(contaminateOuterInterior = true)

    val cleanMetrics = measureGlassOpticalMetrics(clean.grid, clean.uniform, clean.surface, clean.background, 8)
    val contaminatedMetrics = measureGlassOpticalMetrics(
      contaminated.grid,
      contaminated.uniform,
      contaminated.surface,
      contaminated.background,
      8,
    )

    assertThat(contaminatedMetrics.blurAttenuation).isEqualTo(cleanMetrics.blurAttenuation)
    assertThat(contaminatedMetrics.interiorLumaShift).isEqualTo(cleanMetrics.interiorLumaShift)
  }

  @Test
  fun opticalMetrics_recoverKnownDisplacementBlurAndPositiveLuma() {
    val fixture = knownOpticalMetricFixture(interiorUniform = .3f)

    val metrics = measureGlassOpticalMetrics(
      fixture.grid,
      fixture.uniform,
      fixture.surface,
      fixture.background,
      8,
    )

    assertThat(abs(metrics.displacementPx - 2f)).isLessThanOrEqualTo(.02f)
    assertThat(abs(metrics.blurAttenuation - Color(.5f, .5f, .5f).luminance()))
      .isLessThanOrEqualTo(1e-6f)
    assertThat(
      abs(
        metrics.interiorLumaShift -
          (Color(.3f, .3f, .3f).luminance() - Color(.2f, .2f, .2f).luminance()),
      ),
    ).isLessThanOrEqualTo(1e-6f)
  }

  @Test
  fun opticalMetrics_recoverNegativeInteriorLumaShift() {
    val fixture = knownOpticalMetricFixture(interiorUniform = .1f)

    val metrics = measureGlassOpticalMetrics(
      fixture.grid,
      fixture.uniform,
      fixture.surface,
      fixture.background,
      8,
    )

    assertThat(
      abs(
        metrics.interiorLumaShift -
          (Color(.1f, .1f, .1f).luminance() - Color(.2f, .2f, .2f).luminance()),
      ),
    ).isLessThanOrEqualTo(1e-6f)
  }

  @Test
  fun opticalMetrics_rejectMismatchedDimensionsAndInvalidBounds() {
    val fixture = knownOpticalMetricFixture(interiorUniform = .3f)
    val wrongSize = PixelSnapshot(1, 1, listOf(Color.Black))

    assertFailsWith<IllegalArgumentException> {
      measureGlassOpticalMetrics(
        fixture.grid,
        wrongSize,
        fixture.surface,
        fixture.background,
        8,
      )
    }
    assertFailsWith<IllegalArgumentException> {
      measureGlassOpticalMetrics(
        fixture.grid,
        fixture.uniform,
        IntRect(16, 0, 97, 32),
        fixture.background,
        8,
      )
    }
  }

  @Test
  fun pixelSnapshot_rejectsMismatchedStorage() {
    val failure = assertFailsWith<IllegalArgumentException> {
      PixelSnapshot(width = 2, height = 2, colors = listOf(Color.Black))
    }

    assertThat(failure.message.orEmpty()).contains("4 colors")
  }

  @Test
  fun comparisons_rejectEmptySnapshots() {
    val empty = PixelSnapshot(width = 0, height = 0, colors = emptyList())

    assertThat(
      assertFailsWith<IllegalArgumentException> { empty.changedPixelRatio(empty) }
        .message.orEmpty(),
    ).contains("non-empty")
    assertThat(
      assertFailsWith<IllegalArgumentException> { empty.meanAbsoluteDifference(empty) }
        .message.orEmpty(),
    ).contains("non-empty")
  }

  @Test
  fun scanlineDerivative_rejectsInvalidCoordinatesAndShortRanges() {
    val snapshot = snapshot(width = 3, height = 2)

    assertThat(
      assertFailsWith<IllegalArgumentException> {
        snapshot.scanlineDerivative(y = 2, xRange = 0..2)
      }.message.orEmpty(),
    ).contains("y")
    assertThat(
      assertFailsWith<IllegalArgumentException> {
        snapshot.scanlineDerivative(y = 0, xRange = 1..1)
      }.message.orEmpty(),
    ).contains("at least 2")
    assertThat(
      assertFailsWith<IllegalArgumentException> {
        snapshot.scanlineDerivative(y = 0, xRange = 1..3)
      }.message.orEmpty(),
    ).contains("xRange")
  }

  @Test
  fun alphaEdgePosition_ignoresStrongerContentEdges() {
    val snapshot = PixelSnapshot(
      width = 5,
      height = 1,
      colors = listOf(
        Color.Transparent,
        Color.Transparent,
        Color.Black,
        Color.White,
        Color.Black,
      ),
    )

    assertThat(snapshot.horizontalEdgePosition(y = 0, xRange = 0..4)).isEqualTo(2f)
    assertThat(snapshot.horizontalAlphaEdgePosition(y = 0, xRange = 0..4)).isEqualTo(1f)
  }

  @Test
  fun alphaCoverage_andSpanRejectMissingOutput() {
    val transparent = PixelSnapshot(
      width = 4,
      height = 1,
      colors = List(4) { Color.Transparent },
    )

    assertThat(transparent.alphaCoverage(IntRect(0, 0, 4, 1))).isEqualTo(0f)
    assertThat(transparent.horizontalAlphaSpan(y = 0)).isEqualTo(null)
  }

  @Test
  fun alphaCoverage_andSpanMeasureVisibleOutput() {
    val snapshot = PixelSnapshot(
      width = 4,
      height = 1,
      colors = listOf(Color.Transparent, Color.Black, Color.White, Color.Transparent),
    )

    assertThat(snapshot.alphaCoverage(IntRect(0, 0, 4, 1))).isEqualTo(0.5f)
    assertThat(snapshot.horizontalAlphaSpan(y = 0)).isEqualTo(1..2)
  }

  @Test
  fun highFrequencyEnergy_rejectsSmallAndOutOfBoundsRegions() {
    val snapshot = snapshot(width = 3, height = 3)

    assertThat(
      assertFailsWith<IllegalArgumentException> {
        snapshot.highFrequencyEnergy(IntRect(0, 0, 1, 2))
      }.message.orEmpty(),
    ).contains("at least 2x2")
    assertThat(
      assertFailsWith<IllegalArgumentException> {
        snapshot.highFrequencyEnergy(IntRect(0, 0, 4, 3))
      }.message.orEmpty(),
    ).contains("bounds")
  }

  @Test
  fun crop_rejectsEmptyAndOutOfBoundsRegions() {
    val snapshot = snapshot(width = 3, height = 3)

    assertThat(
      assertFailsWith<IllegalArgumentException> {
        snapshot.crop(IntRect(1, 1, 1, 2))
      }.message.orEmpty(),
    ).contains("non-empty")
    assertThat(
      assertFailsWith<IllegalArgumentException> {
        snapshot.crop(IntRect(-1, 0, 2, 2))
      }.message.orEmpty(),
    ).contains("bounds")
  }

  @Test
  fun transparentPoints_requireTransparentAlphaAndRgb() {
    val opaque = PixelSnapshot(width = 1, height = 1, colors = listOf(Color.Black))

    assertFailsWith<AssertionError> {
      opaque.assertTransparentAt(listOf(IntOffset.Zero))
    }

    PixelSnapshot(width = 1, height = 1, colors = listOf(Color.Transparent))
      .assertTransparentAt(listOf(IntOffset.Zero))
  }

  @Test
  fun blackAndWhiteMattes_recoverPremultipliedColorAndAlpha() {
    val overBlack = PixelSnapshot(
      width = 1,
      height = 1,
      colors = listOf(Color(red = 0.2f, green = 0.1f, blue = 0.05f, alpha = 1f)),
    )
    val overWhite = PixelSnapshot(
      width = 1,
      height = 1,
      colors = listOf(Color(red = 0.7f, green = 0.6f, blue = 0.55f, alpha = 1f)),
    )

    val recovered = recoverPremultipliedSnapshot(overBlack, overWhite)[0, 0]

    assertThat(recovered).isEqualTo(
      Color(red = 0.2f, green = 0.1f, blue = 0.05f, alpha = 0.5f),
    )
  }

  @Test
  fun blackAndWhiteMattes_rejectInvalidCapturePairs() {
    val opaqueBlack = PixelSnapshot(1, 1, listOf(Color.Black))
    val translucentWhite = PixelSnapshot(1, 1, listOf(Color.White.copy(alpha = 0.5f)))
    val darkerWhite = PixelSnapshot(1, 1, listOf(Color(red = 0.1f, green = 0.1f, blue = 0.1f)))
    val brighterBlack = PixelSnapshot(1, 1, listOf(Color(red = 0.2f, green = 0.2f, blue = 0.2f)))
    val inconsistentWhite = PixelSnapshot(
      1,
      1,
      listOf(Color(red = 0.5f, green = 0.6f, blue = 0.5f)),
    )

    assertThat(
      assertFailsWith<IllegalArgumentException> {
        recoverPremultipliedSnapshot(opaqueBlack, translucentWhite)
      }.message.orEmpty(),
    ).contains("opaque")
    assertThat(
      assertFailsWith<IllegalArgumentException> {
        recoverPremultipliedSnapshot(brighterBlack, darkerWhite)
      }.message.orEmpty(),
    ).contains("darker")
    assertThat(
      assertFailsWith<IllegalArgumentException> {
        recoverPremultipliedSnapshot(opaqueBlack, inconsistentWhite)
      }.message.orEmpty(),
    ).contains("agree")
  }

  @Test
  fun depthProgression_acceptsNonlinearStatesAndRejectsCollapsedMidpoint() {
    val depth0 = PixelSnapshot(1, 1, listOf(Color.Black))
    val depth50 = PixelSnapshot(1, 1, listOf(Color.White))
    val depth100 = PixelSnapshot(1, 1, listOf(Color.Gray))

    assertDepthProgression(depth0, depth50, depth100)
    assertFailsWith<AssertionError> {
      assertDepthProgression(depth0, depth0, depth100)
    }
  }

  @Test
  fun boundaryContinuity_rejectsEmptyDerivativeAndInvalidIndex() {
    assertThat(
      assertFailsWith<IllegalArgumentException> {
        assertBoundaryContinuous(emptyList(), boundaryIndex = 0)
      }.message.orEmpty(),
    ).contains("non-empty")
    assertThat(
      assertFailsWith<IllegalArgumentException> {
        assertBoundaryContinuous(listOf(0f), boundaryIndex = 1)
      }.message.orEmpty(),
    ).contains("boundaryIndex")
  }

  @Test
  fun outsideBackgroundComparison_rejectsInvalidInputs() {
    val onePixel = snapshot(width = 1, height = 1)
    val twoPixels = snapshot(width = 2, height = 1)

    assertThat(
      assertFailsWith<IllegalArgumentException> {
        assertOutsideMatchesBackground(onePixel, onePixel, emptyList())
      }.message.orEmpty(),
    ).contains("non-empty")
    assertThat(
      assertFailsWith<IllegalArgumentException> {
        assertOutsideMatchesBackground(onePixel, twoPixels, listOf(IntOffset.Zero))
      }.message.orEmpty(),
    ).contains("dimensions")
    assertThat(
      assertFailsWith<IllegalArgumentException> {
        assertOutsideMatchesBackground(onePixel, onePixel, listOf(IntOffset(1, 0)))
      }.message.orEmpty(),
    ).contains("within")
  }
}

private fun snapshot(width: Int, height: Int): PixelSnapshot = PixelSnapshot(
  width = width,
  height = height,
  colors = List(width * height) { Color.Black },
)

private data class OpticalMetricFixture(
  val grid: PixelSnapshot,
  val uniform: PixelSnapshot,
  val surface: IntRect,
  val background: IntRect,
)

private fun opticalMetricFixture(contaminateOuterInterior: Boolean): OpticalMetricFixture {
  val width = 96
  val height = 64
  val surface = IntRect(24, 8, 88, 56)
  val background = IntRect(0, 0, 16, 64)
  val middle = IntRect(48, 26, 64, 38)
  val grid = ArrayList<Color>(width * height)
  val uniform = ArrayList<Color>(width * height)
  for (y in 0 until height) {
    for (x in 0 until width) {
      val line = x % 8 == 0 || y % 8 == 0
      val inSurface = x in surface.left until surface.right && y in surface.top until surface.bottom
      val inMiddle = x in middle.left until middle.right && y in middle.top until middle.bottom
      val contamination = contaminateOuterInterior && inSurface && !inMiddle && (x + y) % 2 == 0
      val uniformValue = if (inSurface) .45f else .4f
      val gridValue = when {
        y == 32 && x == 32 -> 1f
        y == 32 && x in 28..36 -> uniformValue
        contamination -> .95f
        inMiddle && line -> .5f
        line -> .8f
        else -> uniformValue
      }
      uniform += Color(uniformValue, uniformValue, uniformValue)
      grid += Color(gridValue, gridValue, gridValue)
    }
  }
  return OpticalMetricFixture(
    PixelSnapshot(width, height, grid),
    PixelSnapshot(width, height, uniform),
    surface,
    background,
  )
}

private fun knownOpticalMetricFixture(interiorUniform: Float): OpticalMetricFixture {
  val width = 64
  val height = 32
  val surface = IntRect(16, 0, 64, 32)
  val background = IntRect(0, 0, 12, 8)
  val middle = IntRect(34, 12, 46, 20)
  val grid = ArrayList<Color>(width * height)
  val uniform = ArrayList<Color>(width * height)
  for (y in 0 until height) {
    for (x in 0 until width) {
      val inMiddle = x in middle.left until middle.right && y in middle.top until middle.bottom
      val uniformValue = if (inMiddle) interiorUniform else .2f
      val gridColor = when {
        x == 24 && y == 4 -> Color.White
        x == 26 && y == 16 -> Color.White
        inMiddle && (x + y) % 2 == 0 -> Color(.5f, .5f, .5f)
        inMiddle -> Color.Black
        x in background.left until background.right &&
          y in background.top until background.bottom &&
          (x + y) % 2 == 0 -> Color.White
        x in background.left until background.right &&
          y in background.top until background.bottom -> Color.Black
        else -> Color(.2f, .2f, .2f)
      }
      uniform += Color(uniformValue, uniformValue, uniformValue)
      grid += gridColor
    }
  }
  return OpticalMetricFixture(
    PixelSnapshot(width, height, grid),
    PixelSnapshot(width, height, uniform),
    surface,
    background,
  )
}

private data class DisplacementFixture(
  val grid: PixelSnapshot,
  val uniform: PixelSnapshot,
)

private fun signedDisplacementFixture(
  shift: Int,
  secondShift: Int? = null,
  gamma: Float = 1f,
  quantize: Boolean = false,
): DisplacementFixture {
  fun encode(value: Float): Float {
    val encoded = value.coerceIn(0f, 1f).pow(1f / gamma)
    return if (quantize) (encoded * 255f).toInt() / 255f else encoded
  }
  val background = encode(.4f)
  val feature = encode(.8f)
  val uniform = PixelSnapshot(41, 2, List(82) { Color(background, background, background) })
  val featurePositions = listOfNotNull(20 + shift, secondShift?.let { 20 + it }).toSet()
  val grid = PixelSnapshot(
    41,
    2,
    List(82) { index ->
      val x = index % 41
      val y = index / 41
      val value = when {
        y == 0 && x == 20 -> feature
        y == 1 && x in featurePositions -> feature
        else -> background
      }
      Color(value, value, value)
    },
  )
  return DisplacementFixture(grid, uniform)
}

private fun correlationSnapshot(shift: Int): PixelSnapshot {
  val features = mapOf(14 to .2f, 21 to .9f, 33 to .45f, 47 to .75f)
  return PixelSnapshot(
    64,
    1,
    List(64) { x ->
      val value = features[x - shift] ?: .05f
      Color(value, value, value)
    },
  )
}
