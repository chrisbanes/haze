// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
        CreditCardSample(tint = HazeTint(Color.Transparent))
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_mask() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(
          mask = Brush.verticalGradient(
            0f to Color.Transparent,
            0.5f to Color.Black,
            1f to Color.Transparent,
          ),
        )
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_alpha() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(alpha = 0.5f)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_horiz() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(
          progressive = HazeProgressive.horizontalGradient(),
        )
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_vertical() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(
          progressive = HazeProgressive.verticalGradient(),
        )
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_childTint() = runScreenshotTest {
    var tint by mutableStateOf(
      HazeTint(Color.Magenta.copy(alpha = 0.5f)),
    )

    setContent {
      ScreenshotTheme {
        CreditCardSample(tint = tint)
      }
    }

    waitForIdle()
    captureRoot("magenta")

    tint = HazeTint(Color.Yellow.copy(alpha = 0.5f))
    waitForIdle()
    captureRoot("yellow")

    tint = HazeTint(Color.Red.copy(alpha = 0.5f))
    waitForIdle()
    captureRoot("red")
  }

  @Test
  fun creditCard_roundedCorner_topStart() {
    roundedCornerTest(RoundedCornerShape(topStart = 32.dp))
  }

  @Test
  fun creditCard_roundedCorner_topEnd() {
    roundedCornerTest(RoundedCornerShape(topEnd = 32.dp))
  }

  @Test
  fun creditCard_roundedCorner_bottomEnd() {
    roundedCornerTest(RoundedCornerShape(bottomEnd = 32.dp))
  }

  @Test
  fun creditCard_roundedCorner_bottomStart() {
    roundedCornerTest(RoundedCornerShape(bottomStart = 32.dp))
  }

  @Test
  fun creditCard_conditional() = runScreenshotTest {
    var enabled by mutableStateOf(true)

    setContent {
      ScreenshotTheme {
        CreditCardSample(enabled = enabled)
      }
    }

    waitForIdle()
    captureRoot("0_initial")

    enabled = false
    waitForIdle()
    captureRoot("1_disabled")

    enabled = true
    waitForIdle()
    captureRoot("2_reenabled")
  }

  private fun roundedCornerTest(roundedCornerShape: RoundedCornerShape) = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(shape = roundedCornerShape)
      }
    }
    captureRoot()
  }
}
