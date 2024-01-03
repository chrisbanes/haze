// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable

actual abstract class ScreenshotTest

actual fun ScreenshotTest.screenshot(content: @Composable () -> Unit) {
  // no-op on iOS
}
