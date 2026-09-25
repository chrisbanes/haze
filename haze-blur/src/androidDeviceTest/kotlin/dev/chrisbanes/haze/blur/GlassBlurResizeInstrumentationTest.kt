// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
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
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class)
class GlassBlurResizeInstrumentationTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  @SdkSuppress(minSdkVersion = 31)
  fun glassSamplingResizingBlur_keepsItsInputOnEveryFrame() {
    val page = HazeState()
    val blurredPage = HazeState()
    val blurWidth = mutableStateOf(100.dp)
    val blurStyle = HazeBlurStyle {
      blurRadius(8.dp)
      noiseFactor(0f)
      backgroundColor(Color.Transparent)
      colorEffects(emptyList())
    }
    val blurFactory = object : HazeEffectFactory<BlurConfiguration> {
      lateinit var renderer: BlurVisualEffect

      override fun createRenderer(): HazeEffectRenderer<BlurConfiguration> =
        BlurVisualEffect().also { renderer = it }
    }
    val glassStyle = GlassStyle.clear.then {
      optics(
        GlassOptics(
          refractionStrength = 0f,
          refractionHeightFraction = 0f,
          refractionDisplacement = 0.dp,
          depth = OpticalSizeValue.Fixed(1f),
          blurRadius = OpticalSizeValue.Fixed(0.dp),
          refractionDetailIntensity = 0f,
        ),
      )
      specularIntensity(0f)
      edgeShadow(Color.Transparent)
      ambientResponse(0f)
      backgroundColor(Color.Transparent)
      tint(Color.Transparent)
      edgeSoftness(0.dp)
      chromaticAberrationStrength(0f)
      contrast(0f)
      whitePoint(0f)
      chromaMultiplier(1f)
    }

    composeTestRule.setContent {
      Box(Modifier.size(200.dp).testTag("root")) {
        Box(Modifier.fillMaxSize().hazeSource(page).background(Color.Red))
        Box(Modifier.fillMaxSize().background(Color.Black))
        Box(
          Modifier
            .width(blurWidth.value)
            .fillMaxHeight()
            .hazeSource(blurredPage),
        ) {
          Box(
            Modifier
              .fillMaxSize()
              .hazeEffect(
                factory = blurFactory,
                input = HazeInput.Sources(page),
                style = BlurConfiguration(blurStyle, HazePerformanceMode.Quality),
              ),
          )
        }
        // Cover the blur itself: red must reach the screenshot through Glass sampling it.
        Box(Modifier.fillMaxSize().background(Color.Black))
        Box(
          Modifier
            .fillMaxSize()
            .hazeGlass(
              input = HazeInput.Sources(blurredPage),
              style = glassStyle,
              performanceMode = HazePerformanceMode.Quality,
            ),
        )
      }
    }
    composeTestRule.waitForIdle()
    val blurDelegate = blurFactory.renderer.delegate as RenderEffectBlurVisualEffectDelegate
    // Glass can still show red when a resized Blur replaces its capture layer. Check layer
    // identity as well as pixels to catch the replacement that caused the transient flicker.
    val layerField = blurDelegate.javaClass.getDeclaredField("scaledContentLayer").apply {
      isAccessible = true
    }
    val captureLayer = checkNotNull(layerField.get(blurDelegate))

    fun assertGlassInput(frame: String) {
      val pixels = composeTestRule.onNodeWithTag("root").captureToImage().toPixelMap()
      val pixel = pixels[pixels.width / 4, pixels.height / 2]
      assertThat(pixel.red, "Glass red channel at $frame").isGreaterThan(0.5f)
      assertThat(pixel.red - pixel.blue, "Glass red over blue at $frame").isGreaterThan(0.4f)
    }

    assertGlassInput("initial")
    composeTestRule.mainClock.autoAdvance = false
    for (width in 105..180 step 5) {
      composeTestRule.runOnIdle { blurWidth.value = width.dp }
      composeTestRule.mainClock.advanceTimeByFrame()
      assertGlassInput("${width}dp")
      assertThat(layerField.get(blurDelegate), "Blur capture layer at ${width}dp")
        .isSameInstanceAs(captureLayer)
    }
  }
}
