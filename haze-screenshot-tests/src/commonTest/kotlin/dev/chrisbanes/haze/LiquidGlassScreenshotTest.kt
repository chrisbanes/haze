// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.liquidglass.ChromaticAberrationMode
import dev.chrisbanes.haze.liquidglass.LiquidGlassStyle
import dev.chrisbanes.haze.liquidglass.LiquidGlassVisualEffect
import dev.chrisbanes.haze.liquidglass.SurfaceProfile
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

  @Test
  fun creditCard_shape_refractionHeight() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
      refractionStrength = 0.7f
      refractionHeight = 0.32f
      depth = 0.45f
      specularIntensity = 0.6f
      shape = RoundedCornerShape(24.dp)
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = visualEffect, shape = RoundedCornerShape(24.dp))
      }
    }

    captureRoot("rounded")

    visualEffect.refractionHeight = 0.18f
    waitForIdle()
    captureRoot("shallow")
  }

  @Test
  fun creditCard_chromaticAberration() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
      refractionStrength = 0.8f
      chromaticAberrationStrength = 0.0f
      depth = 0.4f
      edgeSoftness = 14.dp
      shape = RoundedCornerShape(20.dp)
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = visualEffect, shape = RoundedCornerShape(20.dp))
      }
    }

    captureRoot("off")

    visualEffect.chromaticAberrationStrength = 0.24f
    waitForIdle()
    captureRoot("on")
  }

  @Test
  fun creditCard_surfaceProfile() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
      refractionStrength = 0.7f
      refractionHeight = 0.28f
      depth = 0.4f
      specularIntensity = 0.5f
      shape = RoundedCornerShape(24.dp)
      surfaceProfile = SurfaceProfile.Squircle
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = visualEffect, shape = RoundedCornerShape(24.dp))
      }
    }

    captureRoot("squircle")

    visualEffect.surfaceProfile = SurfaceProfile.Concave
    waitForIdle()
    captureRoot("concave")

    visualEffect.surfaceProfile = SurfaceProfile.Lip
    waitForIdle()
    captureRoot("lip")
  }

  @Test
  fun creditCard_chromaticAberrationMode() = runScreenshotTest {
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = DefaultTint
      refractionStrength = 0.8f
      chromaticAberrationStrength = 0.3f
      depth = 0.45f
      edgeSoftness = 14.dp
      shape = RoundedCornerShape(20.dp)
      chromaticAberrationMode = ChromaticAberrationMode.Simple
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = visualEffect, shape = RoundedCornerShape(20.dp))
      }
    }

    captureRoot("simple")

    visualEffect.chromaticAberrationMode = ChromaticAberrationMode.Full
    waitForIdle()
    captureRoot("full")
  }

  @Test
  fun creditCard_shape_change_sameSize() = runScreenshotTest {
    var shape by mutableStateOf(RoundedCornerShape(24.dp))

    setContent {
      ScreenshotTheme {
        SimpleLiquidGlassCard(
          tint = DefaultTint,
          shape = shape,
        )
      }
    }

    captureRoot("24dp")

    shape = RoundedCornerShape(8.dp)
    waitForIdle()
    captureRoot("8dp")
  }

  @Test
  fun creditCard_shape_rtl_asymmetric() = runScreenshotTest {
    var layoutDirection by mutableStateOf(LayoutDirection.Ltr)
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 0.dp, bottomEnd = 24.dp, bottomStart = 0.dp)

    setContent {
      ScreenshotTheme {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
          SimpleLiquidGlassCard(
            tint = DefaultTint,
            shape = shape,
          )
        }
      }
    }

    captureRoot("ltr")

    layoutDirection = LayoutDirection.Rtl
    waitForIdle()
    captureRoot("rtl")
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

@Composable
private fun SimpleLiquidGlassCard(
  tint: Color,
  shape: RoundedCornerShape,
) {
  val hazeState = remember { HazeState() }
  val effect = remember(tint, shape) {
    LiquidGlassVisualEffect().apply {
      this.tint = tint
      this.shape = shape
    }
  }

  Box(Modifier.fillMaxSize()) {
    Spacer(
      Modifier
        .fillMaxSize()
        .hazeSource(state = hazeState, zIndex = 0f)
        .background(brush = Brush.linearGradient(listOf(Color.Blue, Color.Cyan))),
    )

    Box(
      Modifier
        .align(Alignment.Center)
        .size(250.dp, 150.dp)
        .hazeSource(hazeState, zIndex = 1f)
        .hazeEffect(state = hazeState) {
          visualEffect = effect
        },
    )
  }
}
