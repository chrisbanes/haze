// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.test.ExperimentalTestApi
import org.junit.Test

class HazeScreenshotTest {
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun creditCard() = captureRoboImage {
    CreditCardSample()
  }
}
