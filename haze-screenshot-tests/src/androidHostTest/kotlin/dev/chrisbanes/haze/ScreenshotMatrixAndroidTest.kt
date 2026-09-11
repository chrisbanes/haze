// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import android.os.Build
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(
  sdk = [
    SCREENSHOT_MATRIX_ANDROID_SDK_28,
    SCREENSHOT_MATRIX_ANDROID_SDK_32,
    SCREENSHOT_MATRIX_ANDROID_SDK_35,
  ],
)
internal class ScreenshotMatrixAndroidTest(
  private val case: ScreenshotMatrixCase,
) : ScreenshotTest() {

  @Test
  fun capture() {
    val profiledCase = case.withProfile(ScreenshotMatrixProfile.AndroidHost(Build.VERSION.SDK_INT))
    profiledCase.withPlatformBackdropFlag {
      runScreenshotTest {
        captureMatrixCase(profiledCase)
      }
    }
  }

  private companion object {
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
    fun cases(): List<Array<Any>> = ScreenshotMatrix.selectHostCases(
      profile = ScreenshotMatrixProfile.AndroidHost(SCREENSHOT_MATRIX_ANDROID_SDK_28),
      selectedCaseId = System.getProperty(SCREENSHOT_MATRIX_CASE_PROPERTY),
    ).map { arrayOf<Any>(it) }
  }
}
