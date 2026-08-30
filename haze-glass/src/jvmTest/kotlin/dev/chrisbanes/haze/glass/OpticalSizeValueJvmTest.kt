// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test

class OpticalSizeValueJvmTest {
  @Test
  fun responsive_exposesUnmodifiablePointSnapshot() {
    val expected = listOf(
      OpticalSizePoint(64.dp, 1f),
      OpticalSizePoint(176.dp, 2f),
    )
    val value = OpticalSizeValue.Responsive(expected)

    assertFailure {
      @Suppress("UNCHECKED_CAST")
      (value.points as MutableList<OpticalSizePoint<Float>>).clear()
    }.isInstanceOf<UnsupportedOperationException>()
    assertThat(value.points).isEqualTo(expected)
  }
}
