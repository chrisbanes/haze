// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import dev.chrisbanes.haze.test.ScreenshotUiTest
import org.robolectric.annotation.Config

@Config(sdk = [37])
class ColorFilterAlignmentAndroidTest : ColorFilterAlignmentRegressionTest() {
  override fun ScreenshotUiTest.refreshCapture() {
    composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
    waitForIdle()
  }
}
