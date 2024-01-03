// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.material3.MaterialTheme
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.screenshotTest
import kotlin.test.Test

class HazeScreenshotTest: ScreenshotTest() {
  @Test
  fun creditCard() = screenshotTest {
    MaterialTheme {
      CreditCardSample()
    }
  }
}
