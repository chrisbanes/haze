// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Ignore
import kotlin.test.Test

class HazeScreenshotTest : ScreenshotTest() {
  @Test
  fun creditCard() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(tint = DefaultTint)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_style() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(style = OverrideStyle)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_compositionLocalStyle() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CompositionLocalProvider(LocalHazeStyle provides OverrideStyle) {
          CreditCardSample()
        }
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
          tint = DefaultTint,
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
        CreditCardSample(tint = DefaultTint, alpha = 0.5f)
      }
    }
    captureRoot()
  }

  @Test
  @Ignore // https://github.com/robolectric/robolectric/issues/9691
  fun creditCard_progressive_horiz() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(
          tint = DefaultTint,
          progressive = HazeProgressive.horizontalGradient(),
        )
      }
    }
    captureRoot()
  }

  @Test
  @Ignore // https://github.com/robolectric/robolectric/issues/9691
  fun creditCard_progressive_vertical() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(
          tint = DefaultTint,
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
        CreditCardSample(tint = DefaultTint, enabled = enabled)
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
        CreditCardSample(tint = DefaultTint, shape = roundedCornerShape)
      }
    }
    captureRoot()
  }

  companion object {
    val DefaultTint = HazeTint(Color.White.copy(alpha = 0.1f))
    val OverrideStyle = HazeStyle(tints = listOf(HazeTint(Color.Red.copy(alpha = 0.5f))))
  }
}
