// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.unit.roundToIntSize
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.math.ceil
import org.junit.Rule
import org.junit.Test

/**
 * Below Quality, a wide Glass blur reads a reduced copy of its source (see
 * [HazePerformanceMode.resolveGlassLayerBlurSigma]). Turning the blur on and off at runtime must
 * diffuse and then restore a striped backdrop, as the platform blur chain does in Quality, and the
 * result must match Quality's in the interior and along the edges (#1903).
 */
// Runtime-shader Glass, which this covers, starts at API 33.
@SdkSuppress(minSdkVersion = 33)
class GlassLayerBlurInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val scene = mutableStateOf<Scene?>(null)

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

  @Test
  fun layerBlurInterior_balanced_matchesQuality() = assertInteriorMatchesQuality(HazePerformanceMode.Balanced)

  @Test
  fun layerBlurInterior_performance_matchesQuality() = assertInteriorMatchesQuality(HazePerformanceMode.Performance)

  // Glass's default background is transparent, so a translucent source stays translucent.
  @Test
  fun layerBlurInterior_translucentSource_balanced_matchesQuality() =
    assertInteriorMatchesQuality(HazePerformanceMode.Balanced, SceneKind.TranslucentInterior)

  // At 176 dp and above Regular's depth is 1, so no sharp share hides a partly empty reduced edge.
  // Below that, the sharp share does hide it; that case guards the composed mix instead.
  @Test
  fun layerBlurEdges_depthOne_balanced_matchesQuality() = assertEdgesMatchQuality(startSize = 200.dp, depthOne = true)

  @Test
  fun layerBlurEdges_depthBelowOne_balanced_matchesQuality() = assertEdgesMatchQuality(startSize = 100.dp, depthOne = false)

  private fun assertInteriorMatchesQuality(mode: HazePerformanceMode, kind: SceneKind = SceneKind.Interior) {
    setScene()
    val (tested, testedRender) = capture(Scene(kind, mode, 64.dp))
    assertThat(testedRender.layerBlurFactor, "$mode layer blur factor").isGreaterThan(0f)
    assertThat(testedRender.params.depth, "$mode depth").isLessThan(1f)
    val (quality, qualityRender) = capture(Scene(kind, HazePerformanceMode.Quality, 64.dp))
    assertThat(qualityRender.layerBlurFactor, "Quality layer blur factor").isEqualTo(0f)

    // Clear of the corners, outline and rim.
    val inset = (20f * composeTestRule.density.density).toInt()
    val difference = tested.maxDifferenceFrom(quality) { x, y ->
      x in inset until tested.width - inset && y in inset until tested.height - inset
    }
    Log.i(TAG, "interior $kind $mode vs Quality: max $difference")
    assertThat(difference, "$mode interior max channel difference (x255)").isLessThanOrEqualTo(2f)
  }

  private fun assertEdgesMatchQuality(startSize: Dp, depthOne: Boolean) {
    setScene()
    var size = startSize
    var balanced = capture(Scene(SceneKind.Edges, HazePerformanceMode.Balanced, size))
    assertThat(balanced.second.layerBlurFactor, "layer blur factor").isGreaterThan(0f)
    // Fixture calibration: the edge fault needs a fractional reduced size on both axes.
    while (!balanced.second.hasFractionalReducedSize() && size < startSize + EDGE_SIZE_STEPS.dp) {
      size += 1.dp
      balanced = capture(Scene(SceneKind.Edges, HazePerformanceMode.Balanced, size))
    }
    val (tested, render) = balanced
    val factor = render.layerBlurFactor
    val sample = render.params.coordinates.sampleSize.roundToIntSize()
    assertThat(render.hasFractionalReducedSize(), "fractional reduced size of $sample / $factor from $startSize").isTrue()
    if (depthOne) {
      assertThat(render.params.depth, "depth").isGreaterThanOrEqualTo(0.9999f)
    } else {
      assertThat(render.params.depth, "depth").isLessThan(1f)
    }
    val (quality, qualityRender) = capture(Scene(SceneKind.Edges, HazePerformanceMode.Quality, size))
    assertThat(qualityRender.layerBlurFactor, "Quality layer blur factor").isEqualTo(0f)

    // The clamp deficit reaches about 2 sigma of the reduced copy into the sample.
    val band = ceil(2f * render.params.layerBlurSigmaPx * factor / render.params.coordinates.scaleFactor).toInt() + 1
    // The outermost pixels hold the rim and antialiasing, which differ with each mode's input scale.
    val rim = ceil(2f * composeTestRule.density.density).toInt()
    val difference = tested.maxDifferenceFrom(quality) { x, y ->
      x in rim until tested.width - rim && y in rim until tested.height - rim &&
        (x >= tested.width - band || y >= tested.height - band)
    }
    val variant = "size=$size depth=${render.params.depth} factor=$factor sample=$sample band=$band rim=$rim"
    Log.i(TAG, "edges $variant Balanced vs Quality: max $difference")
    assertThat(difference, "$variant edge max channel difference (x255)").isLessThanOrEqualTo(EDGE_TOLERANCE)
  }

  private fun GlassPreparedRender.hasFractionalReducedSize(): Boolean {
    val sample = params.coordinates.sampleSize.roundToIntSize()
    return listOf(sample.width, sample.height).all { (it / layerBlurFactor % 1f) in 0.15f..0.85f }
  }

  private fun setScene() {
    composeTestRule.setContent {
      val current = scene.value ?: return@setContent
      // A fresh state and effect per configuration keeps each capture independent of the previous one.
      key(current) {
        val state = remember { HazeState() }
        val glass = Modifier.hazeGlass(
          factory = HazeEffectFactory { current.effect },
          input = HazeInput.Sources(state),
          style = GlassStyle.regular.then {
            if (current.kind == SceneKind.Edges) shape(RoundedCornerShape(0.dp))
            optics(GlassOptics.regular.copy(refractionStrength = 0f))
          },
          performanceMode = current.mode,
          expandLayerBounds = true,
          interactionSource = null,
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          when (current.kind) {
            SceneKind.Interior -> {
              // The black rect covers the glass plus the blur extent on every side.
              Canvas(Modifier.fillMaxSize().hazeSource(state)) {
                drawRect(Color.White)
                val rect = (current.size + 40.dp * 2).toPx()
                drawRect(Color.Black, center - Offset(rect / 2f, rect / 2f), Size(rect, rect))
              }
              Box(Modifier.size(current.size).testTag("glass").then(glass))
            }
            SceneKind.TranslucentInterior -> {
              // The black window shows how opaque the Glass is; it is not part of the capture.
              Box(Modifier.fillMaxSize().background(Color.Black)) {
                Canvas(Modifier.fillMaxSize().hazeSource(state)) { drawRect(Color.Red.copy(alpha = 0.5f)) }
              }
              Box(Modifier.size(current.size).testTag("glass").then(glass))
            }
            SceneKind.Edges -> {
              Box(Modifier.fillMaxSize().hazeSource(state).background(Color(0xFF3366CC)))
              // An opaque underlay shows through any see-through edge.
              Box(Modifier.size(current.size).testTag("glass").background(Color.Magenta).clip(RectangleShape).then(glass))
            }
          }
        }
      }
    }
  }

  private fun capture(configuration: Scene): Pair<PixelMap, GlassPreparedRender> {
    composeTestRule.runOnIdle { scene.value = configuration }
    composeTestRule.waitForIdle()
    val pixels = captureGlass()
    return pixels to checkNotNull(configuration.effect.preparedRender) { "${configuration.mode} prepared render" }
  }

  private fun captureGlass(): PixelMap = composeTestRule.onNodeWithTag("glass").captureToImage().toPixelMap()

  /** The largest per-channel absolute difference from [other] over pixels in [region], in 1/255 steps. */
  private inline fun PixelMap.maxDifferenceFrom(other: PixelMap, region: (x: Int, y: Int) -> Boolean): Float {
    var maximum = 0f
    for (x in 0 until width) {
      for (y in 0 until height) {
        if (!region(x, y)) continue
        val a = this[x, y]
        val b = other[x, y]
        maximum = maxOf(maximum, abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue), abs(a.alpha - b.alpha))
      }
    }
    return maximum * 255f
  }

  private enum class SceneKind { Interior, TranslucentInterior, Edges }

  private class Scene(val kind: SceneKind, val mode: HazePerformanceMode, val size: Dp) {
    val effect = GlassRuntimeEffect()
  }

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

  private companion object {
    const val TAG = "GlassLayerBlurTest"
    const val EDGE_SIZE_STEPS = 24

    // Below half the 11/255 Robolectric measured inside the rim at depth 1 before the fix (#1903).
    const val EDGE_TOLERANCE = 4f
  }
}
