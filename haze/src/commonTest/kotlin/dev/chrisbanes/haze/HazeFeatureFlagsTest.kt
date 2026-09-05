// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class HazeFeatureFlagsTest {

  @Test
  fun platformBackdrop_isDisabledByDefaultAndMutable() {
    assertThat(HazeFeatureFlags.isPlatformBackdropEnabled).isFalse()

    val previous = HazeFeatureFlags.isPlatformBackdropEnabled
    try {
      HazeFeatureFlags.isPlatformBackdropEnabled = true
      assertThat(HazeFeatureFlags.isPlatformBackdropEnabled).isTrue()
    } finally {
      HazeFeatureFlags.isPlatformBackdropEnabled = previous
    }
  }
}
