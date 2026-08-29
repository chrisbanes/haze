// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.github.takahirom.roborazzi.roboOutputName
import io.github.takahirom.roborazzi.captureRoboImage

actual abstract class ScreenshotTest : ContextTest()

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
actual fun ScreenshotTest.runScreenshotTest(
  size: Size,
  block: ScreenshotUiTest.() -> Unit,
) {
  runSkikoComposeUiTest(
    size = size,
    density = Density(2.75f),
  ) {
    provideRoborazziContext().apply {
      setRuleOverrideRoborazziOptions(HazeRoborazziDefaults.roborazziOptions)
      setRuleOverrideOutputDirectory("screenshots/desktop")
    }
    createScreenshotUiTest().block()
  }
}

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
private fun SkikoComposeUiTest.createScreenshotUiTest() = object : ScreenshotUiTest {
  override val supportsRuntimeBlur: Boolean = true

  override fun setContent(content: @Composable () -> Unit) {
    this@createScreenshotUiTest.setContent(content)
  }

  override fun captureRoot(
    nameSuffix: String?,
    unmatchedPixelThreshold: Float?,
  ) {
    val output = when {
      nameSuffix.isNullOrEmpty() -> "${roboOutputName()}.webp"
      else -> "${roboOutputName()}_$nameSuffix.webp"
    }
    val options = unmatchedPixelThreshold
      ?.let(HazeRoborazziDefaults::roborazziOptions)
      ?: HazeRoborazziDefaults.roborazziOptions
    this@createScreenshotUiTest.onRoot().captureRoboImage(output, options)
  }

  override fun captureRootPixels(): PixelMap =
    this@createScreenshotUiTest.onRoot().captureToImage().toPixelMap()

  override fun onNodeWithTag(testTag: String) =
    this@createScreenshotUiTest.onNodeWithTag(testTag)

  override fun waitForIdle() {
    this@createScreenshotUiTest.waitForIdle()
  }

  override fun swipeUpOnRoot() {
    this@createScreenshotUiTest.onRoot().performTouchInput {
      swipeUp()
    }
  }
}
