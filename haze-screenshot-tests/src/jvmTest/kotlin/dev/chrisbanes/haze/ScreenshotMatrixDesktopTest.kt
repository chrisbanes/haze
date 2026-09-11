// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class ScreenshotMatrixDesktopTest(
  private val case: ScreenshotMatrixCase,
) : ScreenshotTest() {

  @Test
  fun capture() = case.withPlatformBackdropFlag {
    runScreenshotTest {
      setContent {
        ScreenshotTheme {
          case.Render()
        }
      }
      captureRoot(artifactPath = case.artifactPath)
    }
  }

  private companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun cases(): List<Array<Any>> = selectedCases().map { arrayOf<Any>(it) }

    private fun selectedCases(): List<ScreenshotMatrixCase> = ScreenshotMatrix.selectHostCases(
      profile = ScreenshotMatrixProfile.Desktop,
      selectedCaseId = System.getProperty(ScreenshotMatrixCaseProperty),
      fullRun = System.getProperty(ScreenshotMatrixFullRunProperty).toBoolean(),
    )
  }
}
