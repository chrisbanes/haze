// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.abs
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [31, 32])
class ProgressiveBlurAndroidScreenshotTest : ScreenshotTest() {

  @Test
  fun solidRedBrushTint_matchesSolidRedColorTint() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        Row(Modifier.fillMaxSize()) {
          TintCell(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colorEffect = HazeColorEffect.tint(
              Brush.verticalGradient(0f to Color.Red, 1f to Color.Red),
            ),
          )
          TintCell(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colorEffect = HazeColorEffect.tint(Color.Red),
          )
        }
      }
    }

    val pixels = captureRootPixels()
    val brushTintPixel = pixels[pixels.width / 4, pixels.height / 2]
    val colorTintPixel = pixels[pixels.width * 3 / 4, pixels.height / 2]

    assertThat(brushTintPixel.red, "brush tint red").isCloseTo(colorTintPixel.red, 0.01f)
    assertThat(brushTintPixel.green, "brush tint green").isCloseTo(colorTintPixel.green, 0.01f)
    assertThat(brushTintPixel.blue, "brush tint blue").isCloseTo(colorTintPixel.blue, 0.01f)
    assertThat(brushTintPixel.alpha, "brush tint alpha").isCloseTo(colorTintPixel.alpha, 0.01f)
    assertThat(brushTintPixel.red, "brush tint preserves red").isGreaterThan(0.9f)
  }

  @Test
  fun switchedProgressive_matchesFreshAttachment() = assertTransitionMatchesFreshAttachment(
    initialStyle = ordinaryStyle(alpha = 1f),
    finalStyle = layeredStyle(alpha = 1f),
  )

  @Test
  fun switchedProgressive_withAlpha_matchesFreshAttachment() = assertTransitionMatchesFreshAttachment(
    initialStyle = ordinaryStyle(alpha = 0.5f),
    finalStyle = layeredStyle(alpha = 0.5f),
  )

  @Test
  fun maskedToLayered_matchesFreshAttachment() = assertTransitionMatchesFreshAttachment(
    initialStyle = maskedStyle(alpha = 0.5f),
    finalStyle = layeredStyle(alpha = 0.5f),
  )

  @Test
  fun layeredToMasked_matchesFreshAttachment() = assertTransitionMatchesFreshAttachment(
    initialStyle = layeredStyle(alpha = 0.5f),
    finalStyle = maskedStyle(alpha = 0.5f),
  )

  @Test
  fun layeredToOrdinary_matchesFreshAttachment() = assertTransitionMatchesFreshAttachment(
    initialStyle = layeredStyle(alpha = 0.5f),
    finalStyle = ordinaryStyle(alpha = 0.5f),
  )

  @Test
  fun progressiveBlur_contentInputRefreshesWhenBackgroundChanges() = runScreenshotTest {
    val style = HazeBlurStyle {
      blurRadius(48.dp)
      noiseFactor(0f)
      progressive(HazeProgressive.verticalGradient())
    }
    val upperColor = mutableStateOf(Color.Black)
    val lowerColor = mutableStateOf(Color.White)

    setContent {
      ScreenshotTheme {
        Column(
          Modifier
            .fillMaxSize()
            .hazeBlur(
              input = HazeInput.Content,
              style = style,
              performanceMode = HazePerformanceMode.Quality,
            ),
        ) {
          Box(
            Modifier
              .weight(1f)
              .fillMaxSize()
              .background(upperColor.value),
          )
          Box(
            Modifier
              .weight(1f)
              .fillMaxSize()
              .background(lowerColor.value),
          )
        }
      }
    }

    waitForIdle()
    captureRoot("initial")

    upperColor.value = Color.Blue
    lowerColor.value = Color.Red
    waitForIdle()
    captureRoot("changed")

    val pixels = captureRootPixels()
    assertThat(pixels[pixels.width / 2, pixels.height / 4].blue, "updated upper background")
      .isGreaterThan(0.8f)
    assertThat(pixels[pixels.width / 2, pixels.height * 3 / 4].red, "updated lower background")
      .isGreaterThan(0.8f)
  }

  @Test
  fun progressiveBlur_contentInputKeepsCoverageAtBothEndsOfTheAxis() = runScreenshotTest {
    val style = HazeBlurStyle {
      blurRadius(48.dp)
      noiseFactor(0f)
      progressive(
        HazeProgressive.horizontalGradient(
          startIntensity = 0.5f,
          endIntensity = 0.5f,
        ),
      )
    }

    setContent {
      ScreenshotTheme {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.Black),
        ) {
          Column(
            Modifier
              .fillMaxSize()
              .hazeBlur(
                input = HazeInput.Content,
                style = style,
                performanceMode = HazePerformanceMode.Quality,
              ),
          ) {
            Box(
              Modifier
                .weight(1f)
                .fillMaxSize()
                .background(Color.Black),
            )
            Box(
              Modifier
                .weight(1f)
                .fillMaxSize()
                .background(Color.White),
            )
          }
        }
      }
    }

    val pixels = captureRootPixels()
    listOf(pixels.width / 4, pixels.width * 3 / 4).forEach { x ->
      val farUpper = pixels[x, pixels.height / 4].luminance()
      val farLower = pixels[x, pixels.height * 3 / 4].luminance()
      assertThat(farUpper, "upper source at x=$x").isLessThan(0.2f)
      assertThat(farLower, "lower source at x=$x").isGreaterThan(0.8f)

      listOf(pixels.height / 2 - 4, pixels.height / 2 + 4).forEach { y ->
        val luminance = pixels[x, y].luminance()
        val label = "progressive transition at ($x, $y) on ${pixels.width}x${pixels.height}"
        assertThat(luminance, label).isGreaterThan(0.02f)
        assertThat(luminance, label).isLessThan(0.98f)
      }
    }
  }

  private fun assertTransitionMatchesFreshAttachment(
    initialStyle: HazeBlurStyle,
    finalStyle: HazeBlurStyle,
  ) = runScreenshotTest {
    val style = mutableStateOf(initialStyle)
    val attachment = mutableStateOf(0)

    setContent {
      ScreenshotTheme {
        key(attachment.value) {
          TransitionBlurContent(style = style.value)
        }
      }
    }

    waitForIdle()
    captureRootPixels()

    style.value = finalStyle
    waitForIdle()
    val transitioned = captureRootPixels().snapshot()

    attachment.value++
    waitForIdle()
    val fresh = captureRootPixels().snapshot()

    assertVisibleBlur(fresh)
    assertThat(
      transitioned.maxChannelDifference(fresh),
      "transitioned and fresh $finalStyle output",
    ).isLessThanOrEqualTo(0.01f)
  }
}

