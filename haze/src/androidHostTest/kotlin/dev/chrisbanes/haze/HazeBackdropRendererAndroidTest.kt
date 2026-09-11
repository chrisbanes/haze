// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class HazeBackdropRendererAndroidTest {
  @Test
  @Config(sdk = [37])
  fun createHazeBackdropRenderer_supportsRobolectricAndroid17() {
    assertThat(createHazeBackdropRenderer()).isNotNull()
  }

  @Test
  @Config(sdk = [35, 36])
  fun createHazeBackdropRenderer_rejectsOlderRobolectricSdks() {
    assertThat(createHazeBackdropRenderer()).isNull()
  }
}
