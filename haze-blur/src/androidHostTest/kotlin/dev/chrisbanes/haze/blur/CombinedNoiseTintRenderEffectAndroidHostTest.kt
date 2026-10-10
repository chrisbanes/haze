// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The combined noise/tint effect reuses one compiled shader. Every effect must still render exactly
 * as a freshly constructed RuntimeShader with the same inputs did, and keep its own uniforms.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35, 37])
class CombinedNoiseTintRenderEffectAndroidHostTest : ScreenshotTest() {
  private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

  @Test
  fun combinedEffect_matchesFreshRuntimeShaderReference() = runScreenshotTest {
    val cases = REFERENCE_CASES
    val candidates = cases.map { candidate(it) }
    val references = cases.map { reference(it) }
    render(cases.indices.flatMap { listOf(candidates[it], references[it]) }, translucent = cases.map { it.translucent }.flatMap { listOf(it, it) })
    val pixels = captureRootPixels()
    cases.forEachIndexed { index, case ->
      assertMatches(pixels, 2 * index, 2 * index + 1, case.toString())
    }
    assertThat(difference(pixels, 0, 2 * cases.lastIndex), "opaque tint control").isGreaterThan(0.1f)
  }

  @Test
  fun retainedEffects_keepTheirOwnUniformsAndChildren() = runScreenshotTest {
    val a = Case(Color.Red.copy(alpha = 0.3f), noise = 0.2f, scale = 1f)
    val b = Case(Color(0.1f, 0.3f, 0.9f, 0.6f, ColorSpaces.DisplayP3), noise = 0.8f, scale = 0.5f)
    val effectA = candidate(a)
    val effectB = candidate(b)
    // Churn the shared shader with further effects, as animated nodes and cache misses do.
    repeat(24) { candidate(Case(Color.Green.copy(alpha = it / 24f), noise = it / 23f, scale = if (it % 2 == 0) 1f else 0.5f)) }
    render(listOf(effectA, effectB, effectA, reference(a), reference(b)))
    val pixels = captureRootPixels()
    assertMatches(pixels, 0, 3, "A")
    assertMatches(pixels, 1, 4, "B")
    assertMatches(pixels, 2, 3, "A again")
    assertThat(difference(pixels, 0, 1), "A and B differ").isGreaterThan(0.1f)
  }

  @Test
  fun controls_tintIsVisibleAndNoiseIsObservable() = runScreenshotTest {
    val withNoise = candidate(Case(Color.Transparent, noise = 1f, scale = 1f))
    val noNoise = candidate(Case(Color.Transparent, noise = 0f, scale = 1f))
    val tinted = candidate(Case(Color.Green.copy(alpha = 0.5f), noise = 0.1f, scale = 1f))
    render(listOf(null, withNoise, noNoise, tinted))
    val pixels = captureRootPixels()
    assertThat(difference(pixels, 1, 2), "noise changes output").isGreaterThan(0.02f)
    assertThat(difference(pixels, 0, 2), "zero noise and tint leave content").isLessThanOrEqualTo(TOLERANCE)
    assertThat(difference(pixels, 0, 3), "tint changes output").isGreaterThan(0.1f)
  }

  @Test
  @Config(sdk = [31, 32])
  fun combinedEffect_isUnavailableBeforeApi33() {
    assertThat(createCombinedNoiseTintRenderEffectOrNull(context, input(), 0.5f, Color.Red, 1f)).isNull()
  }

  @Test
  @Config(sdk = [35])
  fun combinedEligibility_excludesOtherGraphs() {
    val tint = HazeColorEffect.tint(Color.Red.copy(alpha = 0.5f))
    assertThat(params(listOf(tint)).combinedNoiseTintColor()).isNotNull()
    assertThat(params(listOf(tint), noise = 0f).combinedNoiseTintColor()).isNull()
    assertThat(params(listOf(tint), mask = SolidColor(Color.Black)).combinedNoiseTintColor()).isNull()
    assertThat(params(listOf(tint), progressiveMask = SolidColor(Color.Black)).combinedNoiseTintColor()).isNull()
    assertThat(params(listOf(tint), progressive = HazeProgressive.verticalGradient()).combinedNoiseTintColor()).isNull()
    assertThat(params(listOf(HazeColorEffect.tint(SolidColor(Color.Red)))).combinedNoiseTintColor()).isNull()
    assertThat(params(listOf(tint, tint)).combinedNoiseTintColor()).isNull()
    assertThat(params(listOf(HazeColorEffect.tint(Color.Red, BlendMode.Multiply))).combinedNoiseTintColor()).isNull()
    assertThat(params(listOf(tint), modulate = 0.5f).combinedNoiseTintColor()).isEqualTo(Color.Red.copy(alpha = 0.25f))
  }

  private fun params(
    effects: List<HazeColorEffect>,
    noise: Float = 0.5f,
    mask: Brush? = null,
    progressiveMask: Brush? = null,
    progressive: HazeProgressive? = null,
    modulate: Float = 1f,
  ) = RenderEffectParams(
    blurRadius = 8.dp,
    noiseFactor = noise,
    scale = 1f,
    contentSize = Size(100f, 100f),
    contentOffset = Offset.Zero,
    colorEffects = effects,
    colorEffectsAlphaModulate = modulate,
    mask = mask,
    progressiveMask = progressiveMask,
    progressive = progressive,
    blurTileMode = TileMode.Clamp,
  )

