// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.BlurVisualEffect
import dev.chrisbanes.haze.blur.HazeBlurDefaults
import dev.chrisbanes.haze.blur.HazeProgressive
import dev.chrisbanes.haze.blur.HazeStyle
import dev.chrisbanes.haze.blur.HazeTint
import dev.chrisbanes.haze.blur.LocalHazeStyle
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class HazeScreenshotTest : ScreenshotTest() {

  @BeforeTest
  fun before() {
    HazeLogger.enabled = true
  }

  @Test
  fun creditCard() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_noStyle() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect()

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_multiple() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect, numberCards = 3)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_blurEnabled() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }

    waitForIdle()
    captureRoot("default")

    blurVisualEffect.blurEnabled = false
    waitForIdle()
    captureRoot("disabled")

    blurVisualEffect.blurEnabled = true
    waitForIdle()
    captureRoot("enabled")
  }

  @Test
  fun creditCard_style() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      style = OverrideStyle
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_compositionLocalStyle() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CompositionLocalProvider(LocalHazeStyle provides OverrideStyle) {
          CreditCardSample(visualEffect = blurVisualEffect)
        }
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_transparentTint() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      blurRadius = 8.dp
      tints = listOf(HazeTint(Color.Transparent))
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_zeroBlurRadius() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      blurRadius = 0.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_mask() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
      mask = VerticalMask
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_alpha() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
      alpha = 0.5f
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }

    captureRoot()

    blurVisualEffect.alpha = 0.2f
    waitForIdle()
    captureRoot("20")

    blurVisualEffect.alpha = 0.7f
    waitForIdle()
    captureRoot("70")
  }

  @Test
  fun creditCard_progressive_horiz() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.horizontalGradient()
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_horiz_preferMask() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.horizontalGradient(preferPerformance = true)
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_vertical() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.verticalGradient()
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_vertical_multiple() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.verticalGradient()
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect, numberCards = 3)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_radial() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.RadialGradient()
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_shader() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.Brush(
        Brush.sweepGradient(colors = listOf(Color.Transparent, Color.Black)),
      )
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_childTint() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(HazeTint(Color.Magenta.copy(alpha = 0.5f)))
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }

    waitForIdle()
    captureRoot("magenta")

    blurVisualEffect.tints = listOf(HazeTint(Color.Yellow.copy(alpha = 0.5f)))
    waitForIdle()
    captureRoot("yellow")

    blurVisualEffect.tints = listOf(HazeTint(Color.Red.copy(alpha = 0.5f)))
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
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
    }
    var enabled by mutableStateOf(true)

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect, enabled = enabled)
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
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect, shape = roundedCornerShape)
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
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
    }
    var backgroundColors by mutableStateOf(listOf(Color.Blue, Color.Cyan))

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect, backgroundColors = backgroundColors)
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
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(BrushTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_brushTint_mask() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(BrushTint)
      blurRadius = 8.dp
      mask = VerticalMask
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_brushTint_progressive() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(BrushTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.verticalGradient()
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun nested_content() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        Box(Modifier.fillMaxSize()) {
          val outerHazeState = remember { HazeState() }

          Box(Modifier.hazeSource(outerHazeState)) {
            CreditCardSample(visualEffect = blurVisualEffect)
          }

          Box(
            modifier = Modifier
              .hazeEffect(state = outerHazeState) {
                blurEffect {
                  style = HazeBlurDefaults.style(
                    backgroundColor = Color.Blue,
                    tint = DefaultTint,
                    blurRadius = 8.dp,
                  )
                }
              }
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
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardPagerSample(visualEffect = blurVisualEffect, pagerPosition = .25f)
      }
    }
    captureRoot()
  }

  @Test
  fun horizontalPager_half() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardPagerSample(visualEffect = blurVisualEffect, pagerPosition = .5f)
      }
    }
    captureRoot()
  }

  @Test
  fun horizontalPager_three_quarters() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardPagerSample(visualEffect = blurVisualEffect, pagerPosition = .75f, numberCards = 3)
      }
    }
    captureRoot()
  }

  @Test
  fun horizontalPager_one_and_three_quarters() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardPagerSample(visualEffect = blurVisualEffect, pagerPosition = 1.75f, numberCards = 3)
      }
    }
    captureRoot()
  }

  @Test
  fun layerTransformations() = runScreenshotTest {
    var offset by mutableStateOf(DpOffset.Zero)
    val blurVisualEffect = BlurVisualEffect()

    setContent {
      ScreenshotTheme {
        OverlayingContent(visualEffect = blurVisualEffect, topOffset = offset)
      }
    }

    captureRoot("center")

    offset = DpOffset(x = (-128).dp, y = 0.dp)
    captureRoot("left")

    offset = DpOffset(x = 0.dp, y = (-128).dp)
    captureRoot("top")

    offset = DpOffset(x = 128.dp, y = 0.dp)
    captureRoot("right")

    offset = DpOffset(x = 0.dp, y = 128.dp)
    captureRoot("bottom")
  }

  @Test
  fun edges() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      tints = listOf(DefaultTint)
      backgroundColor = Color.Transparent
    }

    setContent {
      ScreenshotTheme {
        ContentAtEdges(visualEffect = blurVisualEffect)
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
