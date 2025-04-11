// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        CreditCardSample(tint = DefaultTint, blurRadius = 8.dp)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_noStyle() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample()
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_multiple() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(tint = DefaultTint, blurRadius = 8.dp, numberCards = 3)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_blurEnabled() = runScreenshotTest {
    var blurEnabled by mutableStateOf(HazeDefaults.blurEnabled())

    setContent {
      ScreenshotTheme {
        CreditCardSample(tint = DefaultTint, blurRadius = 8.dp, blurEnabled = blurEnabled)
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
          CreditCardSample(blurRadius = 8.dp)
        }
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_transparentTint() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(blurRadius = 8.dp, tint = HazeTint(Color.Transparent))
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_zeroBlurRadius() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(blurRadius = 0.dp)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_mask() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(tint = DefaultTint, mask = VerticalMask, blurRadius = 8.dp)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_alpha() = runScreenshotTest {
    var alpha by mutableFloatStateOf(0.5f)

    setContent {
      ScreenshotTheme {
        CreditCardSample(tint = DefaultTint, blurRadius = 8.dp, alpha = alpha)
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

  @Test
  fun creditCard_progressive_horiz() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(
          tint = DefaultTint,
          blurRadius = 8.dp,
          progressive = HazeProgressive.horizontalGradient(),
        )
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_horiz_preferMask() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(
          tint = DefaultTint,
          blurRadius = 8.dp,
          progressive = HazeProgressive.horizontalGradient(preferPerformance = true),
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
          tint = DefaultTint,
          blurRadius = 8.dp,
          progressive = HazeProgressive.verticalGradient(),
        )
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_vertical_multiple() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(
          tint = DefaultTint,
          blurRadius = 8.dp,
          progressive = HazeProgressive.verticalGradient(),
          numberCards = 3,
        )
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_radial() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(
          tint = DefaultTint,
          blurRadius = 8.dp,
          progressive = HazeProgressive.RadialGradient(),
        )
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_shader() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(
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

  @Test
  fun creditCard_childTint() = runScreenshotTest {
    var tint by mutableStateOf(
      HazeTint(Color.Magenta.copy(alpha = 0.5f)),
    )

    setContent {
      ScreenshotTheme {
        CreditCardSample(tint = tint, blurRadius = 8.dp)
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
        CreditCardSample(tint = DefaultTint, blurRadius = 8.dp, enabled = enabled)
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
        CreditCardSample(tint = DefaultTint, blurRadius = 8.dp, shape = roundedCornerShape)
      }
    }
    captureRoot()
  }

  /**
   * This test does not currently produce the correct output on Skia platforms.
   * It works correctly when run on device, etc. It seems to be a timing setup thing in tests.
   *
   * My working theory is that state updates are ran immediately in the CMP UI tests, which
   * breaks how dependent graphics layers are invalidated. In non-tests, state updates are deferred
   * until the next 'pass'.
   *
   * This is being re-worked in CMP 1.8, so there's little point in investigating this too much:
   * https://youtrack.jetbrains.com/issue/CMP-6703
   */
  @Test
  fun creditCard_sourceContentChange() = runScreenshotTest {
    var backgroundColors by mutableStateOf(listOf(Color.Blue, Color.Cyan))

    setContent {
      ScreenshotTheme {
        CreditCardSample(backgroundColors = backgroundColors, blurRadius = 8.dp, tint = DefaultTint)
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

  @Test
  fun creditCard_brushTint() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(tint = BrushTint, blurRadius = 8.dp)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_brushTint_mask() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(tint = BrushTint, blurRadius = 8.dp, mask = VerticalMask)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_brushTint_progressive() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardSample(
          tint = BrushTint,
          blurRadius = 8.dp,
          progressive = HazeProgressive.verticalGradient(),
        )
      }
    }
    captureRoot()
  }

  @Test
  fun nested_content() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        Box(Modifier.fillMaxSize()) {
          val outerHazeState = remember { HazeState() }

          Box(Modifier.hazeSource(outerHazeState)) {
            CreditCardSample(tint = DefaultTint, blurRadius = 8.dp)
          }

          Box(
            modifier = Modifier
              .hazeEffect(
                state = outerHazeState,
                style = HazeDefaults.style(
                  backgroundColor = Color.Blue,
                  tint = DefaultTint,
                  blurRadius = 8.dp,
                ),
              )
              .align(Alignment.TopStart)
              .fillMaxWidth()
              .height(56.dp),
          )
        }
      }
    }
    captureRoot()
  }

  @Test
  fun horizontalPager_quarter() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardPagerSample(.25f, tint = DefaultTint, blurRadius = 8.dp)
      }
    }
    captureRoot()
  }

  @Test
  fun horizontalPager_half() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardPagerSample(.5f, tint = DefaultTint, blurRadius = 8.dp)
      }
    }
    captureRoot()
  }

  @Test
  fun horizontalPager_three_quarters() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardPagerSample(.75f, tint = DefaultTint, blurRadius = 8.dp, numberCards = 3)
      }
    }
    captureRoot()
  }

  @Test
  fun horizontalPager_one_and_three_quarters() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardPagerSample(1.75f, tint = DefaultTint, blurRadius = 8.dp, numberCards = 3)
      }
    }
    captureRoot()
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
