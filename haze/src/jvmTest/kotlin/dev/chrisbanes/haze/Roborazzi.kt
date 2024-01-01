// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.provideRoborazziContext
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalRoborazziApi::class)
object HazeRoborazziDefaults {

  val roborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
      changeThreshold = 0.1f,
      imageComparator = SimpleImageComparator(maxDistance = 0f, hShift = 1, vShift = 1),
    ),
  )
}

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
@ExperimentalTestApi
fun captureRoboImage(
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
