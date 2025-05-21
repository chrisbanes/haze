// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

actual abstract class ScreenshotTest : ContextTest()

actual fun ScreenshotTest.runScreenshotTest(
  block: ScreenshotUiTest.() -> Unit,
) {
  // no-op
}
