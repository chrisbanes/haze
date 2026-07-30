// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test

class GlassOpticsTest {

  @Test
  fun adaptive_isTheDefaultOptics() {
    assertThat(GlassDefaults.optics).isEqualTo(GlassOptics.Adaptive)
    assertThat(resolveGlassStyleValues(GlassStyle, GlassDefaults.style).optics)
      .isEqualTo(GlassOptics.Adaptive)
  }

  @Test
  fun absolute_rejectsInvalidSemanticValues() {
    listOf<() -> Unit>(
      { GlassOptics.Absolute(refractionStrength = Float.NaN) },
      { GlassOptics.Absolute(refractionStrength = -0.1f) },
      { GlassOptics.Absolute(refractionHeightFraction = 1.1f) },
      { GlassOptics.Absolute(refractionDisplacement = Dp.Unspecified) },
      { GlassOptics.Absolute(refractionDisplacement = Float.POSITIVE_INFINITY.dp) },
      { GlassOptics.Absolute(refractionDisplacement = (-1).dp) },
      { GlassOptics.Absolute(depth = Float.NaN) },
      { GlassOptics.Absolute(blurRadius = Dp.Unspecified) },
      { GlassOptics.Absolute(blurRadius = (-1).dp) },
    ).forEach { create ->
      assertFailure { create() }.isInstanceOf<IllegalArgumentException>()
    }
  }

  @Test
  fun absolute_acceptsFiniteDisplacementBeyondRendererLimit() {
    val displacement = Float.MAX_VALUE.dp

    assertThat(GlassOptics.Absolute(refractionDisplacement = displacement).refractionDisplacement)
      .isEqualTo(displacement)
  }
}
