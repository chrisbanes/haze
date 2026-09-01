// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class HazeBackdropSdkSupportTest {

  @Test
  fun isHazeBackdropSdkSupported_returnsTrue_forFinal37_2() {
    assertThat(
      isHazeBackdropSdkSupported(
        fullSdkInt = 3_700_002,
        previewSdkInt = 0,
      ),
    ).isTrue()
  }

  @Test
  fun isHazeBackdropSdkSupported_returnsTrue_for37_2Beta3() {
    assertThat(
      isHazeBackdropSdkSupported(
        fullSdkInt = 3_700_001,
        previewSdkInt = 3_723,
      ),
    ).isTrue()
  }

  @Test
  fun isHazeBackdropSdkSupported_returnsFalse_forDifferentPreviewRevision() {
    assertThat(
      isHazeBackdropSdkSupported(
        fullSdkInt = 3_700_001,
        previewSdkInt = 3_722,
      ),
    ).isFalse()
  }

  @Test
  fun isHazeBackdropSdkSupported_returnsFalse_forFinal37_1() {
    assertThat(
      isHazeBackdropSdkSupported(
        fullSdkInt = 3_700_001,
        previewSdkInt = 0,
      ),
    ).isFalse()
  }
}
