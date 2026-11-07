// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
@SdkSuppress(minSdkVersion = 33, maxSdkVersion = 33)
class BlurCaptureCoordinatesApi33InstrumentationTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun translatedCustomMask_retainsPixelsAndCallerMatrixAcrossGeometryChanges() {
    val source = LinearGradient(0f, 4f, 0f, 20f, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
    source.setLocalMatrix(Matrix().apply { setTranslate(0f, 2f) })
    fun matrix() = FloatArray(9).also { values -> Matrix().also { source.getLocalMatrix(it) }.getValues(values) }.toList()
    val originalMatrix = matrix()
    val brush = object : ShaderBrush() {
      override fun createShader(size: Size): Shader = source
    }
    fun effect(layer: Size, offset: Offset): RenderEffect {
      val capture = Size(layer.width.roundToInt().toFloat(), layer.height.roundToInt().toFloat())
      return createRenderEffect(
        context = rule.activity,
        density = Density(1f),
        params = RenderEffectParams(
          blurRadius = 0.dp,
          noiseFactor = 0f,
          scale = 1f,
          contentSize = Size(21f, 19f),
          contentOffset = offset,
          captureScale = ScaleFactor(capture.width / layer.width, capture.height / layer.height),
          captureSize = capture,
          mask = brush,
          blurTileMode = TileMode.Clamp,
        ),
      )
    }
    val first = effect(Size(25.2f, 23.4f), Offset(2.25f, 4.5f))
    var effects by mutableStateOf(listOf(first))
    rule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Row(Modifier.background(Color.Black)) {
          effects.forEachIndexed { index, current ->
            Box(
              Modifier.size(27.dp, 25.dp).testTag("effect$index").graphicsLayer {
                renderEffect = current.asComposeRenderEffect()
              }.background(Color.White),
            )
          }
        }
      }
    }
    rule.waitForIdle()
    val before = rule.onNodeWithTag("effect0").captureToImage().toPixelMap()
    val beforeRows = (12..21).map { before[10, it] }
    val second = effect(Size(26.4f, 24.6f), Offset(1.5f, 3.25f))
    rule.runOnIdle { effects = listOf(first, second, first) }
    rule.waitForIdle()
    val a = rule.onNodeWithTag("effect0").captureToImage().toPixelMap()
    val b = rule.onNodeWithTag("effect1").captureToImage().toPixelMap()
    val again = rule.onNodeWithTag("effect2").captureToImage().toPixelMap()
    val scaleY = 23f / 23.4f
    for (y in 12..21) {
      val expected = ((22f - ((y + 0.5f) / scaleY - 4.5f)) / 16f).coerceIn(0f, 1f)
      assertThat(a[10, y].red, "authored mask y=$y").isCloseTo(expected, 0.025f)
      assertThat(again[10, y], "retained effect y=$y").isEqualTo(beforeRows[y - 12])
    }
    assertThat(abs(a[10, 17].red - b[10, 17].red), "alternate geometry changes output").isGreaterThan(0.025f)
    assertThat(matrix(), "caller shader matrix").isEqualTo(originalMatrix)
  }

  @Test
  fun qualityProgressive_blursStripedInputMoreAtHighIntensity() = progressiveIntensity(HazePerformanceMode.Quality)

  @Test
  fun performanceProgressive_blursStripedInputMoreAtHighIntensity() = progressiveIntensity(HazePerformanceMode.Performance)

  private fun progressiveIntensity(mode: HazePerformanceMode) {
    rule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Box(
          Modifier.size(80.dp).testTag("progressive")
            .hazeBlur(
              input = HazeInput.Content,
              performanceMode = mode,
              style = HazeBlurStyle {
                blurRadius(8.dp)
                noiseFactor(0f)
                backgroundColor(Color.Transparent)
                colorEffects(emptyList())
                progressive(HazeProgressive.verticalGradient(startY = 0f, endY = 80f, startIntensity = 1f, endIntensity = 0f))
              },
            ).drawBehind {
              drawRect(Color.White)
              for (x in 0 until 80 step 16) {
                drawRect(Color.Black, topLeft = Offset(x.toFloat(), 0f), size = Size(8f, 80f))
              }
            },
        )
      }
    }
    rule.waitForIdle()
    val pixels = rule.onNodeWithTag("progressive").captureToImage().toPixelMap()
    assertThat(pixels[17, 8].red - pixels[17, 70].red, "$mode progressive intensity").isGreaterThan(0.05f)
  }
}
