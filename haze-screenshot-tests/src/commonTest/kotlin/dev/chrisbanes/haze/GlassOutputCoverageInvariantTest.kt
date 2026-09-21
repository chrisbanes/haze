// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Color
import assertk.assertFailure
import assertk.assertions.isInstanceOf
import kotlin.test.Test

class GlassOutputCoverageInvariantTest {
  @Test
  fun outputCoverageOracle_rejectsMissingCandidateCoverage() {
    val control = PixelSnapshot(
      width = 3,
      height = 3,
      colors = List(9) { Color.White.copy(alpha = 0.5f) },
    )
    val missingCoverage = PixelSnapshot(
      width = 3,
      height = 3,
      colors = List(9) { Color.Transparent },
    )

    assertFailure {
      assertGlassOutputCoverageMatchesControls(
        performanceMode = HazePerformanceMode.Balanced,
        candidate = missingCoverage,
        quality = control,
        composeClip = control,
        qualityControlTolerance = 0f,
      )
    }.isInstanceOf<AssertionError>()
  }
}
