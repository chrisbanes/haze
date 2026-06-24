// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roboOutputName
import org.junit.Rule
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28, 32, 35], qualifiers = RobolectricDeviceQualifiers.Pixel5)
actual abstract class ScreenshotTest : ContextTest() {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @get:Rule
  val roborazziRule = RoborazziRule(
    composeRule = composeTestRule,
    captureRoot = composeTestRule.onRoot(),
    options = RoborazziRule.Options(
      outputDirectoryPath = "screenshots/android",
      roborazziOptions = HazeRoborazziDefaults.roborazziOptions,
    ),
  )
}

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
actual fun ScreenshotTest.runScreenshotTest(
  relaxedTolerance: Boolean,
  block: ScreenshotUiTest.() -> Unit,
) {
  createScreenshotUiTest(composeTestRule).block()
}

@OptIn(ExperimentalRoborazziApi::class)
private fun createScreenshotUiTest(rule: AndroidComposeTestRule<*, *>) =
  object : ScreenshotUiTest {
    override fun setContent(content: @Composable () -> Unit) {
      rule.setContent(content)
      rule.waitForIdle()
    }

    override fun captureRoot(nameSuffix: String?) {
      val output = when {
        nameSuffix.isNullOrEmpty() -> "${roboOutputName()}.png"
        else -> "${roboOutputName()}_$nameSuffix.png"
      }
      rule.onRoot().captureRoboImage(output)
    }

    override fun waitForIdle() {
      rule.waitForIdle()
    }

    override fun swipeUpOnRoot() {
      rule.onRoot().performTouchInput {
        swipeUp()
      }
    }
  }
