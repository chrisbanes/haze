// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.BlurVisualEffect
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.HazeProgressive
import dev.chrisbanes.haze.blur.HazeStyle
import dev.chrisbanes.haze.blur.LocalHazeStyle
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class HazeContentBlurringScreenshotTest : ScreenshotTest() {
  @Test
  fun creditCard() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_noStyle() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect()
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_blurEnabled() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
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
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
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
          CreditCardContentBlurring(visualEffect = blurVisualEffect)
        }
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_transparentTint() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      blurRadius = 8.dp
      colorEffects = listOf(HazeColorEffect.tint(Color.Transparent, HazeColorEffect.DefaultBlendMode))
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
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
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_mask() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
      mask = VerticalMask
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_alpha() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
      alpha = 0.5f
    }

    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
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
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.horizontalGradient()
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_horiz_preferMask() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.horizontalGradient(preferPerformance = true)
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_vertical() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.verticalGradient()
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_radial() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.RadialGradient()
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_shader() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.Brush(
        Brush.sweepGradient(colors = listOf(Color.Transparent, Color.Black)),
      )
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_childTint() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(
        HazeColorEffect.tint(
          Color.Magenta.copy(alpha = 0.5f),
          HazeColorEffect.DefaultBlendMode,
        ),
      )
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
      }
    }

    waitForIdle()
    captureRoot("magenta")

    blurVisualEffect.colorEffects = listOf(
      HazeColorEffect.tint(
        Color.Yellow.copy(alpha = 0.5f),
        HazeColorEffect.DefaultBlendMode,
      ),
    )
    waitForIdle()
    captureRoot("yellow")

    blurVisualEffect.colorEffects = listOf(
      HazeColorEffect.tint(
        Color.Red.copy(alpha = 0.5f),
        HazeColorEffect.DefaultBlendMode,
      ),
    )
    waitForIdle()
    captureRoot("red")
  }

  @Test
  fun creditCard_sourceContentChange() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
    }
    var backgroundColors by mutableStateOf(listOf(Color.Blue, Color.Cyan))

    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect, backgroundColors = backgroundColors)
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
      colorEffects = listOf(BrushTint)
      blurRadius = 8.dp
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_brushTint_mask() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(BrushTint)
      blurRadius = 8.dp
      mask = VerticalMask
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_brushTint_progressive() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(BrushTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.verticalGradient()
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  companion object {
    val DefaultTint = HazeColorEffect.tint(
      Color.White.copy(alpha = 0.1f),
      HazeColorEffect.DefaultBlendMode,
    )
    val OverrideStyle = HazeStyle(
      colorEffects = listOf(
        HazeColorEffect.tint(
          Color.Red.copy(alpha = 0.5f),
          HazeColorEffect.DefaultBlendMode,
        ),
      ),
    )

    val BrushTint = HazeColorEffect.tint(
      brush = Brush.radialGradient(
        colors = listOf(
          Color.Yellow.copy(alpha = 0.5f),
          Color.Red.copy(alpha = 0.5f),
        ),
      ),
      blendMode = HazeColorEffect.DefaultBlendMode,
    )

    val VerticalMask = Brush.verticalGradient(
      0f to Color.Transparent,
      0.5f to Color.Black,
      1f to Color.Transparent,
    )
  }
}
