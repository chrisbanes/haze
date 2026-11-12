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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.prop
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.RuntimeShaderRenderEffectException
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.test.Test

/**
 * A square Glass bar over a contrasting layer must cover its outermost pixels, so the layer beneath
 * never shows through at its edges, whatever its edge softness (#1775).
 */
class GlassSquareEdgeCoverageTest {
  @Test
  fun squareSurface_edgePixelsHideUnderlay_qualityMode() = assertRuntimeEdgesHideUnderlay(HazePerformanceMode.Quality, expandLayerBounds = false)

  @Test
  fun squareSurface_edgePixelsHideUnderlay_qualityMode_expanded() = assertRuntimeEdgesHideUnderlay(HazePerformanceMode.Quality, expandLayerBounds = true)

  @Test
  fun squareSurface_edgePixelsHideUnderlay_defaultMode() = assertRuntimeEdgesHideUnderlay(HazePerformanceMode.Default, expandLayerBounds = false)

  @Test
  fun squareSurface_edgePixelsHideUnderlay_defaultMode_expanded() = assertRuntimeEdgesHideUnderlay(HazePerformanceMode.Default, expandLayerBounds = true)

  @Test
  fun squareSurface_edgePixelsHideUnderlay_smallBlur() {
    // Clear's shallow blur runs the tap blur, whose taps past the clipped edge read transparency.
    // Expanded bounds too: the clip still crops the padding, so taps must not reach into it.
    for (performanceMode in listOf(HazePerformanceMode.Default, HazePerformanceMode.Quality)) {
      for (expandLayerBounds in listOf(false, true)) {
        assertRuntimeEdgesHideUnderlay(performanceMode, expandLayerBounds, optics = GlassOptics.clear)
        assertRuntimeEdgesHideUnderlay(
          performanceMode,
          expandLayerBounds,
          optics = GlassOptics.clear.copy(refractionStrength = 0f),
        )
      }
    }
  }

