// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.PixelMap

expect abstract class ScreenshotTest()

expect fun ScreenshotTest.runScreenshotTest(
  block: ScreenshotUiTest.() -> Unit,
)

interface ScreenshotUiTest {
  val supportsRuntimeBlur: Boolean
  fun setContent(content: @Composable () -> Unit)
  fun captureRoot(nameSuffix: String? = null)
  fun captureRootPixels(): PixelMap
  fun waitForIdle()
  fun swipeUpOnRoot()
}
