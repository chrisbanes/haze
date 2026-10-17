// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import dev.chrisbanes.haze.test.ScreenshotUiTest
import org.robolectric.annotation.Config

@Config(sdk = [35])
class NavDisplaySourceAndroidTest : NavDisplaySourceScreenshotTest() {
  override fun ScreenshotUiTest.refreshCapture() {
    composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
    waitForIdle()
    // The first hardware snapshot after a clock step can precede descendant layer updates.
    captureRootPixels()
    composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
    waitForIdle()
  }
}
