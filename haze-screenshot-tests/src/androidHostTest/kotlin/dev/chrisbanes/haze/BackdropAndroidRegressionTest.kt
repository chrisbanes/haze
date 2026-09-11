// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Exercises native backdrop rendering on Robolectric 4.17's SDK 37 runtime.
 */
@Config(sdk = [37])
class BackdropAndroidRegressionTest : ScreenshotTest() {
  private var previousBackdropEnabled = false

  @Before
  fun setUpBackdrop() {
    previousBackdropEnabled = HazeFeatureFlags.isPlatformBackdropEnabled
    HazeFeatureFlags.isPlatformBackdropEnabled = true
  }

  @After
  fun tearDownBackdrop() {
    HazeFeatureFlags.isPlatformBackdropEnabled = previousBackdropEnabled
  }

  @Test
  fun blurBackdrop_blursEarlierPixelsWithoutFallbackSources() {
    assertBackdropEdge { input ->
      hazeBlur(
        input = input,
        style = HazeBlurStyle {
          blurRadius(14.dp)
          noiseFactor(0f)
          colorEffects(emptyList())
        },
      )
    }
  }

  @Test
  @Config(sdk = [36])
  fun blurBackdrop_unsupportedSdk_leavesEarlierPixelsSharpWithoutFallbackSources() {
    assertBackdropEdge(expectBlur = false) { input ->
      hazeBlur(
        input = input,
        style = HazeBlurStyle {
          blurRadius(14.dp)
          noiseFactor(0f)
          colorEffects(emptyList())
        },
      )
    }
  }

  @Test
  fun blurBackdrop_disabledFlag_leavesEarlierPixelsSharpWithoutFallbackSources() {
    HazeFeatureFlags.isPlatformBackdropEnabled = false
    assertBackdropEdge(expectBlur = false) { input ->
      hazeBlur(
        input = input,
        style = HazeBlurStyle {
          blurRadius(14.dp)
          noiseFactor(0f)
          colorEffects(emptyList())
        },
      )
    }
  }

  @Test
  fun glassBackdrop_blursEarlierPixelsWithoutFallbackSources() {
    assertBackdropEdge { input ->
      hazeGlass(
        input = input,
        style = GlassStyle.regular.then {
          optics(
            GlassOptics(
              refractionStrength = 0f,
              refractionHeightFraction = 0f,
              refractionDisplacement = 0.dp,
              depth = OpticalSizeValue.Fixed(1f),
              blurRadius = OpticalSizeValue.Fixed(14.dp),
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
        },
      )
    }
  }

  private fun assertBackdropEdge(
    expectBlur: Boolean = true,
    effect: Modifier.(HazeInput.Backdrop) -> Modifier,
  ) = runScreenshotTest {
    // No hazeSource exists: softened pixels can only come from the native backdrop, not fallback.
    val state = HazeState()
    setContent {
      Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Row(Modifier.size(200.dp, 100.dp)) {
          Box(Modifier.size(100.dp).background(Color.Black))
          Box(Modifier.size(100.dp).background(Color.White))
        }
        Box(Modifier.size(200.dp, 100.dp).testTag("backdrop").effect(HazeInput.Backdrop(state)))
      }
    }
    if (Build.VERSION.SDK_INT >= 37) {
      // Refresh the window display list before Robolectric's synchronous hardware PixelCopy.
      composeTestRule.runOnIdle {
        composeTestRule.activity.window.decorView.invalidate()
      }
      waitForIdle()
    }
    val pixels = captureRootPixels()
    val bounds = onNodeWithTag("backdrop").fetchSemanticsNode().boundsInRoot
    val centerX = bounds.center.x.roundToInt()
    val centerY = bounds.center.y.roundToInt()
    val near = (bounds.width * 3 / 200).roundToInt()
    val far = (bounds.width * 60 / 200).roundToInt()
    val blackInterior = pixels[centerX - far, centerY].red
    val blackNearEdge = pixels[centerX - near, centerY].red
    val whiteNearEdge = pixels[centerX + near, centerY].red
    val whiteInterior = pixels[centerX + far, centerY].red
    assertThat(blackInterior, "black interior remains black").isLessThan(0.05f)
    assertThat(whiteInterior, "white interior remains white").isGreaterThan(0.95f)
    if (expectBlur) {
      assertThat(blackNearEdge - blackInterior, "black side is softened").isGreaterThan(0.05f)
      assertThat(whiteInterior - whiteNearEdge, "white side is softened").isGreaterThan(0.05f)
    } else {
      assertThat(blackNearEdge, "black side remains sharp").isEqualTo(blackInterior)
      assertThat(whiteNearEdge, "white side remains sharp").isEqualTo(whiteInterior)
    }
  }
}
