// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertFailure
import assertk.assertions.messageContains
import kotlin.test.Test

class GlassOutputCoverageInvariantTest {
  @Test
  fun qualityBoundaryOracle_acceptsFixedQuantizationBudget() {
    assertGlassOutputCoverageMatchesQualityBoundary(
      candidate = REFERENCE.withAlphaDelta(index = 1, delta = 2),
      reference = REFERENCE,
      expectedInteriorAlpha = 128,
      interiorIndex = 4,
    )
  }

  @Test
  fun qualityBoundaryOracle_rejectsEmptyCandidate() {
    assertFailure {
      assertGlassOutputCoverageMatchesQualityBoundary(
        candidate = AlphaSnapshot(3, 3, ByteArray(9)),
        reference = REFERENCE,
        expectedInteriorAlpha = 128,
        interiorIndex = 4,
      )
    }.messageContains("nonempty support")
  }

  @Test
  fun qualityBoundaryOracle_rejectsShiftedCandidate() {
    assertFailure {
      assertGlassOutputCoverageMatchesQualityBoundary(
        candidate = REFERENCE.shiftedRight(),
        reference = REFERENCE,
        expectedInteriorAlpha = 128,
        interiorIndex = 4,
      )
    }.messageContains("candidate interior alpha")
  }

  @Test
  fun qualityBoundaryOracle_rejectsCoarseUpscaledCandidate() {
    assertFailure {
      assertGlassOutputCoverageMatchesQualityBoundary(
        candidate = AlphaSnapshot(
          width = 3,
          height = 3,
          alpha = byteArrayOf(
            0, 32, 32,
            0, 128.toByte(), 128.toByte(),
            0, 128.toByte(), 128.toByte(),
          ),
        ),
        reference = REFERENCE,
        expectedInteriorAlpha = 128,
        interiorIndex = 4,
      )
    }.messageContains("maximum alpha difference")
  }

  @Test
  fun qualityBoundaryOracle_rejectsDoubleMaskedCandidate() {
    assertFailure {
      assertGlassOutputCoverageMatchesQualityBoundary(
        candidate = AlphaSnapshot(
          width = 3,
          height = 3,
          alpha = REFERENCE.alpha.map { value ->
            val unsigned = value.toInt() and 0xff
            ((unsigned * unsigned) / 128).coerceAtMost(255).toByte()
          }.toByteArray(),
        ),
        reference = REFERENCE,
        expectedInteriorAlpha = 128,
        interiorIndex = 4,
      )
    }.messageContains("maximum alpha difference")
  }

  @Test
  fun qualityBoundaryOracle_rejectsTwoCodeValuesOutsideSupport() {
    assertFailure {
      assertGlassOutputCoverageMatchesQualityBoundary(
        candidate = REFERENCE.withAlphaDelta(index = 0, delta = 2),
        reference = REFERENCE,
        expectedInteriorAlpha = 128,
        interiorIndex = 4,
      )
    }.messageContains("candidate alpha outside reference support")
  }
}

private val REFERENCE = AlphaSnapshot(
  width = 3,
  height = 3,
  alpha = byteArrayOf(
    0, 32, 0,
    32, 128.toByte(), 32,
    0, 32, 0,
  ),
)

private fun AlphaSnapshot.withAlphaDelta(index: Int, delta: Int): AlphaSnapshot = copy(
  alpha = alpha.copyOf().also { values ->
    values[index] = ((values[index].toInt() and 0xff) + delta).coerceIn(0, 255).toByte()
  },
)

private fun AlphaSnapshot.shiftedRight(): AlphaSnapshot = copy(
  alpha = ByteArray(alpha.size) { index ->
    val x = index % width
    if (x == 0) 0 else alpha[index - 1]
  },
)
