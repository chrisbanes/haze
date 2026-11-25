// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test

/**
 * Below Quality, a wide Glass blur reads a reduced copy of its source (see
 * [HazePerformanceMode.resolveGlassLayerBlurSigma]). Turning the blur on and off at runtime must
 * diffuse and then restore a striped backdrop, as the platform blur chain does in Quality.
 */
// Runtime-shader Glass, which this covers, starts at API 33.
@SdkSuppress(minSdkVersion = 33)
class GlassLayerBlurInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun layerBlur_balanced_diffusesStripesAndRestoresThem() = assertBlurToggles(HazePerformanceMode.Balanced)

  @Test
  fun layerBlur_performance_diffusesStripesAndRestoresThem() = assertBlurToggles(HazePerformanceMode.Performance)

  @Test
  fun platformBlur_quality_diffusesStripesAndRestoresThem() = assertBlurToggles(HazePerformanceMode.Quality)

  private fun assertBlurToggles(mode: HazePerformanceMode) {
    var blurEnabled by mutableStateOf(false)
    composeTestRule.setContent {
      val hazeState = remember { HazeState() }
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
          drawRect(Color(0xFF333333))
          val period = 32.dp.toPx()
          var x = 0f
          while (x < size.width) {
            drawRect(Color(0xFFCCCCCC), Offset(x, 0f), Size(period / 2f, size.height))
            x += period
          }
        }
        // 176 dp tall resolves Regular's depth to 1, so the blurred copy is the whole depth input.
        Box(
          Modifier.size(280.dp, 176.dp).testTag("glass").hazeGlass(
            input = HazeInput.Sources(hazeState),
            style = GlassStyle.regular.then {
              optics(
                GlassOptics.regular.copy(
                  refractionStrength = 0f,
                  blurRadius = if (blurEnabled) GlassOptics.regular.blurRadius else OpticalSizeValue.Fixed(0.dp),
                ),
              )
            },
            performanceMode = mode,
          ),
        )
      }
    }
    composeTestRule.waitForIdle()
    val sharp = captureGlass().stripeEnergy()
    assertThat(sharp, "$mode sharp positive control").isGreaterThan(1f)

    blurEnabled = true
    composeTestRule.waitForIdle()
    assertThat(captureGlass().stripeEnergy() / sharp, "$mode blurred stripe retention").isLessThan(0.15f)

    blurEnabled = false
    composeTestRule.waitForIdle()
    assertThat(abs(captureGlass().stripeEnergy() / sharp - 1f), "$mode restored stripe energy").isLessThan(0.05f)
  }

  private fun captureGlass(): PixelMap = composeTestRule.onNodeWithTag("glass").captureToImage().toPixelMap()

  /** Mean absolute horizontal luminance step across the central rows, away from the rounded edges. */
  private fun PixelMap.stripeEnergy(): Float {
    val inset = width / 6
    var total = 0f
    var count = 0
    for (y in height / 3 until height * 2 / 3) {
      for (x in inset until width - inset - 1) {
        total += abs(this[x + 1, y].luminanceApprox() - this[x, y].luminanceApprox())
        count++
      }
    }
    return total / count * 255f
  }

  private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
