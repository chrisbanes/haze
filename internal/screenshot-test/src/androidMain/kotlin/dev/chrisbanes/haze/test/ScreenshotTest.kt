// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.AndroidComposeUiTestEnvironment
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roboOutputName
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28, 33], qualifiers = RobolectricDeviceQualifiers.Pixel5)
actual abstract class ScreenshotTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @get:Rule
  val roborazziRule = RoborazziRule(
    composeRule = composeTestRule,
    captureRoot = composeTestRule.onRoot(),
    options = RoborazziRule.Options(
      outputDirectoryPath = "screenshots/${HazeRoborazziDefaults.outputDirectoryName}",
      roborazziOptions = HazeRoborazziDefaults.roborazziOptions,
    ),
  )

  @Before
  fun setup() {
    System.setProperty("robolectric.logging.enabled", "true")
    System.setProperty("robolectric.screenshot.hwrdr.native", "true")
  }
}

actual val HazeRoborazziDefaults.outputDirectoryName: String get() = "android"

@OptIn(ExperimentalTestApi::class)
actual fun ScreenshotTest.runScreenshotTest(block: ScreenshotUiTest.() -> Unit) {
  val environment = AndroidComposeUiTestEnvironment { composeTestRule.activity }
  try {
    environment.runTest {
      createScreenshotUiTest().block()
    }
  } finally {
    // Close the scenario outside runTest to avoid getting stuck.
    //
    // ActivityScenario.close() calls Instrumentation.waitForIdleSync(), which would time out
    // if there is an infinite self-invalidating measure, layout, or draw loop. If the
    // Compose content was set through the test's setContent method, it will remove the
    // AndroidComposeView from the view hierarchy which breaks this loop, which is why we
    // call close() outside the runTest lambda. This will not help if the content is not set
    // through the test's setContent method though, in which case we'll still time out here.
    composeTestRule.activityRule.scenario.close()
  }
}

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
private fun <A : ComponentActivity> AndroidComposeUiTest<A>.createScreenshotUiTest() = object : ScreenshotUiTest {
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
