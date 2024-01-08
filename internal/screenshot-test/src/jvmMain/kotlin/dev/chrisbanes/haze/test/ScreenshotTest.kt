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
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.github.takahirom.roborazzi.roboOutputName
import io.github.takahirom.roborazzi.captureRoboImage

actual abstract class ScreenshotTest

actual val HazeRoborazziDefaults.outputDirectoryName: String get() = "desktop"

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
actual fun ScreenshotTest.runScreenshotTest(
  block: ScreenshotUiTest.() -> Unit,
) {
  runSkikoComposeUiTest(
    size = Size(1080f, 1920f),
    density = Density(2.75f),
  ) {
    provideRoborazziContext().apply {
      setRuleOverrideRoborazziOptions(HazeRoborazziDefaults.roborazziOptions)
      setRuleOverrideOutputDirectory("screenshots/${HazeRoborazziDefaults.outputDirectoryName}")
    }
    createScreenshotUiTest().block()
  }
}

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
private fun SkikoComposeUiTest.createScreenshotUiTest() = object : ScreenshotUiTest {
  override fun setContent(content: @Composable () -> Unit) {
    this@createScreenshotUiTest.setContent(content)
  }

  override fun captureRoot(nameSuffix: String?) {
    val output = when {
      nameSuffix.isNullOrEmpty() -> "${roboOutputName()}.png"
      else -> "${roboOutputName()}_$nameSuffix.png"
    }
    this@createScreenshotUiTest.onRoot().captureRoboImage(output)
  }

  override fun waitForIdle() {
    this@createScreenshotUiTest.waitForIdle()
  }
}