  @Test
  fun squareSurface_edgePixelsHideUnderlay_progressiveBlur() {
    // Progressive blur always runs the tap blur.
    val progressive = GlassOptics.regular.copy(
      progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 1f),
    )
    for (performanceMode in listOf(HazePerformanceMode.Default, HazePerformanceMode.Quality)) {
      for (expandLayerBounds in listOf(false, true)) {
        assertRuntimeEdgesHideUnderlay(performanceMode, expandLayerBounds, optics = progressive)
      }
    }
  }

  private fun assertRuntimeEdgesHideUnderlay(
    performanceMode: HazePerformanceMode,
    expandLayerBounds: Boolean,
    optics: GlassOptics? = null,
  ) {
    for (softness in listOf(0.dp, 1.dp, 4.dp)) {
      // A fully covered edge cannot depend on what lies beneath the bar.
      val overMagenta = capture(softness, fallback = false, performanceMode, expandLayerBounds, Color.Magenta, optics)
      val overGreen = capture(softness, fallback = false, performanceMode, expandLayerBounds, Color.Green, optics)
      val variant = "mode=$performanceMode expand=$expandLayerBounds softness=$softness optics=$optics"
      for ((x, y) in overMagenta.borderPixels()) {
        val difference = abs(overMagenta[x, y].red - overGreen[x, y].red) + abs(overMagenta[x, y].green - overGreen[x, y].green)
        assertThat(difference, "$variant underlay shows at ($x, $y): ${overMagenta[x, y]} vs ${overGreen[x, y]}")
          .isLessThan(4f / 255f)
      }
    }
  }

  private fun capture(
    softness: Dp,
    fallback: Boolean,
    performanceMode: HazePerformanceMode,
    expandLayerBounds: Boolean,
    underlay: Color,
    optics: GlassOptics? = null,
  ): PixelMap {
    lateinit var pixels: PixelMap
    runComposeUiTest {
      setContent { WindowEdgeBar(softness, fallback, performanceMode, expandLayerBounds, underlay, optics) }
      waitForIdle()
      pixels = onNodeWithTag("glass").captureToImage().toPixelMap()
    }
    return pixels
  }

  private fun PixelMap.borderPixels(): List<Pair<Int, Int>> =
    (0 until width).flatMap { x -> listOf(x to 0, x to height - 1) } +
      (0 until height).flatMap { y -> listOf(0 to y, width - 1 to y) }

  @Test
  fun squareSurface_edgePixelsNoMoreTranslucentThanInterior_fallback() {
    // The fallback material is translucent by design, so its interior shows the layer beneath too.
    forEachSoftness(fallback = true, HazePerformanceMode.Default, expandLayerBounds = false) { pixels, variant ->
      val inner = pixels[pixels.width / 2, pixels.height / 2]
      for ((x, y) in pixels.probes()) {
        val color = pixels[x, y]
        assertThat(color.red - color.green, "$variant magenta at ($x, $y)")
          .isLessThan(inner.red - inner.green + 0.1f)
      }
    }
  }

  private fun forEachSoftness(
    fallback: Boolean,
    performanceMode: HazePerformanceMode,
    expandLayerBounds: Boolean,
    assert: (PixelMap, String) -> Unit,
  ) {
    for (softness in listOf(0.dp, 1.dp, 4.dp)) {
      runComposeUiTest {
        setContent { WindowEdgeBar(softness, fallback, performanceMode, expandLayerBounds, Color.Magenta) }
        waitForIdle()
        val variant = "fallback=$fallback mode=$performanceMode expand=$expandLayerBounds softness=$softness"
        assert(onNodeWithTag("glass").captureToImage().toPixelMap(), variant)
      }
    }
  }

  @Test
  fun squareSurface_translucentSourceEdgeMatchesInterior() {
    // A uniform half-transparent source under a rim-free style renders flat Glass. Its top edge, where
    // the source continues beyond the bar, must not gain alpha from content composited twice.
    for (performanceMode in listOf(HazePerformanceMode.Default, HazePerformanceMode.Quality)) {
      lateinit var pixels: PixelMap
      runComposeUiTest {
        setContent { FlatBar(performanceMode, glass = true, clipToNode = true) }
        waitForIdle()
        pixels = onNodeWithTag("glass").captureToImage().toPixelMap()
      }
      val interior = pixels.rowMean(pixels.height / 2)
      for (y in 0..2) {
        val row = pixels.rowMean(y)
        for (channel in 0..3) {
          assertThat(abs(row[channel] - interior[channel]), "mode=$performanceMode row=$y channel=$channel")
            .isLessThan(2f / 255f)
        }
      }
    }
  }

  @Test
  fun squareSurface_drawsNothingOutsideNode() {
    // Coverage reaches past the material; without a caller clip, the node must still bound it.
    fun capture(glass: Boolean): PixelMap {
      lateinit var pixels: PixelMap
      runComposeUiTest {
        setContent { FlatBar(HazePerformanceMode.Default, glass = glass, clipToNode = false) }
        waitForIdle()
        pixels = onNodeWithTag("root").captureToImage().toPixelMap()
      }
      return pixels
    }
    val withGlass = capture(glass = true)
    val without = capture(glass = false)
    // The bar spans (40, 100)-(360, 180) in the 400 x 300 root; probe one and two pixels outside it.
    val ring = (40..359).flatMap { x -> listOf(x to 98, x to 99, x to 180, x to 181) } +
      (100..179).flatMap { y -> listOf(38 to y, 39 to y, 360 to y, 361 to y) }
    for ((x, y) in ring) {
      val a = withGlass[x, y]
      val b = without[x, y]
      val difference = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
      assertThat(difference, "outside the node at ($x, $y): $a vs $b").isLessThan(1f / 255f)
    }
  }

  @Test
  fun squareSurface_shapeChangeAtLayerLimit_reevaluatesBudget() = runComposeUiTest {
    // A full-scale rounded surface exactly at the layer limit fits; its square capture gains margins
    // and must lower its scale rather than reuse the rounded decision, and recover when rounded again.
    val state = HazeState()
    val effect = GlassRuntimeEffect()
    var square by mutableStateOf(false)
    setContent {
      val size = with(LocalDensity.current) { DpSize(MAX_GLASS_LAYER_DIMENSION_PX.toDp(), 40.toDp()) }
      Box(Modifier.wrapContentSize(unbounded = true).requiredSize(size)) {
        Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Green))
        Box(
          Modifier.fillMaxSize().hazeGlass(
            factory = HazeEffectFactory { effect },
            input = HazeInput.Sources(state),
            style = GlassStyle.regular.then { shape(RoundedCornerShape(if (square) 0.dp else 1.dp)) },
            performanceMode = HazePerformanceMode.Quality,
            expandLayerBounds = false,
            interactionSource = null,
          ),
        )
      }
    }
    waitForIdle()
    assertThat(effect.preparedRenderBudget).isInstanceOf<GlassRenderBudgetDecision.Runtime>()

    square = true
    waitForIdle()
    assertThat(effect.preparedRenderBudget).isInstanceOf<GlassRenderBudgetDecision.Runtime>()
      .prop(GlassRenderBudgetDecision.Runtime::scaleFactor).isLessThan(1f)

    square = false
    waitForIdle()
    assertThat(effect.preparedRenderBudget).isInstanceOf<GlassRenderBudgetDecision.Runtime>()
      .prop(GlassRenderBudgetDecision.Runtime::scaleFactor).isEqualTo(1f)
  }

  private fun PixelMap.rowMean(y: Int): FloatArray {
    val sum = FloatArray(4)
    for (x in 8 until width - 8) {
      val color = this[x, y]
      sum[0] += color.red
      sum[1] += color.green
      sum[2] += color.blue
      sum[3] += color.alpha
    }
    return FloatArray(4) { sum[it] / (width - 16) }
  }

  @Composable
  private fun FlatBar(performanceMode: HazePerformanceMode, glass: Boolean, clipToNode: Boolean) {
    val state = HazeState()
    Box(Modifier.size(400.dp, 300.dp).testTag("root").background(Color.Black)) {
      Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Green.copy(alpha = 0.5f)))
      Box(
        Modifier.offset(40.dp, 100.dp)
          .size(320.dp, 80.dp)
          .testTag("glass")
          .then(if (clipToNode) Modifier.clip(RectangleShape) else Modifier)
          .then(
            if (glass) {
              Modifier.hazeGlass(
                input = HazeInput.Sources(state),
                style = GlassStyle.regular.then {
                  shape(RoundedCornerShape(0.dp))
                  // No edge softness: it deliberately blends the edge toward ungraded optics.
                  edgeSoftness(0.dp)
                  optics(GlassOptics(refractionStrength = 0f))
                  specularIntensity(0f)
                  edgeShadow(Color.Transparent)
                  ambientResponse(0f)
                  tint(Color.Transparent)
                },
                performanceMode = performanceMode,
                expandLayerBounds = false,
              )
            } else {
              Modifier
            },
          ),
      )
    }
  }

  /** The four corners, the four edge midpoints and the centre, as a positive control. */
  private fun PixelMap.probes(): List<Pair<Int, Int>> {
    val right = width - 1
    val bottom = height - 1
    return listOf(
      0 to 0, right to 0, 0 to bottom, right to bottom,
      width / 2 to 0, width / 2 to bottom, 0 to height / 2, right to height / 2,
      width / 2 to height / 2,
    )
  }

  @Composable
  private fun WindowEdgeBar(
    softness: Dp,
    fallback: Boolean,
    performanceMode: HazePerformanceMode,
    expandLayerBounds: Boolean,
    underlay: Color,
    optics: GlassOptics? = null,
  ) {
    val state = HazeState()
    val effect = GlassRuntimeEffect()
    if (fallback) {
      effect.runtimeEffectFactory = GlassRuntimeEffectFactory {
        throw RuntimeShaderRenderEffectException(IllegalStateException("test unavailable runtime"))
      }
    }
    val shape = RoundedCornerShape(0.dp)
    Box(Modifier.size(400.dp, 300.dp)) {
      Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Green))
      Box(
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(80.dp)
          .testTag("glass")
          .background(underlay)
          .clip(RectangleShape)
          .hazeGlass(
            factory = HazeEffectFactory { effect },
            input = HazeInput.Sources(state),
            style = GlassStyle.regular.then {
              shape(shape)
              edgeSoftness(softness)
              optics?.let { optics(it) }
            },
            performanceMode = performanceMode,
            expandLayerBounds = expandLayerBounds,
            interactionSource = null,
          ),
      )
    }
  }
}
