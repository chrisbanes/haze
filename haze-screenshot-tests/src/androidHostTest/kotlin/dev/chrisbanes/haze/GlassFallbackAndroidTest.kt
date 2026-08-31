// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.glass.GlassAccessibilitySettings
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.LocalGlassAccessibilitySettings
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

private const val FALLBACK_PIXEL_TOLERANCE = 1f / 255f
private val FallbackSurfaceSize = DpSize(280.dp, 180.dp)
private val FallbackShape = RoundedCornerShape(0.dp)

@Config(sdk = [28], qualifiers = "w393dp-h698dp-440dpi")
class GlassFallbackAndroidTest : ScreenshotTest() {

  @Test
  fun fallback_zeroSpecularIntensityDrawsNoHighlight() = runScreenshotTest {
    val effect = fallbackEffect(specularIntensity = 0f)
    var enabled by mutableStateOf(false)
    setContent {
      ScreenshotTheme {
        GlassInvariantSample(
          effect = effect,
          performanceMode = HazePerformanceMode.Quality,
          shape = FallbackShape,
          enabled = enabled,
          surfaceSize = FallbackSurfaceSize,
          drawGridLines = false,
        )
      }
    }

    val baseline = captureRootPixels().snapshot()
    enabled = true
    waitForIdle()
    val zero = captureRootPixels().snapshot()

    assertThat(zero.meanAbsoluteDifference(baseline))
      .isLessThanOrEqualTo(FALLBACK_PIXEL_TOLERANCE)
  }

  @Test
  fun fallback_specularIntensityResponseIsMonotonic() = runScreenshotTest {
    val effect = fallbackEffect(specularIntensity = 0f)
    setContent {
      ScreenshotTheme {
        GlassInvariantSample(
          effect = effect,
          performanceMode = HazePerformanceMode.Quality,
          shape = FallbackShape,
          surfaceSize = FallbackSurfaceSize,
          drawGridLines = false,
        )
      }
    }

    val zero = captureRootPixels().snapshot()

    effect.specularIntensity = 0.5f
    waitForIdle()
    val half = captureRootPixels().snapshot()

    effect.specularIntensity = 1f
    waitForIdle()
    val full = captureRootPixels().snapshot()

    val halfResponse = half.meanAbsoluteDifference(zero)
    val fullResponse = full.meanAbsoluteDifference(zero)
    assertThat(halfResponse).isGreaterThan(0f)
    assertThat(fullResponse).isGreaterThan(halfResponse)
  }

  @Test
  fun fallback_defaultLightPositionMatchesExplicitCenter() = runScreenshotTest {
    val effect = fallbackEffect(specularIntensity = 1f)
    setContent {
      ScreenshotTheme {
        GlassInvariantSample(
          effect = effect,
          performanceMode = HazePerformanceMode.Quality,
          shape = FallbackShape,
          surfaceSize = FallbackSurfaceSize,
          drawGridLines = false,
        )
      }
    }

    val unspecified = captureRootPixels().snapshot()
    effect.lightPosition = Alignment.Center
    waitForIdle()
    val explicitCenter = captureRootPixels().snapshot()

    assertThat(unspecified.changedPixelRatio(explicitCenter)).isEqualTo(0f)
  }

  @Test
  fun fallback_builtInStylesRemainVisuallyDistinct() = runScreenshotTest {
    var style by mutableStateOf(GlassStyle.regular)
    setContent {
      ScreenshotTheme {
        FallbackBuiltInStyleSample(style)
      }
    }

    val regular = captureRootPixels().snapshot()
    captureRoot("regular")

    style = GlassStyle.clear
    waitForIdle()
    val clear = captureRootPixels().snapshot()
    captureRoot("clear")

    assertThat(regular.changedPixelRatio(clear)).isGreaterThan(0.001f)
  }

