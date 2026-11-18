// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test

/**
 * A square Glass bar docked to the bottom of an edge-to-edge window must cover its outermost
 * pixels, so the layer beneath never shows through at its edges (#1775).
 */
// Runtime-shader Glass, which this covers, starts at API 33.
@SdkSuppress(minSdkVersion = 33)
class GlassSquareEdgeCoverageInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val configuration = mutableStateOf(Configuration(0.dp, HazePerformanceMode.Default, expandLayerBounds = false, Color.Magenta))

  @Test
  fun squareBar_edgePixelsHideUnderlay_defaultMode() = assertEdgesHideUnderlay(HazePerformanceMode.Default, expandLayerBounds = false)

  @Test
  fun squareBar_edgePixelsHideUnderlay_defaultMode_expanded() = assertEdgesHideUnderlay(HazePerformanceMode.Default, expandLayerBounds = true)

  @Test
  fun squareBar_edgePixelsHideUnderlay_qualityMode() = assertEdgesHideUnderlay(HazePerformanceMode.Quality, expandLayerBounds = false)

  @Test
  fun squareBar_edgePixelsHideUnderlay_qualityMode_expanded() = assertEdgesHideUnderlay(HazePerformanceMode.Quality, expandLayerBounds = true)

  @Test
  fun squareBar_withRefractionDetailAtEdge_edgePixelsHideUnderlay() =
    // A large displacement puts the refracted edge sample inside the separate detail shader's band.
    assertEdgesHideUnderlay(
      HazePerformanceMode.Default,
      expandLayerBounds = false,
      customOptics = GlassOptics(refractionDisplacement = 24.dp),
    )

  @Test
  fun squareBar_smallBlur_edgePixelsHideUnderlay() =
    // Clear's shallow blur runs the tap blur, whose taps past the window edge read transparency.
    assertEdgesHideUnderlay(HazePerformanceMode.Default, expandLayerBounds = false, customOptics = GlassOptics.clear)

  private fun assertEdgesHideUnderlay(
    performanceMode: HazePerformanceMode,
    expandLayerBounds: Boolean,
    customOptics: GlassOptics? = null,
  ) {
    composeTestRule.runOnUiThread { composeTestRule.activity.enableEdgeToEdge() }
    configuration.value = Configuration(0.dp, performanceMode, expandLayerBounds, Color.Magenta)
    composeTestRule.setContent {
      val current = configuration.value
      // A fresh state per configuration keeps each capture independent of the previous one.
      val state = remember(current) { HazeState() }
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Green))
        Box(
          Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(80.dp)
            .testTag("glass")
            .background(current.underlay)
            .clip(RectangleShape)
            .hazeGlass(
              input = HazeInput.Sources(state),
              style = GlassStyle.regular.then {
                shape(RoundedCornerShape(0.dp))
                edgeSoftness(current.softness)
                customOptics?.let { optics(it) }
              },
              performanceMode = current.performanceMode,
              expandLayerBounds = current.expandLayerBounds,
            ),
        )
      }
    }
    fun capture(softness: Dp, underlay: Color): PixelMap {
      composeTestRule.runOnIdle { configuration.value = Configuration(softness, performanceMode, expandLayerBounds, underlay) }
      composeTestRule.waitForIdle()
      return composeTestRule.onNodeWithTag("glass").captureToImage().toPixelMap()
    }
    for (softness in listOf(0.dp, 1.dp)) {
      // A fully covered edge cannot depend on what lies beneath the bar.
      val overMagenta = capture(softness, Color.Magenta)
      val overGreen = capture(softness, Color.Green)
      val variant = "mode=$performanceMode expand=$expandLayerBounds softness=$softness"
      for ((x, y) in overMagenta.borderPixels()) {
        val difference = abs(overMagenta[x, y].red - overGreen[x, y].red) + abs(overMagenta[x, y].green - overGreen[x, y].green)
        assertThat(difference, "$variant underlay shows at ($x, $y): ${overMagenta[x, y]} vs ${overGreen[x, y]}")
          .isLessThan(4f / 255f)
      }
    }
  }

  private fun PixelMap.borderPixels(): List<Pair<Int, Int>> =
    (0 until width).flatMap { x -> listOf(x to 0, x to height - 1) } +
      (0 until height).flatMap { y -> listOf(0 to y, width - 1 to y) }

  private data class Configuration(
    val softness: Dp,
    val performanceMode: HazePerformanceMode,
    val expandLayerBounds: Boolean,
    val underlay: Color,
  )
}
