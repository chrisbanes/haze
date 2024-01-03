package dev.chrisbanes.haze.test

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = RobolectricDeviceQualifiers.Pixel5)
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
}

actual val HazeRoborazziDefaults.outputDirectoryName: String get() = "android"

actual fun ScreenshotTest.screenshotTest(content: @Composable () -> Unit) {
  captureRoboImage(content = content)
}
