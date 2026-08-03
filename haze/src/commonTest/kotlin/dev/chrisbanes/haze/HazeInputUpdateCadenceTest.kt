// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class HazeInputUpdateCadenceTest {

  @Test
  fun rapidDistinctUpdatesIncreaseMultiplierUpToCap() {
    val timeSource = TestTimeSource()
    val cadence = HazeInputUpdateCadence(timeSource)

    assertThat(cadence.observeUpdate("frame-1")).isFalse()
    assertThat(cadence.multiplier).isEqualTo(1)

    timeSource += 16.milliseconds
    assertThat(cadence.observeUpdate("frame-2")).isTrue()
    assertThat(cadence.multiplier).isEqualTo(2)

    timeSource += 16.milliseconds
    assertThat(cadence.observeUpdate("frame-3")).isTrue()
    assertThat(cadence.multiplier).isEqualTo(3)

    timeSource += 16.milliseconds
    assertThat(cadence.observeUpdate("frame-4")).isFalse()
    assertThat(cadence.multiplier).isEqualTo(3)
  }

  @Test
  fun repeatedInputDoesNotAccumulateAndQuietPeriodResetsMultiplier() {
    val timeSource = TestTimeSource()
    val cadence = HazeInputUpdateCadence(timeSource)

    cadence.observeUpdate("stable")
    repeat(3) {
      timeSource += 16.milliseconds
      assertThat(cadence.observeUpdate("stable")).isFalse()
      assertThat(cadence.multiplier).isEqualTo(1)
    }

    timeSource += 16.milliseconds
    cadence.observeUpdate("changed")
    assertThat(cadence.multiplier).isEqualTo(2)

    timeSource += 250.milliseconds
    assertThat(cadence.observeUpdate("changed")).isTrue()
    assertThat(cadence.multiplier).isEqualTo(1)
  }
}
