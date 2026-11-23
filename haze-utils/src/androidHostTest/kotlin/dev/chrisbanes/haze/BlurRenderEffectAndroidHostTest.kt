// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.TileMode
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BlurRenderEffectAndroidHostTest {

  @Test
  fun createBlurRenderEffect_rejectsNonFiniteRadii() {
    val nonFinitePairs = listOf(
      Float.NaN to 0f,
      0f to Float.NaN,
      Float.NaN to 8f,
      8f to Float.NaN,
      Float.NaN to Float.NaN,
      Float.POSITIVE_INFINITY to 0f,
      0f to Float.POSITIVE_INFINITY,
      Float.POSITIVE_INFINITY to 8f,
      8f to Float.POSITIVE_INFINITY,
      Float.POSITIVE_INFINITY to Float.POSITIVE_INFINITY,
      Float.NEGATIVE_INFINITY to 0f,
      0f to Float.NEGATIVE_INFINITY,
      Float.NEGATIVE_INFINITY to 8f,
      8f to Float.NEGATIVE_INFINITY,
      Float.NEGATIVE_INFINITY to Float.NEGATIVE_INFINITY,
    )

    for ((radiusX, radiusY) in nonFinitePairs) {
      assertFailure {
        createBlurRenderEffect(radiusX, radiusY, TileMode.Clamp)
      }.isInstanceOf<IllegalArgumentException>()
    }
  }

  @Test
  fun createBlurRenderEffect_returnsNullWhenBothRadiiAreNonPositive() {
    val noOpPairs = listOf(
      0f to 0f,
      -1f to 0f,
      0f to -1f,
      -4f to -4f,
    )

    for ((radiusX, radiusY) in noOpPairs) {
      assertThat(createBlurRenderEffect(radiusX, radiusY, TileMode.Clamp)).isNull()
    }
  }
}
