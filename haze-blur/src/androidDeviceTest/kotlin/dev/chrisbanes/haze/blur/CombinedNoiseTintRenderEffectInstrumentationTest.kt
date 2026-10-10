// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.Poko
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test

/**
 * Hardware-canvas parity and snapshot isolation for the shared combined noise/tint shader.
 *
 * Member signatures use Compose's RenderEffect so the class still loads before API 31.
 */
class CombinedNoiseTintRenderEffectInstrumentationTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private var hardwareDraws = 0

  @Test
  @SdkSuppress(minSdkVersion = 33)
  fun sharedShader_matchesFreshReferenceAndRetainsSnapshots() {
    val cases = listOf(
      Case(Color(0.2f, 0.6f, 0.9f, 0.4f), noise = 0.3f, scale = 1f),
      Case(Color(0.2f, 0.6f, 0.9f, 0.4f), noise = 0.3f, scale = 1f, translucent = true),
      Case(Color.Red.copy(alpha = 0.25f), noise = 0.1f, scale = 1f),
      Case(Color.Red.copy(alpha = 0.25f), noise = 1.5f, scale = 0.5f),
      Case(Color(0.9f, 0.2f, 0.1f, 0.5f, ColorSpaces.DisplayP3), noise = 0.5f, scale = 1f),
      Case(Color.Transparent, noise = 1f, scale = 1f),
      Case(Color.Transparent, noise = 0f, scale = 1f),
      Case(Color.Green.copy(alpha = 0.5f), noise = 0.5f, scale = 0.5f, translucent = true),
    )
    val candidates = cases.map(::candidate)
    // Churn the shared shader as other nodes and animated cache misses do, then reuse old effects.
    repeat(24) { candidate(Case(Color.Blue.copy(alpha = it / 24f), noise = it / 23f, scale = if (it % 2 == 0) 1f else 0.5f)) }
    val boxes = cases.indices.flatMap { listOf(candidates[it] to cases[it], reference(cases[it]) to cases[it]) } +
      listOf(candidates[0] to cases[0], candidates[4] to cases[4], candidates[0] to cases[0], null to cases[0])
    val pixels = render(boxes)
    assertThat(hardwareDraws).isGreaterThan(0)

    cases.forEachIndexed { index, case ->
      assertThat(difference(pixels, 2 * index, 2 * index + 1), "$case vs fresh reference").isLessThanOrEqualTo(TOLERANCE)
    }
    val retained = 2 * cases.size
    assertThat(difference(pixels, retained, 1), "A after churn").isLessThanOrEqualTo(TOLERANCE)
    assertThat(difference(pixels, retained + 1, 9), "B after churn").isLessThanOrEqualTo(TOLERANCE)
    assertThat(difference(pixels, retained + 2, 1), "A again").isLessThanOrEqualTo(TOLERANCE)
    assertThat(difference(pixels, retained, retained + 1), "A and B differ").isGreaterThan(0.1f)
    // Controls: the effect is visible over unfiltered content, and noise alone changes output.
    assertThat(difference(pixels, retained, retained + 3), "tint control").isGreaterThan(0.1f)
    assertThat(difference(pixels, 10, 12), "noise control").isGreaterThan(0.02f)
  }

  @Test
  // RenderEffect itself needs API 31.
  @SdkSuppress(minSdkVersion = 31, maxSdkVersion = 32)
  fun combinedEffect_isUnavailableBeforeApi33() {
    val input = RenderEffect.createOffsetEffect(0f, 0f)
    assertThat(createCombinedNoiseTintRenderEffectOrNull(rule.activity, input, 0.5f, Color.Red, 1f)).isNull()
  }

  private fun candidate(case: Case): ComposeRenderEffect = checkNotNull(
    createCombinedNoiseTintRenderEffectOrNull(rule.activity, RenderEffect.createOffsetEffect(0f, 0f), case.noise, case.tint, case.scale),
  ).asComposeRenderEffect()

  /** The pre-reuse construction: a fresh RuntimeShader per effect. */
  private fun reference(case: Case): ComposeRenderEffect {
    val shader = RuntimeShader(COMBINED_NOISE_TINT_SKSL).apply {
      setInputShader("noise", rule.activity.createNoiseShader(case.scale))
      setFloatUniform("noiseAlpha", case.noise.coerceIn(0f, 1f))
      setColorUniform("tintColor", case.tint.toArgb())
    }
    return RenderEffect.createChainEffect(
      RenderEffect.createRuntimeShaderEffect(shader, "content"),
      RenderEffect.createOffsetEffect(0f, 0f),
    ).asComposeRenderEffect()
  }

  private fun render(boxes: List<Pair<ComposeRenderEffect?, Case>>): PixelMap {
    rule.setContent {
      Column(Modifier.background(Color.White).testTag(ROOT)) {
        boxes.chunked(COLUMNS).forEachIndexed { row, rowBoxes ->
          Row {
            rowBoxes.forEachIndexed { column, (effect, case) ->
              Box(
                Modifier.size(32.dp).testTag("effect${row * COLUMNS + column}").graphicsLayer {
                  clip = true
                  renderEffect = effect
                }.drawBehind {
                  assertThat(drawContext.canvas.nativeCanvas.isHardwareAccelerated, "hardware canvas").isTrue()
                  hardwareDraws++
                  val tile = size.width / 4
                  for (x in 0..3) for (y in 0..3) {
                    val color = if ((x + y) % 2 == 0) Color(0xFF2050C0) else Color(0xFFE0A030)
                    drawRect(if (case.translucent) color.copy(alpha = 0.4f) else color, Offset(x * tile, y * tile), Size(tile, tile))
                  }
                },
              )
            }
          }
        }
      }
    }
    rule.waitForIdle()
    val root = rule.onNodeWithTag(ROOT)
    val pixels = root.captureToImage().toPixelMap()
    val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
    bounds = boxes.indices.map { rule.onNodeWithTag("effect$it").fetchSemanticsNode().boundsInRoot.translate(-origin) }
    return pixels
  }

  private var bounds = emptyList<androidx.compose.ui.geometry.Rect>()

  private fun boxPixels(pixels: PixelMap, index: Int): List<Color> {
    val box = bounds[index]
    val left = box.left.roundToInt() + 1
    val top = box.top.roundToInt() + 1
    val size = box.width.roundToInt() - 2
    return (0 until size step 3).flatMap { y -> (0 until size step 3).map { x -> pixels[left + x, top + y] } }
  }

  /** The largest channel difference between two rendered boxes. */
  private fun difference(pixels: PixelMap, first: Int, second: Int): Float =
    boxPixels(pixels, first).zip(boxPixels(pixels, second)).maxOf { (a, b) ->
      maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue), abs(a.alpha - b.alpha))
    }

  @Poko
  private class Case(val tint: Color, val noise: Float, val scale: Float, val translucent: Boolean = false)

  private companion object {
    const val ROOT = "combined_noise_tint_root"
    const val COLUMNS = 6
    const val TOLERANCE = 2f / 255
  }
}
