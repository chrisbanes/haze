// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable

expect abstract class ScreenshotTest()

expect fun ScreenshotTest.screenshotTest(content: @Composable () -> Unit)
