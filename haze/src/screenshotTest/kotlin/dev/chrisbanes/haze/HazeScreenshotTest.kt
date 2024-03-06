// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class HazeScreenshotTest : ScreenshotTest() {
  @Test
  fun creditCard() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample()
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_transparentTint() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(defaultTint = Color.Transparent)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_childTint() = runScreenshotTest {
    var tint by mutableStateOf(Color.Magenta.copy(alpha = 0.5f))

    setContent {
      ScreenshotTheme {
        CreditCardSample(childTint = tint)
      }
    }

    waitForIdle()
    captureRoot("magenta")

    tint = Color.Yellow.copy(alpha = 0.5f)
    waitForIdle()
    captureRoot("yellow")

    tint = Color.Red.copy(alpha = 0.5f)
    waitForIdle()
    captureRoot("red")
  }
}
