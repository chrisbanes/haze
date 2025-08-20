// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.roborazziSystemPropertyCompareOutputDirectory
import io.github.takahirom.roborazzi.CompareOptions
import io.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestScope

actual interface ScreenshotTest

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
actual fun TestScope.runScreenshotTest(block: ScreenshotUiTest.() -> Unit) {
  runSkikoComposeUiTest(
    size = Size(1080f, 1920f),
    density = Density(2.75f),
  ) {
    createScreenshotUiTest(testCase).block()
  }
}

@OptIn(ExperimentalRoborazziApi::class)
private val options = RoborazziOptions(
  compareOptions = CompareOptions(
    outputDirectoryPath = roborazziSystemPropertyCompareOutputDirectory(),
  ),
)

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
private fun SkikoComposeUiTest.createScreenshotUiTest(testCase: TestCase) = object : ScreenshotUiTest {
  override fun setContent(content: @Composable () -> Unit) {
    this@createScreenshotUiTest.setContent(content)
  }

  override fun captureRoot(nameSuffix: String?) {
    this@createScreenshotUiTest.onRoot().captureRoboImage(
      composeUiTest = this@createScreenshotUiTest,
      filePath = "ios/${testCase.generateFilename(nameSuffix)}",
      roborazziOptions = options,
    )
  }

  override fun waitForIdle() {
    this@createScreenshotUiTest.waitForIdle()
  }
}
