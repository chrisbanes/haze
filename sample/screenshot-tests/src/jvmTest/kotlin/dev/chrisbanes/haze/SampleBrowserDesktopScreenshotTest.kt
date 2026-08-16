// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Size
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class SampleBrowserDesktopScreenshotTest : ScreenshotTest() {
  @Test
  fun portrait() = runScreenshotTest {
    captureSampleBrowser()
  }

  @Test
  fun landscape() = runScreenshotTest(size = Size(1920f, 1080f)) {
    captureSampleBrowser()
  }

  @Test
  fun largeLandscape() = runScreenshotTest(size = Size(2560f, 1440f)) {
    captureSampleBrowser(sampleTitle = "Credit Card")
  }
}
