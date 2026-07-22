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
  fun verticalScanlineDerivative_returnsAdjacentLuminanceDifferences() {
    val gray25 = Color(0.25f, 0.25f, 0.25f)
    val gray75 = Color(0.75f, 0.75f, 0.75f)
    val snapshot = PixelSnapshot(
      width = 2,
      height = 4,
      colors = listOf(
        Color.Black,
        Color.Black,
        Color.Black,
        gray25,
        Color.Black,
        gray75,
        Color.Black,
        Color.White,
      ),
    )

    assertThat(snapshot.verticalScanlineDerivative(x = 1, yRange = 0..3))
      .isEqualTo(
        listOf(
          gray25.luminance(),
          gray75.luminance() - gray25.luminance(),
          Color.White.luminance() - gray75.luminance(),
        ),
      )
  }

  @Test
  fun verticalScanlineDerivative_rejectsInvalidCoordinatesAndShortRanges() {
    val snapshot = snapshot(width = 2, height = 3)

    assertThat(
      assertFailsWith<IllegalArgumentException> {
        snapshot.verticalScanlineDerivative(x = 2, yRange = 0..2)
      }.message.orEmpty(),
    ).contains("x")
    assertThat(
      assertFailsWith<IllegalArgumentException> {
        snapshot.verticalScanlineDerivative(x = 0, yRange = 1..1)
      }.message.orEmpty(),
    ).contains("at least 2")
    assertThat(
      assertFailsWith<IllegalArgumentException> {
        snapshot.verticalScanlineDerivative(x = 0, yRange = 1..3)
      }.message.orEmpty(),
    ).contains("yRange")
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
  fun boundaryCurvatureContinuity_rejectsClusteredSpike() {
    val derivative = List(17) { index ->
      if (index in 7..9) 0.25f else 0.01f
    }

    assertFailsWith<AssertionError> {
      assertBoundaryCurvatureContinuous(
        derivative = derivative,
        boundaryIndex = 8,
        boundaryRadius = 1,
      )
    }
  }

  @Test
  fun boundaryCurvatureContinuity_acceptsSmoothWideTransition() {
    val derivative = listOf(
      0f,
      0f,
      0.0039f,
      0f,
      0.0039f,
      0.004f,
      0f,
      0f,
      0f,
      0f,
      0f,
      0.004f,
      0f,
      0f,
      0.0119f,
      0.0228f,
      0.018f,
      0.0171f,
      0.0098f,
      0.0032f,
      0f,
      0f,
      0f,
      0f,
      0f,
      0.0033f,
      0f,
      0f,
      0f,
      0.0033f,
      0f,
      0f,
    )

    assertBoundaryCurvatureContinuous(
      derivative = derivative,
      boundaryIndex = 16,
      boundaryRadius = 1,
    )
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

private data class DisplacementFixture(
  val grid: PixelSnapshot,
  val uniform: PixelSnapshot,
)

private fun signedDisplacementFixture(
  shift: Int,
  quantize: Boolean = false,
): DisplacementFixture {
  fun encode(value: Float): Float {
    return if (quantize) (value * 255f).toInt() / 255f else value
  }
  val background = encode(.4f)
  val feature = encode(.8f)
  val uniform = PixelSnapshot(41, 2, List(82) { Color(background, background, background) })
  val featurePositions = setOf(20 + shift)
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
