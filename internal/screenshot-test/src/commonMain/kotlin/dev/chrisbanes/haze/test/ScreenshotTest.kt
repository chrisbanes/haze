package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable

expect abstract class ScreenshotTest()

expect fun ScreenshotTest.screenshotTest(content: @Composable () -> Unit)