private fun ordinaryStyle(alpha: Float): HazeBlurStyle = HazeBlurStyle {
  blurRadius(48.dp)
  noiseFactor(0f)
  alpha(alpha)
}

private fun layeredStyle(alpha: Float): HazeBlurStyle = HazeBlurStyle {
  blurRadius(48.dp)
  noiseFactor(0f)
  alpha(alpha)
  progressive(
    HazeProgressive.verticalGradient(
      startIntensity = 0.5f,
      endIntensity = 0.5f,
    ),
  )
}

private fun maskedStyle(alpha: Float): HazeBlurStyle = HazeBlurStyle {
  blurRadius(48.dp)
  noiseFactor(0f)
  alpha(alpha)
  progressive(
    HazeProgressive.RadialGradient(
      centerIntensity = 0.5f,
      radiusIntensity = 0.5f,
    ),
  )
}

@Composable
private fun TransitionBlurContent(style: HazeBlurStyle) {
  Box(
    Modifier
      .fillMaxSize()
      .background(Color.Black),
  ) {
    Column(
      Modifier
        .fillMaxSize()
        .hazeBlur(
          input = HazeInput.Content,
          style = style,
          performanceMode = HazePerformanceMode.Quality,
        ),
    ) {
      Box(
        Modifier
          .weight(1f)
          .fillMaxSize()
          .background(Color.Black),
      )
      Box(
        Modifier
          .weight(1f)
          .fillMaxSize()
          .background(Color.White),
      )
    }
  }
}

private fun assertVisibleBlur(pixels: PixelSnapshot) {
  val boundary = pixels[pixels.width / 2, pixels.height / 2 - 4].luminance()
  assertThat(boundary, "visible blur at source boundary").isGreaterThan(0.005f)
  assertThat(boundary, "visible blur at source boundary").isLessThan(0.98f)
}

private fun PixelSnapshot.maxChannelDifference(other: PixelSnapshot): Float {
  require(width == other.width && height == other.height) {
    "Cannot compare ${width}x$height to ${other.width}x${other.height}"
  }
  return colors.indices.maxOf { index ->
    val first = colors[index]
    val second = other.colors[index]
    maxOf(
      abs(first.red - second.red),
      abs(first.green - second.green),
      abs(first.blue - second.blue),
      abs(first.alpha - second.alpha),
    )
  }
}

@Composable
private fun TintCell(
  modifier: Modifier,
  colorEffect: HazeColorEffect,
) {
  Box(
    modifier.hazeBlur(
      input = HazeInput.Content,
      style = HazeBlurStyle {
        blurRadius(0.dp)
        noiseFactor(0f)
        colorEffects(listOf(colorEffect))
        progressive(
          HazeProgressive.horizontalGradient(
            startIntensity = 0.5f,
            endIntensity = 0.5f,
          ),
        )
      },
      performanceMode = HazePerformanceMode.Quality,
    ),
  ) {
    Box(Modifier.fillMaxSize().background(Color.White))
  }
}
