package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable

actual abstract class ScreenshotTest

actual fun ScreenshotTest.screenshotTest(content: @Composable () -> Unit) {
}
