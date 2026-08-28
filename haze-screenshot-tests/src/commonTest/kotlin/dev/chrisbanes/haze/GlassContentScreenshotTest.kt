// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.SurfaceProfile
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class GlassContentScreenshotTest : ScreenshotTest() {

  @Test
  fun creditCard_builtInStylesRemainVisuallyDistinct() = runScreenshotTest {
    var style by mutableStateOf(GlassStyle.regular)
    setContent {
      ScreenshotTheme {
        CreditCardGlassContentBlurring(style = style)
      }
    }

    waitForIdle()
    val regular = captureRootPixels().snapshot()
    captureRoot("regular")

    style = GlassStyle.clear
    waitForIdle()
    val clear = captureRootPixels().snapshot()
    // Fixed refraction amplifies Skia's platform-specific pixel variance.
    captureRoot("clear", unmatchedPixelThreshold = 0.01f)

    assertThat(regular.changedPixelRatio(clear)).isGreaterThan(0.01f)
  }

  @Test
  fun creditCard() = runScreenshotTest {
    val style = GlassStyle { tint(DefaultTint) }
    setContent {
      ScreenshotTheme {
        CreditCardGlassContentBlurring(style = style)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_noTint() = runScreenshotTest {
    val style = GlassStyle { tint(Color.Transparent) }
    setContent {
      ScreenshotTheme {
        CreditCardGlassContentBlurring(style = style)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_style() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardGlassContentBlurring(style = VibrantStyle)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_alpha() = runScreenshotTest {
    var style by mutableStateOf(
      GlassStyle {
        tint(DefaultTint)
        alpha(0.7f)
        optics(refractionStrength = 0.45f)
      },
    )
    setContent {
      ScreenshotTheme {
        CreditCardGlassContentBlurring(style = style)
      }
    }

    captureRoot("70")

    style = style.then { alpha(0.4f) }
    waitForIdle()
    captureRoot("40")

    style = style.then { alpha(1f) }
    waitForIdle()
    captureRoot("100")
  }

  @Test
  fun creditCard_lightPosition() = runScreenshotTest {
    var style by mutableStateOf(
      GlassStyle {
        tint(DefaultTint)
        specularIntensity(0.65f)
      },
    )
    setContent {
      ScreenshotTheme {
        CreditCardGlassContentBlurring(style = style)
      }
    }

    captureRoot("center")

    style = style.then { lightPosition(exactLightAlignment(-96, -64)) }
    waitForIdle()
    captureRoot("topLeft")

    style = style.then { lightPosition(exactLightAlignment(120, 80)) }
    waitForIdle()
    captureRoot("bottomRight")
  }

  @Test
  fun creditCard_backgroundChange() = runScreenshotTest {
    val style = GlassStyle {
      tint(DefaultTint)
      edgeSoftness(12.dp)
    }
    var backgroundColors by mutableStateOf(listOf(Color.Blue, Color.Cyan))

    setContent {
      ScreenshotTheme {
        CreditCardGlassContentBlurring(
          style = style,
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

  @Test
  fun creditCard_shape_refractionHeight() = runScreenshotTest {
    var style by mutableStateOf(
      GlassStyle {
        tint(DefaultTint)
        optics(refractionHeightFraction = 0.3f, depth = 0.45f)
        specularIntensity(0.6f)
        edgeSoftness(10.dp)
        shape(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
      },
    )

    setContent {
      ScreenshotTheme {
        CreditCardGlassContentBlurring(
          style = style,
          backgroundColors = listOf(Color(0xFF1E88E5), Color(0xFF00ACC1)),
        )
      }
    }

    captureRoot("rounded")

    style = style.then { optics(refractionHeightFraction = 0.16f, depth = 0.45f) }
    waitForIdle()
    captureRoot("shallow")
  }

  @Test
  fun creditCard_chromatic() = runScreenshotTest {
    val visualEffect = GlassTestConfiguration().apply {
      tint = DefaultTint
      optics = GlassOptics(refractionStrength = 0.8f, depth = GlassOptics.SizeValue.Fixed(0.5f))
      chromaticAberrationStrength = 0.22f
      ambientResponse = 0.7f
      edgeSoftness = 14.dp
      shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    }

    setContent {
      ScreenshotTheme {
        CreditCardGlassContentBlurring(
          style = visualEffect.resolvedStyle,
          backgroundColors = listOf(Color(0xFF7E57C2), Color(0xFF26C6DA)),
        )
      }
    }

    captureRoot()
  }

  @Test
  fun creditCard_surfaceProfile() = runScreenshotTest {
    val visualEffect = GlassTestConfiguration().apply {
      tint = DefaultTint
      optics = GlassOptics(refractionHeightFraction = 0.28f, depth = GlassOptics.SizeValue.Fixed(0.4f))
      specularIntensity = 0.5f
      shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
      surfaceProfile = SurfaceProfile.Squircle
    }

    setContent {
      ScreenshotTheme {
        CreditCardGlassContentBlurring(
          style = visualEffect.resolvedStyle,
          backgroundColors = listOf(Color(0xFF1E88E5), Color(0xFF00ACC1)),
        )
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
    val visualEffect = GlassTestConfiguration().apply {
      tint = DefaultTint
      optics = GlassOptics(refractionStrength = 0.8f, depth = GlassOptics.SizeValue.Fixed(0.45f))
      chromaticAberrationStrength = 0.3f
      edgeSoftness = 14.dp
      shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
      chromaticAberrationMode = ChromaticAberrationMode.Simple
    }

    setContent {
      ScreenshotTheme {
        CreditCardGlassContentBlurring(
          style = visualEffect.resolvedStyle,
          backgroundColors = listOf(Color(0xFF7E57C2), Color(0xFF26C6DA)),
        )
      }
    }

    captureRoot("simple", unmatchedPixelThreshold = 0.01f)

    visualEffect.chromaticAberrationMode = ChromaticAberrationMode.Full
    waitForIdle()
    captureRoot("full")
  }

  companion object {
    val DefaultTint = Color.White.copy(alpha = 0.1f)

    val VibrantStyle = GlassStyle {
      tint(Color(0xFF49E1FF).copy(alpha = 0.35f))
      optics(
        GlassOptics(
          refractionStrength = 0.5f,
          depth = GlassOptics.SizeValue.Fixed(0.35f),
        ),
      )
      specularIntensity(0.75f)
      ambientResponse(0.75f)
      lightPosition(exactLightAlignment(48, -32))
      edgeSoftness(12.dp)
    }
  }
}
