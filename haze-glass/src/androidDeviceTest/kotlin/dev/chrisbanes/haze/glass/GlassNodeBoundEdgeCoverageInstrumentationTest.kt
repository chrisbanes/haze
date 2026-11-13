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
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.math.ceil
import org.junit.Rule
import org.junit.Test

/**
 * A rounded or continuous Glass bar docked to the bottom of an edge-to-edge window, outlined or
 * not, must cover the outermost pixels of its node-bound edges, so the layer beneath never shows
 * through there (#1785).
 */
// Runtime-shader Glass, which this covers, starts at API 33.
@SdkSuppress(minSdkVersion = 33)
class GlassNodeBoundEdgeCoverageInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val configuration = mutableStateOf(Configuration(continuous = false, outlined = false, 0.dp, Color.Magenta))

  @Test
  fun bar_nodeBoundEdgePixelsHideUnderlay_defaultMode() = assertNodeBoundEdgesCovered(HazePerformanceMode.Default, expandLayerBounds = false)

  @Test
  fun bar_nodeBoundEdgePixelsHideUnderlay_defaultMode_expanded() = assertNodeBoundEdgesCovered(HazePerformanceMode.Default, expandLayerBounds = true)

  @Test
  fun bar_nodeBoundEdgePixelsHideUnderlay_qualityMode() = assertNodeBoundEdgesCovered(HazePerformanceMode.Quality, expandLayerBounds = false)

  @Test
  fun bar_nodeBoundEdgePixelsHideUnderlay_qualityMode_expanded() = assertNodeBoundEdgesCovered(HazePerformanceMode.Quality, expandLayerBounds = true)

  @Test
  fun bar_withRefractionDetailAtEdge_nodeBoundEdgePixelsHideUnderlay() =
    // A large displacement puts the refracted edge sample inside the separate detail shader's band.
    assertNodeBoundEdgesCovered(
      HazePerformanceMode.Default,
      expandLayerBounds = false,
      customOptics = GlassOptics(refractionDisplacement = 24.dp),
    )

  @Test
  fun bar_smallBlur_nodeBoundEdgePixelsHideUnderlay() =
    // Clear's shallow blur runs the tap blur, whose taps past the window edge read transparency.
    assertNodeBoundEdgesCovered(HazePerformanceMode.Default, expandLayerBounds = false, customOptics = GlassOptics.clear)

  private fun assertNodeBoundEdgesCovered(
    performanceMode: HazePerformanceMode,
    expandLayerBounds: Boolean,
    customOptics: GlassOptics? = null,
  ) {
    composeTestRule.runOnUiThread { composeTestRule.activity.enableEdgeToEdge() }
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
                if (current.continuous) shape(ContinuousRoundedCornerShape(CORNER)) else shape(RoundedCornerShape(CORNER))
                edgeSoftness(current.softness)
                if (current.outlined) edgeOutline(Color.White.copy(alpha = 0.5f), 0.7.dp)
                customOptics?.let { optics(it) }
              },
              performanceMode = performanceMode,
              expandLayerBounds = expandLayerBounds,
            ),
        )
      }
    }
    fun capture(next: Configuration): PixelMap {
      composeTestRule.runOnIdle { configuration.value = next }
      composeTestRule.waitForIdle()
      return composeTestRule.onNodeWithTag("glass").captureToImage().toPixelMap()
    }
    // A continuous corner eases into its edges beyond its radius, by continuousCornerExtent.
    val cornerBox = ceil(with(composeTestRule.density) { (CORNER * 1.6f).toPx() }).toInt()
    for (continuous in listOf(false, true)) {
      for (outlined in listOf(false, true)) {
        for (softness in listOf(0.dp, 1.dp)) {
          // A fully covered edge cannot depend on what lies beneath the bar.
          val overMagenta = capture(Configuration(continuous, outlined, softness, Color.Magenta))
          val overGreen = capture(Configuration(continuous, outlined, softness, Color.Green))
          val variant = "mode=$performanceMode expand=$expandLayerBounds continuous=$continuous outlined=$outlined softness=$softness"
          fun shown(x: Int, y: Int) =
            abs(overMagenta[x, y].red - overGreen[x, y].red) + abs(overMagenta[x, y].green - overGreen[x, y].green)
          for ((x, y) in overMagenta.nodeBoundEdgePixels(cornerBox)) {
            assertThat(shown(x, y), "$variant underlay shows at ($x, $y): ${overMagenta[x, y]} vs ${overGreen[x, y]}")
              .isLessThan(4f / 255f)
          }
          // Coverage only falls from the bottom edge into its corner, where the underlay shows.
          var previous = 0f
          for (x in overMagenta.width / 2 downTo 0) {
            val current = shown(x, overMagenta.height - 1)
            assertThat(current, "$variant coverage rises again at ($x, ${overMagenta.height - 1})").isGreaterThan(previous - 2f / 255f)
            previous = maxOf(previous, current)
          }
          assertThat(previous, "$variant the corner beyond the arc shows the underlay").isGreaterThan(1.9f)
        }
      }
    }
  }

  /** The border pixels outside every corner's box. */
  private fun PixelMap.nodeBoundEdgePixels(cornerBox: Int): List<Pair<Int, Int>> =
    (cornerBox until width - cornerBox).flatMap { x -> listOf(x to 0, x to height - 1) } +
      (cornerBox until height - cornerBox).flatMap { y -> listOf(0 to y, width - 1 to y) }

  private data class Configuration(
    val continuous: Boolean,
    val outlined: Boolean,
    val softness: Dp,
    val underlay: Color,
  )

  private companion object {
    val CORNER = 16.dp
  }
}
