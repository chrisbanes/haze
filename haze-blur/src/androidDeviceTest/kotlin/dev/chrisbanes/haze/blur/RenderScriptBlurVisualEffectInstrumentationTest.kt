// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class RenderScriptBlurVisualEffectInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun scaledSubpixelBlurRadius_animatesToZero() {
    assumeTrue("RenderScript backend test requires API 30", Build.VERSION.SDK_INT == 30)
    val hazeState = HazeState()
    val targetRadius = mutableStateOf(0.5.dp)
    val sourceColor = mutableStateOf(Color.Blue)

    composeTestRule.setContent {
      val blurRadius by animateDpAsState(
        targetValue = targetRadius.value,
        animationSpec = tween(durationMillis = 100),
        label = "blurRadius",
      )

      Box(
        Modifier
          .size(100.dp)
          .hazeSource(hazeState)
          .background(sourceColor.value),
      )
      Box(
        Modifier
          .testTag(BLUR_EFFECT_TAG)
          .size(100.dp)
          .background(Color.Black)
          .hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeBlurStyle {
              blurEnabled(true)
              blurRadius(blurRadius)
              backgroundColor(Color.Transparent)
              colorEffects(emptyList())
              fallbackColorEffect(null)
            },
            performanceMode = HazePerformanceMode.Performance,
          ),
      )
    }
    composeTestRule.waitForIdle()
    assertThat(Build.VERSION.SDK_INT).isEqualTo(30)
    val initial = composeTestRule.captureCenterPixel()
    assertThat(initial.blue, "initial RenderScript output").isGreaterThan(initial.red)

    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.runOnIdle { sourceColor.value = Color.Red }
    val updated = composeTestRule.awaitCenterPixel { it.red > it.blue }
    assertThat(updated.red, "positive subpixel RenderScript output").isGreaterThan(updated.blue)

    composeTestRule.runOnIdle { targetRadius.value = 0.dp }
    repeat(8) {
      composeTestRule.mainClock.advanceTimeByFrame()
      composeTestRule.waitForIdle()
    }
    composeTestRule.runOnIdle { sourceColor.value = Color.Green }
    val zeroRadius = composeTestRule.awaitCenterPixel {
      it.green > it.red && it.green > it.blue
    }
    assertThat(zeroRadius.green, "zero-radius copy output").isGreaterThan(zeroRadius.red)
    assertThat(zeroRadius.green, "zero-radius copy output").isGreaterThan(zeroRadius.blue)
    composeTestRule.onNodeWithTag(BLUR_EFFECT_TAG).assertIsDisplayed()
  }

  private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.captureCenterPixel(): Color {
    val pixels = onNodeWithTag(BLUR_EFFECT_TAG).captureToImage().toPixelMap()
    return pixels[pixels.width / 2, pixels.height / 2]
  }

  private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.awaitCenterPixel(
    predicate: (Color) -> Boolean,
  ): Color {
    var pixel = captureCenterPixel()
    repeat(MAX_PIXEL_WAIT_FRAMES) {
      if (predicate(pixel)) return pixel
      mainClock.advanceTimeByFrame()
      waitForIdle()
      pixel = captureCenterPixel()
    }
    return pixel
  }

  private companion object {
    const val BLUR_EFFECT_TAG = "render_script_blur_effect"
    const val MAX_PIXEL_WAIT_FRAMES = 30
  }
}
