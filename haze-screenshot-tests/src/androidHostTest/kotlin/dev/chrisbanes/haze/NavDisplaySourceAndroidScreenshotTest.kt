// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35])
class NavDisplaySourceAndroidScreenshotTest : ScreenshotTest() {

  @Test
  fun blur_navigationSuiteSibling_matchesDirectSibling() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        NavigationSample(
          backStack = listOf(FIRST_SCENE),
          modifier = Modifier.testTag(NAVIGATION_SAMPLE_TAG),
        )
      }
    }
    waitForIdle()

    val root = captureRootPixels().snapshot()
    val controlBounds = onNodeWithTag(DIRECT_NAVIGATION_TAG).fetchSemanticsNode().boundsInRoot
    val subjectBounds = onNodeWithTag(ADAPTIVE_NAVIGATION_TAG).fetchSemanticsNode().boundsInRoot
    assertNavigationSuiteSiblingCapture(
      root = root,
      controlBounds = controlBounds,
      subjectBounds = subjectBounds,
      pixelTolerance = ANDROID_PIXEL_TOLERANCE,
    )
  }
}

private const val ANDROID_PIXEL_TOLERANCE = 0.01f
