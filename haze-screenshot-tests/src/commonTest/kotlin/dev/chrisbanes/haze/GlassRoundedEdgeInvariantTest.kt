// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Color
import assertk.assertFailure
import assertk.assertions.isInstanceOf
import kotlin.test.Test

class GlassRoundedEdgeInvariantTest {

  @Test
  fun roundedEdgeContinuity_rejectsEffectClipBoundaryDiscontinuity() {
    val internalMaskOnly = PixelSnapshot(
      width = 2,
      height = 2,
      colors = List(4) { Color.Black },
    )
    val effectClip = internalMaskOnly.copy(
      colors = internalMaskOnly.colors.toMutableList().apply {
        this[0] = Color(0.5f, 0f, 0f)
      },
    )

    assertFailure {
      assertRoundedEdgeContinuity(
        internalMaskOnly = internalMaskOnly,
        effectClip = effectClip,
        contentClip = internalMaskOnly,
      )
    }.isInstanceOf<AssertionError>()
  }
}
