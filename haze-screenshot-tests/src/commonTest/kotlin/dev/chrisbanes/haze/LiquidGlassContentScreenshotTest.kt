// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.liquidglass.LiquidGlassStyle
import dev.chrisbanes.haze.liquidglass.LiquidGlassVisualEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class LiquidGlassContentScreenshotTest : ScreenshotTest() {

  @Test
  fun creditCard() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = visualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_noTint() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = Color.Transparent
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = visualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_style() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      style = VibrantStyle
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = visualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_drawContentBehind() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
      edgeSoftness = 16.dp
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(
          visualEffect = visualEffect,
          drawContentBehind = true,
        )
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_alpha() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
      alpha = 0.7f
      refractionStrength = 0.45f
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = visualEffect)
      }
    }

    captureRoot("70")

    visualEffect.alpha = 0.4f
    waitForIdle()
    captureRoot("40")

    visualEffect.alpha = 1f
    waitForIdle()
    captureRoot("100")
  }

  @Test
  fun creditCard_lightPosition() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
      specularIntensity = 0.65f
    }
    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(visualEffect = visualEffect)
      }
    }

    captureRoot("center")

    visualEffect.lightPosition = Offset(-96f, -64f)
    waitForIdle()
    captureRoot("topLeft")

    visualEffect.lightPosition = Offset(120f, 80f)
    waitForIdle()
    captureRoot("bottomRight")
  }

  @Test
  fun creditCard_backgroundChange() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
      edgeSoftness = 12.dp
    }
    var backgroundColors by mutableStateOf(listOf(Color.Blue, Color.Cyan))

    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(
          visualEffect = visualEffect,
          backgroundColors = backgroundColors,
        )
      }
    }

    captureRoot("blue")

    backgroundColors = listOf(Color.Magenta, Color(0xFF7CF7C8))
    waitForIdle()
    captureRoot("magenta")

    backgroundColors = listOf(Color(0xFFFBA045), Color(0xFFF25555))
    waitForIdle()
    captureRoot("orange")
  }

  companion object {
    val DefaultTint = Color.White.copy(alpha = 0.1f)

    val VibrantStyle = LiquidGlassStyle(
      tint = Color(0xFF49E1FF).copy(alpha = 0.35f),
      refractionStrength = 0.5f,
      specularIntensity = 0.75f,
      depth = 0.35f,
      ambientResponse = 0.75f,
      edgeSoftness = 12.dp,
      lightPosition = Offset(48f, -32f),
    )
  }
}
