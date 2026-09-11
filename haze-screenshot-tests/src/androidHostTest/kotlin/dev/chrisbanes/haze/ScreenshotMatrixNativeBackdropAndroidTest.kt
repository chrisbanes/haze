// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import android.os.Build
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/** SDK 37-only native Backdrop visual references, isolated from fallback matrix coverage. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [SCREENSHOT_MATRIX_ANDROID_SDK_37])
internal class ScreenshotMatrixNativeBackdropAndroidTest(
  private val case: ScreenshotMatrixCase,
) : ScreenshotTest() {

  @Test
  fun capture() {
    val profiledCase = case.withProfile(ScreenshotMatrixProfile.AndroidHost(Build.VERSION.SDK_INT))
    profiledCase.withPlatformBackdropFlag {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            profiledCase.Render()
          }
        }
        // Refresh the window display list before Robolectric's synchronous hardware PixelCopy.
        composeTestRule.runOnIdle {
          composeTestRule.activity.window.decorView.invalidate()
        }
        waitForIdle()
        captureRoot(artifactPath = profiledCase.artifactPath)
      }
    }
  }

  private companion object {
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
    fun cases(): List<Array<Any>> = ScreenshotMatrix.selectNativeHostCases(
      profile = ScreenshotMatrixProfile.AndroidHost(SCREENSHOT_MATRIX_ANDROID_SDK_37),
      selectedCaseId = System.getProperty(SCREENSHOT_MATRIX_CASE_PROPERTY),
    ).map { arrayOf<Any>(it) }
  }
}
