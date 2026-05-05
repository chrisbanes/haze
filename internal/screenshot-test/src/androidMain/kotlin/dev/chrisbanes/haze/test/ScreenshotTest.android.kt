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
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roboOutputName
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28, 32, 35], qualifiers = RobolectricDeviceQualifiers.Pixel5)
actual abstract class ScreenshotTest : ContextTest()

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
actual fun ScreenshotTest.runScreenshotTest(block: ScreenshotUiTest.() -> Unit) {
  @Suppress("UNCHECKED_CAST")
  val clazz =
    Class.forName("org.jetbrains.compose.resources.AndroidContextProvider") as Class<ContentProvider>
  Robolectric.setupContentProvider(clazz)

  var activity: ComponentActivity? = null
  val scenario = ActivityScenario.launch(ComponentActivity::class.java).onActivity {
    activity = it
  }
  val environment = AndroidComposeUiTestEnvironment<ComponentActivity> { requireNotNull(activity) }
  try {
    environment.runTest {
      createScreenshotUiTest().block()
    }
  } finally {
    scenario.close()
  }
}

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
private fun <A : ComponentActivity> AndroidComposeUiTest<A>.createScreenshotUiTest() =
  object : ScreenshotUiTest {
    override fun setContent(content: @Composable () -> Unit) {
      this@createScreenshotUiTest.setContent(content)
    }

    override fun captureRoot(nameSuffix: String?) {
      this@createScreenshotUiTest.waitForIdle()
      val name = when {
        nameSuffix.isNullOrEmpty() -> "${roboOutputName()}.png"
        else -> "${roboOutputName()}_$nameSuffix.png"
      }
      this@createScreenshotUiTest.onRoot().captureRoboImage(
        filePath = "android/$name",
        roborazziOptions = HazeRoborazziDefaults.roborazziOptions,
      )
    }

    override fun waitForIdle() {
      this@createScreenshotUiTest.waitForIdle()
    }
  }
