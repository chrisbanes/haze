// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.ScreenshotUiTest

internal fun ScreenshotUiTest.captureMatrixCase(case: ScreenshotMatrixCase) {
  var input by mutableStateOf(case.input)
  setContent {
    ScreenshotTheme {
      key(input) {
        ScreenshotMatrixCase(case.scene, input, case.mode, case.profile).Render()
      }
    }
  }
  captureRoot(artifactPath = case.artifactPath)
  if (case.input == ScreenshotMatrixInput.BackdropFallback) {
    val fallback = captureRootPixels().snapshot()
    input = ScreenshotMatrixInput.Sources
    waitForIdle()
    val sources = captureRootPixels().snapshot()
    assertThat(fallback.meanAbsoluteDifference(sources), "${case.id} source/fallback parity")
      .isLessThanOrEqualTo(1f / 255f)
  }
}
