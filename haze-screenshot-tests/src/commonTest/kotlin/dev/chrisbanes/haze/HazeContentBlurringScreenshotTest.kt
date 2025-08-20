// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import io.kotest.core.spec.style.FunSpec

class HazeContentBlurringScreenshotTest : FunSpec(), ScreenshotTest {
  init {
    test("creditCard") {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(tint = DefaultTint, blurRadius = 8.dp)
          }
        }
        captureRoot()
      }
    }

    test("creditCard_noStyle") {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring()
          }
        }
        captureRoot()
      }
    }

    test("creditCard_blurEnabled") {
      runScreenshotTest {
        var blurEnabled by mutableStateOf(HazeDefaults.blurEnabled())

        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(tint = DefaultTint, blurRadius = 8.dp, blurEnabled = blurEnabled)
          }
        }

        waitForIdle()
        captureRoot("default")

        blurEnabled = false
        waitForIdle()
        captureRoot("disabled")

        blurEnabled = true
        waitForIdle()
        captureRoot("enabled")
      }
    }

    test("creditCard_style") {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(style = OverrideStyle)
          }
        }
        captureRoot()
      }
    }

    test("creditCard_compositionLocalStyle") {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            CompositionLocalProvider(LocalHazeStyle provides OverrideStyle) {
              CreditCardContentBlurring(blurRadius = 8.dp)
            }
          }
        }
        captureRoot()
      }
    }

    test("creditCard_transparentTint") {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(blurRadius = 8.dp, tint = HazeTint(Color.Transparent))
          }
        }
        captureRoot()
      }
    }

    test("creditCard_zeroBlurRadius") {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(blurRadius = 0.dp)
          }
        }
        captureRoot()
      }
    }

    test("creditCard_mask") {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(tint = DefaultTint, mask = VerticalMask, blurRadius = 8.dp)
          }
        }
        captureRoot()
      }
    }

    test("creditCard_alpha") {
      runScreenshotTest {
        var alpha by mutableFloatStateOf(0.5f)

        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(tint = DefaultTint, blurRadius = 8.dp, alpha = alpha)
          }
        }

        captureRoot()

        alpha = 0.2f
        waitForIdle()
        captureRoot("20")

        alpha = 0.7f
        waitForIdle()
        captureRoot("70")
      }
    }

    test("creditCard_progressive_horiz") {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(
              tint = DefaultTint,
              blurRadius = 8.dp,
              progressive = HazeProgressive.horizontalGradient(),
            )
          }
        }
        captureRoot()
      }
    }

    test("creditCard_progressive_horiz_preferMask") {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(
              tint = DefaultTint,
              blurRadius = 8.dp,
              progressive = HazeProgressive.horizontalGradient(preferPerformance = true),
            )
          }
        }
        captureRoot()
      }
    }

    test("creditCard_progressive_vertical") {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(
              tint = DefaultTint,
              blurRadius = 8.dp,
              progressive = HazeProgressive.verticalGradient(),
            )
          }
        }
        captureRoot()
      }
    }

    test("creditCard_progressive_radial") {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(
              tint = DefaultTint,
              blurRadius = 8.dp,
              progressive = HazeProgressive.RadialGradient(),
            )
          }
        }
        captureRoot()
      }
    }

    test("creditCard_progressive_shader") {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(
              tint = DefaultTint,
              blurRadius = 8.dp,
              progressive = HazeProgressive.Brush(
                Brush.sweepGradient(colors = listOf(Color.Transparent, Color.Black)),
              ),
            )
          }
        }
        captureRoot()
      }
    }

    test("creditCard_childTint") {
      runScreenshotTest {
        var tint by mutableStateOf(
          HazeTint(Color.Magenta.copy(alpha = 0.5f)),
        )

        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(tint = tint, blurRadius = 8.dp)
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
    }

    test("creditCard_sourceContentChange") {
      runScreenshotTest {
        var backgroundColors by mutableStateOf(listOf(Color.Blue, Color.Cyan))

        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(backgroundColors = backgroundColors, blurRadius = 8.dp, tint = DefaultTint)
          }
        }

        waitForIdle()
        captureRoot("blue")

        backgroundColors = listOf(Color.Yellow, Color.hsl(0.4f, 0.94f, 0.58f))
        waitForIdle()
        captureRoot("yellow")

        backgroundColors = listOf(Color.Red, Color.hsl(0.06f, 0.69f, 0.35f))
        waitForIdle()
        captureRoot("red")
      }
    }

    test("creditCard_brushTint") {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(tint = BrushTint, blurRadius = 8.dp)
          }
        }
        captureRoot()
      }
    }

    test("creditCard_brushTint_mask") {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(tint = BrushTint, blurRadius = 8.dp, mask = VerticalMask)
          }
        }
        captureRoot()
      }
    }

    test("creditCard_brushTint_progressive") {
      runScreenshotTest {
        setContent {
          ScreenshotTheme {
            CreditCardContentBlurring(
              tint = BrushTint,
              blurRadius = 8.dp,
              progressive = HazeProgressive.verticalGradient(),
            )
          }
        }
        captureRoot()
      }
    }
  }

  companion object {
    val DefaultTint = HazeTint(Color.White.copy(alpha = 0.1f))
    val OverrideStyle = HazeStyle(tints = listOf(HazeTint(Color.Red.copy(alpha = 0.5f))))

    val BrushTint = HazeTint(
      brush = Brush.radialGradient(
        colors = listOf(
          Color.Yellow.copy(alpha = 0.5f),
          Color.Red.copy(alpha = 0.5f),
        ),
      ),
    )

    val VerticalMask = Brush.verticalGradient(
      0f to Color.Transparent,
      0.5f to Color.Black,
      1f to Color.Transparent,
    )
  }
}
