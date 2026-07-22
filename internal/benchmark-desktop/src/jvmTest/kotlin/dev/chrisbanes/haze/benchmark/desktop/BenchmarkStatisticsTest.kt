// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import kotlin.math.abs
import kotlin.test.Test

class BenchmarkStatisticsTest {
  @Test
  fun nearestRankPercentiles_areStable() {
    val values = listOf(10L, 20L, 30L, 40L, 50L)
    assertThat(nearestRank(values, 0.50)).isEqualTo(30L)
    assertThat(nearestRank(values, 0.95)).isEqualTo(50L)
  }

  @Test
  fun variationAboveTenPercent_isNoisy() {
    assertThat(robustRelativeVariationPercent(listOf(10.0, 10.0, 14.0, 14.0))).isGreaterThan(10.0)
    assertThat(isNoisy(listOf(10.0, 10.0, 14.0, 14.0))).isTrue()
  }

  @Test
  fun pairedDelta_usesThreeAbbaRounds() {
    val base = listOf(10.0, 10.0, 10.0, 10.0, 10.0, 10.0)
    val head = listOf(11.0, 11.0, 11.0, 11.0, 11.0, 11.0)
    assertThat(abs(pairedDeltaPercent(base, head) - 10.0)).isLessThan(0.0001)
  }
}
