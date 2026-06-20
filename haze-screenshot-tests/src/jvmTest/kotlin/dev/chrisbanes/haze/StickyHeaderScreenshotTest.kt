// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class StickyHeaderScreenshotTest : ScreenshotTest() {

  @Test
  fun blur_stickyHeader_updatesAfterScroll() = runScreenshotTest(relaxedTolerance = true) {
    val visualEffect = stickyHeaderBlurVisualEffect()

    setContent {
      ScreenshotTheme {
        StickyHeaderListSample(
          visualEffect = visualEffect,
        )
      }
    }

    waitForIdle()
    captureRoot("initial")

    swipeUpOnRoot()
    waitForIdle()
    captureRoot("scrolled")
  }
}
