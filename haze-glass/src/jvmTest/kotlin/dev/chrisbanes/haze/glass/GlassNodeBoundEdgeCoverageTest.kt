// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test

/**
 * A Glass surface must cover the outermost pixels of its node-bound edges, the straight parts of
 * its silhouette that lie on its node bounds, so the layer beneath never shows through there,
 * whatever its corners (#1785).
 */
class GlassNodeBoundEdgeCoverageTest {
  @Test
  fun nodeBoundEdges_hideUnderlay_qualityMode() = assertNodeBoundEdgesCovered(HazePerformanceMode.Quality, expandLayerBounds = false)

  @Test
  fun nodeBoundEdges_hideUnderlay_qualityMode_expanded() = assertNodeBoundEdgesCovered(HazePerformanceMode.Quality, expandLayerBounds = true)

  @Test
  fun nodeBoundEdges_hideUnderlay_defaultMode() = assertNodeBoundEdgesCovered(HazePerformanceMode.Default, expandLayerBounds = false)

  @Test
  fun nodeBoundEdges_hideUnderlay_defaultMode_expanded() = assertNodeBoundEdgesCovered(HazePerformanceMode.Default, expandLayerBounds = true)

  @Test
  fun nodeBoundEdges_hideUnderlay_smallBlur() {
    // Clear's shallow blur runs the tap blur, whose taps past the clipped edge read transparency.
    for (performanceMode in listOf(HazePerformanceMode.Default, HazePerformanceMode.Quality)) {
      for (expandLayerBounds in listOf(false, true)) {
        for (optics in listOf(GlassOptics.clear, GlassOptics.clear.copy(refractionStrength = 0f))) {
          assertNodeBoundEdgesCovered(performanceMode, expandLayerBounds, listOf(Rounded, Continuous), optics)
        }
      }
    }
  }