  private fun candidate(case: Case): RenderEffect =
    checkNotNull(createCombinedNoiseTintRenderEffectOrNull(context, input(), case.noise, case.tint, case.scale))

  /** The pre-reuse construction: a fresh RuntimeShader per effect. */
  private fun reference(case: Case): RenderEffect {
    val shader = RuntimeShader(COMBINED_NOISE_TINT_SKSL).apply {
      setInputShader("noise", context.createNoiseShader(case.scale))
      setFloatUniform("noiseAlpha", case.noise.coerceIn(0f, 1f))
      setColorUniform("tintColor", case.tint.toArgb())
    }
    return RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"), input())
  }

  private fun ScreenshotUiTest.render(effects: List<RenderEffect?>, translucent: List<Boolean> = effects.map { false }) {
    setContent {
      Column(Modifier.background(Color.White)) {
        effects.chunked(COLUMNS).forEachIndexed { row, rowEffects ->
          Row {
            rowEffects.forEachIndexed { column, effect ->
              val index = row * COLUMNS + column
              Box(
                Modifier.size(BOX_SIZE.dp).testTag("effect$index").graphicsLayer {
                  clip = true
                  renderEffect = effect?.asComposeRenderEffect()
                }.drawBehind { drawChecker(translucent[index]) },
              )
            }
          }
        }
      }
    }
    // As in RuntimeShaderIntegerUniformAndroidHostTest: later tests in an SDK 37 sandbox otherwise
    // capture a blank frame.
    composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
    waitForIdle()
  }

  private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChecker(translucent: Boolean) {
    val tile = size.width / 4
    for (x in 0..3) for (y in 0..3) {
      val color = if ((x + y) % 2 == 0) Color(0xFF2050C0) else Color(0xFFE0A030)
      drawRect(if (translucent) color.copy(alpha = 0.4f) else color, Offset(x * tile, y * tile), Size(tile, tile))
    }
  }

  private fun ScreenshotUiTest.boxPixels(pixels: PixelMap, index: Int): List<Color> {
    val bounds = onNodeWithTag("effect$index").fetchSemanticsNode().boundsInRoot
    val left = bounds.left.roundToInt() + 1
    val top = bounds.top.roundToInt() + 1
    val size = bounds.width.roundToInt() - 2
    return (0 until size step 3).flatMap { y -> (0 until size step 3).map { x -> pixels[left + x, top + y] } }
  }

  private fun ScreenshotUiTest.assertMatches(pixels: PixelMap, actual: Int, expected: Int, label: String) {
    assertThat(difference(pixels, actual, expected), "$label max channel difference").isLessThanOrEqualTo(TOLERANCE)
  }

  /** The largest channel difference between two rendered boxes. */
  private fun ScreenshotUiTest.difference(pixels: PixelMap, first: Int, second: Int): Float =
    boxPixels(pixels, first).zip(boxPixels(pixels, second)).maxOf { (a, b) ->
      maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue), abs(a.alpha - b.alpha))
    }

  @Poko
  private class Case(val tint: Color, val noise: Float, val scale: Float, val translucent: Boolean = false)

  private companion object {
    const val TOLERANCE = 2f / 255
    const val BOX_SIZE = 24
    const val COLUMNS = 8

    // Created per effect: Robolectric does not keep native effects valid across tests.
    fun input(): RenderEffect = RenderEffect.createOffsetEffect(0f, 0f)
    val SRGB = Color(0.2f, 0.6f, 0.9f, 0.4f)
    val P3 = Color(0.9f, 0.2f, 0.1f, 0.5f, ColorSpaces.DisplayP3)
    val REFERENCE_CASES = listOf(
      Case(SRGB, noise = 0.3f, scale = 1f),
      Case(SRGB, noise = 0.3f, scale = 1f, translucent = true),
      Case(Color.Red.copy(alpha = 0.25f), noise = 0f, scale = 1f),
      Case(Color.Red.copy(alpha = 0.25f), noise = 0.1f, scale = 1f),
      Case(Color.Red.copy(alpha = 0.25f), noise = 0.5f, scale = 1f),
      Case(Color.Red.copy(alpha = 0.25f), noise = 1f, scale = 1f),
      Case(Color.Red.copy(alpha = 0.25f), noise = 1.5f, scale = 1f),
      Case(Color.Red.copy(alpha = 0.25f), noise = -0.5f, scale = 1f),
      Case(Color.Red.copy(alpha = 0.25f), noise = 0.5f, scale = 0.5f),
      Case(P3, noise = 0.3f, scale = 1f),
      Case(P3, noise = 0.3f, scale = 0.5f, translucent = true),
      Case(Color.Transparent, noise = 1f, scale = 1f),
      Case(Color.Transparent, noise = 1f, scale = 0.5f, translucent = true),
      Case(Color.Green, noise = 0.5f, scale = 1f),
    )
  }
}
