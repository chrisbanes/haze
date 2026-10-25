// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeFeatureFlags
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SdkSuppress(maxSdkVersion = 36)
@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class)
class BlurBackdropFallbackInstrumentationTest {

  private var previousPlatformBackdropEnabled = false

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setUp() {
    previousPlatformBackdropEnabled = HazeFeatureFlags.isPlatformBackdropEnabled
    HazeFeatureFlags.isPlatformBackdropEnabled = false
  }

  @After
  fun tearDown() {
    HazeFeatureFlags.isPlatformBackdropEnabled = previousPlatformBackdropEnabled
  }

  @Test
  fun unsupportedPlatform_usesSourcesFallback() {
    val fallbackState = HazeState()

    composeTestRule.setContent {
      Box(Modifier.fillMaxSize().background(Color.White)) {
        Box(
          Modifier
            .align(Alignment.Center)
            .size(width = 200.dp, height = 100.dp)
            .hazeSource(fallbackState)
            .background(Color.Black),
        ) {
          Box(
            Modifier
              .align(Alignment.CenterEnd)
              .size(width = 100.dp, height = 100.dp)
              .background(Color.White),
          )
        }
        Box(
          Modifier
            .align(Alignment.Center)
            .size(width = 200.dp, height = 100.dp)
            .testTag(EFFECT_TAG)
            .hazeBlur(
              input = HazeInput.Backdrop(fallbackState),
              style = HazeBlurStyle {
                blurEnabled(true)
                blurRadius(14.dp)
                noiseFactor(0f)
                colorEffects(emptyList())
              },
            ),
        )
      }
    }
    composeTestRule.waitForIdle()

    // RenderScript (API 30) delivers its first blurred frame asynchronously, after waitForIdle().
    var samples = captureSamples()
    repeat(39) {
      if (samples.softensBothEdges()) return@repeat // Settled; remaining iterations are no-ops.
      CountDownLatch(1).await(100, TimeUnit.MILLISECONDS)
      samples = captureSamples()
    }
    val (blackInterior, blackNearEdge, whiteNearEdge, whiteInterior) = samples

    assertThat(blackNearEdge - blackInterior, "Fallback softens the black side")
      .isGreaterThan(SOFTEN_THRESHOLD)
    assertThat(whiteInterior - whiteNearEdge, "Fallback softens the white side")
      .isGreaterThan(SOFTEN_THRESHOLD)
  }

  private data class EdgeSamples(
    val blackInterior: Float,
    val blackNearEdge: Float,
    val whiteNearEdge: Float,
    val whiteInterior: Float,
  ) {
    fun softensBothEdges() = blackNearEdge - blackInterior > SOFTEN_THRESHOLD &&
      whiteInterior - whiteNearEdge > SOFTEN_THRESHOLD
  }

  private fun captureSamples(): EdgeSamples {
    val pixels = composeTestRule.onNodeWithTag(EFFECT_TAG).captureToImage().toPixelMap()
    val centerX = pixels.width / 2
    val centerY = pixels.height / 2
    return EdgeSamples(
      blackInterior = pixels[centerX - 60, centerY].red,
      blackNearEdge = pixels[centerX - 3, centerY].red,
      whiteNearEdge = pixels[centerX + 3, centerY].red,
      whiteInterior = pixels[centerX + 60, centerY].red,
    )
  }

  private companion object {
    const val SOFTEN_THRESHOLD = 0.05f
    const val EFFECT_TAG = "effect"
  }
}