  @Test
  fun fallback_showBordersDrawsEdgeForHardEdgeStyle() = runScreenshotTest {
    var settings by mutableStateOf(GlassAccessibilitySettings())
    val style = GlassStyle {
      tint(Color.Transparent)
      edgeShadow(Color.Transparent)
      edgeSoftness(0.dp)
      specularIntensity(0f)
    }
    setContent {
      ScreenshotTheme {
        CompositionLocalProvider(LocalGlassAccessibilitySettings provides settings) {
          FallbackBuiltInStyleSample(style)
        }
      }
    }

    val hidden = captureRootPixels().snapshot()
    settings = GlassAccessibilitySettings(showBorders = true)
    waitForIdle()
    val visible = captureRootPixels().snapshot()
    captureRoot("visible")

    assertThat(visible.changedPixelRatio(hidden)).isGreaterThan(0.001f)
  }

  @Test
  fun fallback_roundedPressedLightingDrawsOverOpaqueContent_api28() {
    assertRoundedPressedLightingDrawsOverOpaqueContent()
  }

  @Test
  @Config(sdk = [32])
  fun fallback_roundedPressedLightingDrawsOverOpaqueContent_api32() {
    assertRoundedPressedLightingDrawsOverOpaqueContent()
  }

  private fun assertRoundedPressedLightingDrawsOverOpaqueContent() = runScreenshotTest {
    val interactionSource = MutableInteractionSource()
    val effect = GlassTestConfiguration().apply {
      tint = Color.Transparent
      optics = GlassOptics(refractionStrength = 0f, depth = OpticalSizeValue.Fixed(0f), blurRadius = OpticalSizeValue.Fixed(0.dp))
      specularIntensity = 0f
      ambientResponse = 0f
      edgeSoftness = 0.dp
      shape = RoundedCornerShape(32.dp)
      pressed { lightingIntensity(1f) }
      this.interactionSource = interactionSource
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setContent {
      ScreenshotTheme {
        FallbackOpaqueContentSample(effect)
      }
    }

    val unlit = captureRootPixels().snapshot()
    effect.specularIntensity = 1f
    waitForIdle()
    val rim = captureRootPixels().snapshot()

    assertThat(rim.changedPixelRatio(unlit)).isGreaterThan(0.001f)

    interactionSource.tryEmit(PressInteraction.Press(Offset.Unspecified))
    waitForIdle()
    val pressed = captureRootPixels().snapshot()

    assertThat(pressed.changedPixelRatio(rim)).isGreaterThan(0.001f)
  }
}

@Composable
private fun FallbackOpaqueContentSample(effect: GlassTestConfiguration) {
  val hazeState = rememberHazeState()
  val shape = RoundedCornerShape(32.dp)
  Box(Modifier.fillMaxSize().background(Color.DarkGray)) {
    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
      drawRect(Color.DarkGray)
    }
    Box(
      Modifier
        .align(Alignment.Center)
        .size(FallbackSurfaceSize)
        .hazeGlass(
          input = HazeInput.Sources(hazeState),
          configuration = effect,
          performanceMode = HazePerformanceMode.Quality,
        )
        .clip(shape)
        .background(Color.Black),
    )
  }
}

@Composable
private fun FallbackBuiltInStyleSample(style: GlassStyle) {
  val hazeState = rememberHazeState()
  Box(Modifier.fillMaxSize().background(Color.DarkGray)) {
    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
      drawRect(Color(0xFF102030))
      repeat(14) { index ->
        val offset = index * size.width / 14f
        drawLine(Color.Cyan, Offset(offset, 0f), Offset(offset, size.height), 3f)
      }
    }
    Box(
      Modifier
        .align(Alignment.Center)
        .size(FallbackSurfaceSize)
        .hazeGlass(
          input = HazeInput.Sources(hazeState),
          style = style,
          performanceMode = HazePerformanceMode.Quality,
        ),
    )
  }
}

private fun fallbackEffect(specularIntensity: Float): GlassTestConfiguration = GlassTestConfiguration().apply {
  tint = Color.Transparent
  optics = GlassOptics(refractionStrength = 0f, depth = OpticalSizeValue.Fixed(0f), blurRadius = OpticalSizeValue.Fixed(0.dp))
  this.specularIntensity = specularIntensity
  ambientResponse = 0f
  edgeSoftness = 0.dp
  shape = FallbackShape
}
