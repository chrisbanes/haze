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
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.github.takahirom.roborazzi.roboOutputName
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28, 32, 35], qualifiers = RobolectricDeviceQualifiers.Pixel5)
actual abstract class ScreenshotTest : ContextTest()

@OptIn(
  ExperimentalTestApi::class,
  ExperimentalRoborazziApi::class,
  InternalRoborazziApi::class,
)
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
      provideRoborazziContext().apply {
        setRuleOverrideRoborazziOptions(HazeRoborazziDefaults.roborazziOptions)
        setRuleOverrideOutputDirectory("screenshots/android")
      }
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
