// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Shader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.roundToInt
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [31, 32, 35])
class BlurGradientCoordinatesAndroidTest : ScreenshotTest() {
  @Test
  fun absoluteMask_preservesHeaderFadeAcrossModesAndDensities() = assertFade(masked = true, progressive = false)

  @Test
  @Config(sdk = [35])
  fun absoluteProgressive_preservesHeaderFadeAcrossModesAndDensities() = assertFade(masked = false, progressive = true)

  @Test
  @Config(sdk = [35])
  fun combinedMaskAndProgressive_preserveHeaderFadeAcrossModesAndDensities() = assertFade(masked = true, progressive = true)

  @Test
  fun relativeEndpoints_preserveHeaderFadeAcrossModesAndDensities() = assertFade(masked = true, progressive = false, relative = true)

  @Test
  fun absoluteBrushTint_preservesHeaderFadeAcrossModesAndDensities() = assertFade(masked = false, progressive = false, brushTint = true)

  @Test
  fun sharedTranslatedShader_preservesHeaderFadeAndCallerMatrix() = assertFade(masked = true, progressive = false, custom = true)

  @Test
  fun sharedProgressiveShader_preservesHeaderFadeAndCallerMatrix() = assertFade(masked = false, progressive = true, custom = true)

  @Test
  fun sharedTintShader_preservesHeaderFadeAndCallerMatrix() = assertFade(masked = false, progressive = false, brushTint = true, custom = true)

  @Test
  fun nestedShader_preservesChildTranslations() = assertFade(masked = true, progressive = false, custom = true, nested = true)

  private fun assertFade(
    masked: Boolean,
    progressive: Boolean,
    relative: Boolean = false,
    brushTint: Boolean = false,
    custom: Boolean = false,
    nested: Boolean = false,
  ) = runScreenshotTest {
    var mode by mutableStateOf<HazePerformanceMode>(HazePerformanceMode.Quality)
    var density by mutableStateOf(1f)
    var sharedShader: Shader? = null
    var requestedShaderSize: Size? = null
    val state = HazeState()
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(density)) {
        val start = if (relative) 0f else 80f * density
        val end = if (relative) Float.POSITIVE_INFINITY else 100f * density
        val customMask = remember(density) {
          val shader = LinearGradient(
            0f,
            60f * density,
            0f,
            80f * density,
            Color.White.toArgb(),
            Color.Transparent.toArgb(),
            Shader.TileMode.CLAMP,
          ).apply { setLocalMatrix(Matrix().apply { setTranslate(0f, 20f * density) }) }
          sharedShader = shader
          val maskShader = if (nested) {
            val second = LinearGradient(
              0f,
              40f * density,
              0f,
              60f * density,
              Color.White.toArgb(),
              Color.Transparent.toArgb(),
              Shader.TileMode.CLAMP,
            ).apply { setLocalMatrix(Matrix().apply { setTranslate(0f, 40f * density) }) }
            ComposeShader(shader, second, PorterDuff.Mode.DST_IN)
          } else {
            shader
          }
          object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
              requestedShaderSize = size
              return maskShader
            }
          }
        }
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
          Box(Modifier.size(160.dp).hazeSource(state).background(Color.Black))
          Box(
            Modifier.size(100.dp).testTag("fade-header").clipToBounds().hazeBlur(
              input = HazeInput.Sources(state),
              style = HazeBlurStyle {
                backgroundColor(Color.Transparent)
                blurRadius(4.8.dp)
                noiseFactor(0f)
                alpha(if (custom && brushTint) 0.4f else 1f)
                colorEffects(
                  listOf(
                    if (brushTint) {
                      HazeColorEffect.tint(
                        if (custom) customMask else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent), startY = start, endY = end),
                      )
                    } else {
                      HazeColorEffect.tint(Color.White.copy(alpha = 0.4f))
                    },
                  ),
                )
                if (masked) {
                  mask(if (custom) customMask else Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = start, endY = end))
                }
                if (progressive) {
                  progressive(
                    if (custom) {
                      HazeProgressive.Brush(customMask)
                    } else {
                      HazeProgressive.verticalGradient(
                        easing = LinearEasing,
                        startY = start,
                        startIntensity = 1f,
                        endY = end,
                        endIntensity = 0f,
                      )
                    },
                  )
                }
              },
              performanceMode = mode,
            ),
          )
        }
      }
    }
    for (currentDensity in listOf(1f, 2f)) {
      density = currentDensity
      for (currentMode in listOf(
        HazePerformanceMode.Quality,
        HazePerformanceMode.Balanced,
        HazePerformanceMode.Performance,
        HazePerformanceMode.Balanced,
        HazePerformanceMode.Quality,
      )) {
        mode = currentMode
        waitForIdle()
        val pixels = captureRootPixels()
        val bounds = onNodeWithTag("fade-header").fetchSemanticsNode().boundsInRoot
        val x = bounds.center.x.roundToInt()
        for (fraction in listOf(0.7f, 0.9f, 0.98f)) {
          val y = (bounds.top + bounds.height * fraction).roundToInt()
          val localY = (y + 0.5f - bounds.top) / currentDensity
          val fade = if (relative) 1f - localY / 100f else ((100f - localY) / 20f).coerceIn(0f, 1f)
          val expected = 0.4f * fade * if ((masked && progressive) || nested) fade else 1f
          val label = "mask=$masked progressive=$progressive relative=$relative density=$currentDensity mode=$currentMode y=$localY"
          assertThat(pixels[x, y].red, label).isCloseTo(expected, 0.035f)
        }
        if (custom) {
          val values = FloatArray(9)
          val matrix = Matrix()
          sharedShader!!.getLocalMatrix(matrix)
          matrix.getValues(values)
          assertThat(values[Matrix.MSCALE_X], "caller matrix x scale").isEqualTo(1f)
          assertThat(values[Matrix.MSCALE_Y], "caller matrix y scale").isEqualTo(1f)
          assertThat(values[Matrix.MTRANS_Y], "caller matrix translation").isEqualTo(20f * currentDensity)
          assertThat(requestedShaderSize, "shader receives displayed content size").isEqualTo(Size(100f * currentDensity, 100f * currentDensity))
        }
      }
    }
  }
}
