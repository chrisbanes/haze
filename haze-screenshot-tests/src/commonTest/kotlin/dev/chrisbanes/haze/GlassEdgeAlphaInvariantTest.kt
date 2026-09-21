// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Color
import assertk.assertFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import kotlin.test.Test

class GlassEdgeAlphaInvariantTest {

  @Test
  fun isolatedEdgeAlphaAssertion_rejectsDestroyedCandidateAlpha() {
    val translucent = Color(red = 0.12f, green = 0.24f, blue = 0.08f, alpha = 0.35f)

    assertFailure {
      assertIsolatedEdgeAlphaPreserved(
        reference = translucent,
        candidate = translucent.copy(alpha = 0f),
        transparentReference = Color.Transparent,
        transparentCandidate = Color.Transparent,
      )
    }.isInstanceOf<AssertionError>()
      .messageContains("edge alpha difference")
  }
}
