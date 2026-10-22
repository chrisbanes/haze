// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

@OptIn(ExperimentalHazeApi::class)
class HazeInputSharingTest {

  @Test
  fun auto_sharesFromTwoConsumersWhereSupported() {
    assertThat(HazeInputSharing.Auto.isActive(consumerCount = 1, autoSupported = true)).isFalse()
    assertThat(HazeInputSharing.Auto.isActive(consumerCount = 2, autoSupported = true)).isTrue()
  }

  @Test
  fun auto_neverSharesWhereUnsupported() {
    assertThat(HazeInputSharing.Auto.isActive(consumerCount = 5, autoSupported = false)).isFalse()
  }

  @Test
  fun always_sharesWithAnyConsumer() {
    assertThat(HazeInputSharing.Always.isActive(consumerCount = 0, autoSupported = false)).isFalse()
    assertThat(HazeInputSharing.Always.isActive(consumerCount = 1, autoSupported = false)).isTrue()
  }

  @Test
  fun never_neverShares() {
    assertThat(HazeInputSharing.Never.isActive(consumerCount = 5, autoSupported = true)).isFalse()
  }
}
