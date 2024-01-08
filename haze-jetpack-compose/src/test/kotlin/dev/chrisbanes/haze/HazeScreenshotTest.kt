// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class HazeScreenshotTest : ScreenshotTest() {
  @Test
  fun creditCard() = runScreenshotTest {
    setContent {
      MaterialTheme {
        CreditCardSample()
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_transparentTint() = runScreenshotTest {
    setContent {
      MaterialTheme {
        CreditCardSample(tint = Color.Transparent)
      }
    }
    captureRoot()
  }
}
