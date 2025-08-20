// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import android.content.ContentProvider
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.AndroidComposeUiTestEnvironment
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestScope
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28, 32, 35], qualifiers = RobolectricDeviceQualifiers.Pixel5)
actual interface ScreenshotTest

@OptIn(ExperimentalTestApi::class)
actual fun TestScope.runScreenshotTest(block: ScreenshotUiTest.() -> Unit) {
  // Fix Compose resources for Robolectric
  @Suppress("UNCHECKED_CAST")
  val clazz = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider") as Class<ContentProvider>
  Robolectric.setupContentProvider(clazz)

  val controller = Robolectric.buildActivity(ComponentActivity::class.java)
  controller.setup()

  try {
    val environment = AndroidComposeUiTestEnvironment { controller.get() }
    environment.runTest {
      createScreenshotUiTest(testCase).block()
    }
  } finally {
    // Close the activity to avoid getting stuck in idle sync loops.
    controller.pause().stop().destroy()
  }
}

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
private fun <A : ComponentActivity> AndroidComposeUiTest<A>.createScreenshotUiTest(
  testCase: TestCase,
): ScreenshotUiTest = object : ScreenshotUiTest {
  override fun setContent(content: @Composable () -> Unit) {
    this@createScreenshotUiTest.setContent(content)
  }

  override fun captureRoot(nameSuffix: String?) {
    this@createScreenshotUiTest.onRoot().captureRoboImage(
      filePath = "android/${testCase.generateFilename(nameSuffix)}",
      roborazziOptions = HazeRoborazziDefaults.roborazziOptions,
    )
  }

  override fun waitForIdle() {
    this@createScreenshotUiTest.waitForIdle()
  }
}
