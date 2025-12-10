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
import kotlin.test.BeforeTest
import kotlin.test.Test

class LiquidGlassScreenshotTest : ScreenshotTest() {

  @BeforeTest
  fun before() {
    HazeLogger.enabled = true
  }

  @Test
  fun creditCard() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = visualEffect)
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
        CreditCardSample(visualEffect = visualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_multiple() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
      refractionStrength = 0.45f
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = visualEffect, numberCards = 3)
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
        CreditCardSample(visualEffect = visualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_alpha() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
      alpha = 0.85f
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = visualEffect)
      }
    }

    captureRoot()

    visualEffect.alpha = 0.45f
    waitForIdle()
    captureRoot("45")

    visualEffect.alpha = 0.15f
    waitForIdle()
    captureRoot("15")
  }

  @Test
  fun creditCard_edgeSoftness() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
      edgeSoftness = 0.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = visualEffect)
      }
    }

    captureRoot("sharp")

    visualEffect.edgeSoftness = 18.dp
    waitForIdle()
    captureRoot("soft")
  }

  @Test
  fun creditCard_refraction_depth() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
      refractionStrength = 0.25f
      depth = 0.15f
      specularIntensity = 0.35f
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = visualEffect)
      }
    }

    captureRoot("low")

    visualEffect.refractionStrength = 0.6f
    visualEffect.depth = 0.5f
    visualEffect.specularIntensity = 0.7f
    waitForIdle()
    captureRoot("high")
  }

  @Test
  fun creditCard_lightPosition() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
      lightPosition = Offset.Unspecified
      specularIntensity = 0.55f
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = visualEffect)
      }
    }

    captureRoot("center")

    visualEffect.lightPosition = Offset(-120f, -80f)
    waitForIdle()
    captureRoot("topLeft")

    visualEffect.lightPosition = Offset(140f, 120f)
    waitForIdle()
    captureRoot("bottomRight")
  }

  @Test
  fun creditCard_conditional_enabled() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
    }
    var enabled by mutableStateOf(true)

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = visualEffect, enabled = enabled)
      }
    }

    captureRoot("enabled")

    enabled = false
    waitForIdle()
    captureRoot("disabled")

    enabled = true
    waitForIdle()
    captureRoot("re_enabled")
  }

  companion object {
    val DefaultTint = Color.White.copy(alpha = 0.1f)

    val VibrantStyle = LiquidGlassStyle(
      tint = Color(0xFF3F8CFF).copy(alpha = 0.35f),
      refractionStrength = 0.55f,
      specularIntensity = 0.75f,
      depth = 0.4f,
      ambientResponse = 0.8f,
      edgeSoftness = 14.dp,
      lightPosition = Offset(64f, -48f),
    )
  }
}
