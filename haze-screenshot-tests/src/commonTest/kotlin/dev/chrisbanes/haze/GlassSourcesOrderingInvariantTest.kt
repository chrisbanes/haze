// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Color
import assertk.assertFailure
import assertk.assertions.messageContains
import kotlin.test.Test

class GlassSourcesOrderingInvariantTest {
  @Test
  fun overlapOracle_rejectsForegroundOnlyOutput() {
    val callerOnly = snapshot(Color(0.8f, 0f, 0f, 1f))
    val foregroundOnly = snapshot(Color(0.1f, 0.6f, 0.1f, 1f))

    assertFailure {
      assertSourcesOverlap(
        measureSourcesOverlap(
          ordered = foregroundOnly,
          callerOnly = callerOnly,
          foregroundOnly = foregroundOnly,
        ),
      )
    }.messageContains("caller contributes red")
  }

  @Test
  fun overlapOracle_rejectsCallerOnlyOutput() {
    val callerOnly = snapshot(Color(0.8f, 0f, 0f, 1f))
    val foregroundOnly = snapshot(Color(0.1f, 0.6f, 0.1f, 1f))

    assertFailure {
      assertSourcesOverlap(
        measureSourcesOverlap(
          ordered = callerOnly,
          callerOnly = callerOnly,
          foregroundOnly = foregroundOnly,
        ),
      )
    }.messageContains("foreground contributes green")
  }

  private fun snapshot(color: Color) = PixelSnapshot(1, 1, listOf(color))
}