  @Test
  fun nodeBoundEdges_hideUnderlay_progressiveBlur() {
    // Progressive blur always runs the tap blur.
    val progressive = GlassOptics.regular.copy(
      progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 1f),
    )
    for (performanceMode in listOf(HazePerformanceMode.Default, HazePerformanceMode.Quality)) {
      for (expandLayerBounds in listOf(false, true)) {
        assertNodeBoundEdgesCovered(performanceMode, expandLayerBounds, listOf(Rounded, Continuous), progressive)
      }
    }
  }

  /**
   * For every bar, outlined or not and at each edge softness: the node-bound edge pixels do not
   * depend on what lies beneath the bar, and coverage only falls from an edge into its corner.
   */
  private fun assertNodeBoundEdgesCovered(
    performanceMode: HazePerformanceMode,
    expandLayerBounds: Boolean,
    bars: List<Bar> = listOf(Square, BottomRounded, Rounded, Continuous, Capsule),
    optics: GlassOptics? = null,
  ) {
    for (bar in bars) {
      for (outlined in listOf(false, true)) {
        for (softness in listOf(0.dp, 1.dp, 4.dp)) {
          val overMagenta = capture(bar, performanceMode, expandLayerBounds, softness, Color.Magenta, outlined = outlined, optics = optics)
          val overGreen = capture(bar, performanceMode, expandLayerBounds, softness, Color.Green, outlined = outlined, optics = optics)
          val variant = "bar=$bar outlined=$outlined mode=$performanceMode expand=$expandLayerBounds softness=$softness optics=$optics"
          for ((x, y) in overMagenta.nodeBoundEdgePixels(bar)) {
            assertThat(underlayDifference(overMagenta, overGreen, x, y), "$variant underlay shows at ($x, $y)")
              .isLessThan(4f / 255f)
          }
          assertCoverageFallsMonotonicallyIntoCorners(bar, overMagenta, overGreen, variant)
        }
      }
    }
  }

  @Test
  fun roundedSurface_withoutCoverage_showsUnderlayAtNodeBoundEdges() {
    // The fixture must tell a covered edge from an uncovered one: this is the seam being fixed.
    val overMagenta = capture(BottomRounded, HazePerformanceMode.Quality, false, 0.dp, Color.Magenta, covering = false)
    val overGreen = capture(BottomRounded, HazePerformanceMode.Quality, false, 0.dp, Color.Green, covering = false)
    val worst = overMagenta.nodeBoundEdgePixels(BottomRounded).maxOf { (x, y) -> underlayDifference(overMagenta, overGreen, x, y) }
    assertThat(worst).isGreaterThan(0.5f)
  }

  @Test
  fun roundedSurface_cornersAwayFromNodeBoundsAreUnchanged_qualityMode() {
    // Coverage only rises within a physical pixel of the node bounds. Everything else, including
    // every part of a corner arc further away, renders as it does without the coverage.
    val covering = capture(BottomRounded, HazePerformanceMode.Quality, false, 0.dp, Color.Magenta)
    val legacy = capture(BottomRounded, HazePerformanceMode.Quality, false, 0.dp, Color.Magenta, covering = false)
    var compared = 0
    for (y in 0 until covering.height) {
      for (x in 0 until covering.width) {
        if (covering.isNearNodeBoundEdge(BottomRounded, x, y)) continue
        compared++
        assertThat(colorDifference(covering[x, y], legacy[x, y]), "changed at ($x, $y)").isLessThan(1.5f / 255f)
      }
    }
    // The corner boxes alone hold more pixels than the excluded edge band.
    assertThat(compared).isGreaterThan(covering.width * covering.height * 9 / 10)
  }

  @Test
  fun outlinedSurfaces_drawOnlyTheirOutlineOutsideNode_qualityMode() {
    // The outline is drawn outside the node, over whatever lies beneath it. The material, which
    // now reaches past the node-bound edges, must not spread under or beyond it.
    for (bar in listOf(Square, BottomRounded, Continuous)) {
      val withGlass = captureInset(bar)
      val withoutGlass = captureInset(bar = null)

      // The bar spans (40, 100)-(360, 180) in the root.
      fun ring(distance: Int) = (40 - distance until 360 + distance).flatMap { x -> listOf(x to 100 - distance, x to 179 + distance) } +
        (100 until 180).flatMap { y -> listOf(40 - distance to y, 359 + distance to y) }
      var outlineShown = 0f
      for ((x, y) in ring(1)) {
        // A translucent white line only lightens: over a saturated stripe it leaves the full
        // channels alone and raises the empty ones by the same amount. Material would not.
        val beneath = withoutGlass[x, y].channels()
        val drawn = withGlass[x, y].channels()
        val raised = drawn.indices.filter { beneath[it] == 0f }.map { drawn[it] }
        for (channel in drawn.indices.filter { beneath[it] == 1f }) {
          assertThat(drawn[channel], "$bar material under the outline at ($x, $y)").isGreaterThan(1f - 1.5f / 255f)
        }
        assertThat(raised.max() - raised.min(), "$bar material under the outline at ($x, $y)").isLessThan(1.5f / 255f)
        outlineShown = maxOf(outlineShown, raised.max())
      }
      assertThat(outlineShown, "$bar outline is drawn outside the node").isGreaterThan(0.03f)
      for ((x, y) in ring(2) + ring(3)) {
        assertThat(colorDifference(withGlass[x, y], withoutGlass[x, y]), "$bar drew beyond its outline at ($x, $y)")
          .isLessThan(1.5f / 255f)
      }
    }
  }

  private fun Color.channels(): List<Float> = listOf(red, green, blue)

  private fun captureInset(bar: Bar?): PixelMap {
    lateinit var pixels: PixelMap
    runComposeUiTest {
      setContent {
        Box(Modifier.size(BAR_WIDTH, 300.dp).testTag("root")) {
          val state = HazeState()
          StripedSource(state)
          if (bar != null) {
            Box(
              Modifier.offset(40.dp, 100.dp)
                .size(320.dp, 80.dp)
                .glassBar(state, bar, HazePerformanceMode.Quality, expandLayerBounds = false, 0.dp, outlined = true, covering = true),
            )
          }
        }
      }
      waitForIdle()
      pixels = onNodeWithTag("root").captureToImage().toPixelMap()
    }
    return pixels
  }

  private fun colorDifference(a: Color, b: Color): Float =
    maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue), abs(a.alpha - b.alpha))

  private fun assertCoverageFallsMonotonicallyIntoCorners(bar: Bar, overMagenta: PixelMap, overGreen: PixelMap, variant: String) {
    val right = overMagenta.width - 1
    val bottom = overMagenta.height - 1
    // Each run starts mid-edge, on the straight segment, and ends at a corner of the node.
    val runs = mapOf(
      "bottom row to the left" to (right / 2 downTo 0).map { it to bottom },
      "bottom row to the right" to (right / 2..right).map { it to bottom },
      "left column downwards" to (bottom / 2..bottom).map { 0 to it },
      "right column downwards" to (bottom / 2..bottom).map { right to it },
    )
    for ((name, run) in runs) {
      var previous = 0f
      for ((x, y) in run) {
        // The underlay shows where coverage is missing, so it may only grow along the run.
        val shown = underlayDifference(overMagenta, overGreen, x, y)
        assertThat(shown, "$variant $name: coverage rises again at ($x, $y)").isGreaterThan(previous - 2f / 255f)
        previous = maxOf(previous, shown)
      }
      if (bar.bottomRadius > 0.dp) {
        assertThat(previous, "$variant $name: the corner beyond the arc shows the underlay").isGreaterThan(1.9f)
      }
    }
  }

  private fun capture(
    bar: Bar,
    performanceMode: HazePerformanceMode,
    expandLayerBounds: Boolean,
    softness: Dp,
    underlay: Color,
    covering: Boolean = true,
    outlined: Boolean = false,
    optics: GlassOptics? = null,
  ): PixelMap {
    lateinit var pixels: PixelMap
    runComposeUiTest {
      setContent { DockedBar(bar, performanceMode, expandLayerBounds, softness, underlay, covering, outlined, optics) }
      waitForIdle()
      pixels = onNodeWithTag("glass").captureToImage().toPixelMap()
    }
    return pixels
  }

  /** How much of the difference between a magenta and a green underlay reaches a pixel. */
  private fun underlayDifference(overMagenta: PixelMap, overGreen: PixelMap, x: Int, y: Int): Float =
    abs(overMagenta[x, y].red - overGreen[x, y].red) + abs(overMagenta[x, y].green - overGreen[x, y].green)

  /** The border pixels outside every rounded corner's box. */
  private fun PixelMap.nodeBoundEdgePixels(bar: Bar): List<Pair<Int, Int>> =
    ((0 until width).flatMap { x -> listOf(x to 0, x to height - 1) } + (0 until height).flatMap { y -> listOf(0 to y, width - 1 to y) })
      .filterNot { (x, y) -> cornerDistanceAt(bar, x, y) != null }

  /**
   * Whether the pixel is where coverage may change: within a physical pixel of the silhouette, where
   * the silhouette is itself within a physical pixel of the node bounds.
   */
  private fun PixelMap.isNearNodeBoundEdge(bar: Bar, x: Int, y: Int): Boolean {
    val toNodeBounds = -minOf(x + 0.5f, width - x - 0.5f, y + 0.5f, height - y - 0.5f)
    val toSilhouette = maxOf(toNodeBounds, cornerDistanceAt(bar, x, y) ?: toNodeBounds)
    return toSilhouette > -1f && toSilhouette - toNodeBounds < 1f
  }

  /** The signed distance from the pixel centre to its corner's arc, or null outside every corner box. */
  private fun PixelMap.cornerDistanceAt(bar: Bar, x: Int, y: Int): Float? {
    val radius = (if (y < height / 2) bar.topRadius else bar.bottomRadius).value * width / BAR_WIDTH.value
    val dx = radius - minOf(x + 0.5f, width - x - 0.5f)
    val dy = radius - minOf(y + 0.5f, height - y - 0.5f)
    return if (dx > 0f && dy > 0f) hypot(dx, dy) - radius else null
  }

  @Composable
  private fun DockedBar(
    bar: Bar,
    performanceMode: HazePerformanceMode,
    expandLayerBounds: Boolean,
    softness: Dp,
    underlay: Color,
    covering: Boolean,
    outlined: Boolean,
    optics: GlassOptics?,
  ) {
    val state = HazeState()
    Box(Modifier.size(BAR_WIDTH, 300.dp)) {
      StripedSource(state)
      Box(
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(80.dp)
          .testTag("glass")
          .background(underlay)
          .clip(RectangleShape)
          .glassBar(state, bar, performanceMode, expandLayerBounds, softness, outlined, covering, optics),
      )
    }
  }

  @Composable
  private fun StripedSource(state: HazeState) {
    Box(
      Modifier.fillMaxSize()
        .hazeSource(state)
        // Non-uniform, so blur and refraction matter. Flat stripes rather than a gradient: a
        // gradient is dithered by device position, which differs between the two placements.
        .drawBehind {
          val stripe = size.width / SOURCE_STRIPES.size
          SOURCE_STRIPES.forEachIndexed { index, color ->
            // Each stripe runs to the far edge, under the next: stripes that only abut leave a
            // translucent seam when a reduced-scale capture puts their shared edge mid-pixel.
            drawRect(color, topLeft = Offset(index * stripe, 0f), size = Size(size.width - index * stripe, size.height))
          }
        },
    )
  }

  @Composable
  private fun Modifier.glassBar(
    state: HazeState,
    bar: Bar,
    performanceMode: HazePerformanceMode,
    expandLayerBounds: Boolean,
    softness: Dp,
    outlined: Boolean,
    covering: Boolean,
    optics: GlassOptics? = null,
  ): Modifier {
    val effect = remember { GlassRuntimeEffect().apply { nodeBoundEdgeCoverageDisabledForTest = !covering } }
    return hazeGlass(
      factory = HazeEffectFactory { effect },
      input = HazeInput.Sources(state),
      style = GlassStyle.regular.then {
        when (val shape = bar.shape) {
          is RoundedCornerShape -> shape(shape)
          is ContinuousRoundedCornerShape -> shape(shape)
          else -> error("Unsupported shape $shape")
        }
        edgeSoftness(softness)
        if (outlined) edgeOutline(Color.White.copy(alpha = 0.5f), 0.7.dp)
        optics?.let { optics(it) }
      },
      performanceMode = performanceMode,
      expandLayerBounds = expandLayerBounds,
      interactionSource = null,
    )
  }

  /** A bar shape with how far its top and bottom corners reach along each edge. */
  private class Bar(val name: String, val shape: CornerBasedShape, val topRadius: Dp, val bottomRadius: Dp) {
    override fun toString(): String = name
  }

  private companion object {
    val BAR_WIDTH = 400.dp
    val SOURCE_STRIPES = listOf(Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Green)
    val Square = Bar("square", RoundedCornerShape(0.dp), 0.dp, 0.dp)
    val BottomRounded = Bar("bottomRounded", RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp), 0.dp, 16.dp)
    val Rounded = Bar("rounded", RoundedCornerShape(16.dp), 16.dp, 16.dp)
    val Capsule = Bar("capsule", RoundedCornerShape(percent = 50), 40.dp, 40.dp)

    // A continuous corner eases into its edges beyond its radius, by continuousCornerExtent.
    val Continuous = Bar("continuous", ContinuousRoundedCornerShape(16.dp), 25.dp, 25.dp)
  }
}
