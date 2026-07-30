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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
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
          inputScale = HazeInputScale.None,
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
          inputScale = HazeInputScale.None,
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
  fun fallback_unspecifiedLightPositionMatchesExplicitCenter() = runScreenshotTest {
    val effect = fallbackEffect(specularIntensity = 1f)
    var materialCenter = Offset.Unspecified
    setContent {
      val density = LocalDensity.current
      SideEffect {
        materialCenter = with(density) {
          Offset(FallbackSurfaceSize.width.toPx() / 2f, FallbackSurfaceSize.height.toPx() / 2f)
        }
      }
      ScreenshotTheme {
        GlassInvariantSample(
          effect = effect,
          inputScale = HazeInputScale.None,
          shape = FallbackShape,
          surfaceSize = FallbackSurfaceSize,
          drawGridLines = false,
        )
      }
    }

    val unspecified = captureRootPixels().snapshot()
    check(materialCenter != Offset.Unspecified)
    effect.lightPosition = materialCenter
    waitForIdle()
    val explicitCenter = captureRootPixels().snapshot()

    assertThat(unspecified.changedPixelRatio(explicitCenter)).isEqualTo(0f)
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
      optics = GlassOptics.Absolute(refractionStrength = 0f, depth = 0f, blurRadius = 0.dp)
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
          sampling = HazeSampling.FullResolution,
        )
        .clip(shape)
        .background(Color.Black),
    )
  }
}

private fun fallbackEffect(specularIntensity: Float): GlassTestConfiguration = GlassTestConfiguration().apply {
  tint = Color.Transparent
  optics = GlassOptics.Absolute(refractionStrength = 0f, depth = 0f, blurRadius = 0.dp)
  this.specularIntensity = specularIntensity
  ambientResponse = 0f
  edgeSoftness = 0.dp
  shape = FallbackShape
}
