package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.provideRoborazziContext
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

actual abstract class ScreenshotTest

actual val HazeRoborazziDefaults.outputDirectoryName: String get() = "desktop"

actual fun ScreenshotTest.screenshotTest(content: @Composable () -> Unit) {
  @OptIn(ExperimentalTestApi::class)
  captureRoborazziImage(content = content)
}

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
@ExperimentalTestApi
private fun captureRoborazziImage(
  width: Int = 1080,
  height: Int = 1920,
  density: Density = Density(2.75f),
  roborazziOptions: RoborazziOptions = HazeRoborazziDefaults.roborazziOptions,
  effectContext: CoroutineContext = EmptyCoroutineContext,
  content: @Composable () -> Unit,
) {
  DesktopComposeUiTest(
    width = width,
    height = height,
    effectContext = effectContext,
    density = density,
  ).runTest {
    provideRoborazziContext().apply {
      setRuleOverrideOutputDirectory("screenshots/desktop")
    }
    setContent(content)
    onRoot().captureRoboImage(roborazziOptions = roborazziOptions)
  }
}
