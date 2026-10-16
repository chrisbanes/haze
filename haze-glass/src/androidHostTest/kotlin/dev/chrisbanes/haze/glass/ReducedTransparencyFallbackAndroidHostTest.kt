// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [31])
class ReducedTransparencyFallbackAndroidHostTest : ScreenshotTest() {
  @Test
  fun unsupportedRuntime_hidesDetailedSourcesAndPreservesAuthoredColoursAndAlpha() = runScreenshotTest {
    val state = HazeState()
    val effect = GlassRuntimeEffect()
    val source = mutableStateOf(Color.Red)
    val alpha = mutableStateOf(1f)
    val authored = mutableStateOf(false)
    val settings = mutableStateOf(GlassAccessibilitySettings(reduceTransparency = true))
    val background = Color.Red.copy(alpha = 0.25f)
    val tint = Color.Blue.copy(alpha = 0.5f)
    var density = 1f
    setContent {
      density = LocalDensity.current.density
      Box(Modifier.size(120.dp).background(Color.Green)) {
        Box(
          Modifier.size(120.dp).hazeSource(state).drawBehind {
            val cell = 10.dp.toPx()
            for (x in 0 until 12) for (y in 0 until 12) {
              drawRect(
                if ((x + y) % 2 == 0) source.value else Color.Magenta,
                Offset(x * cell, y * cell),
                Size(cell, cell),
              )
            }
          },
        )
        Box(Modifier.size(120.dp).background(Color.Green))
        CompositionLocalProvider(LocalGlassAccessibilitySettings provides settings.value) {
          Box(
            Modifier.size(120.dp).hazeGlass(
              factory = HazeEffectFactory { effect },
              input = HazeInput.Sources(state),
              style = GlassStyle {
                backgroundColor(if (authored.value) background else Color.Transparent)
                tint(if (authored.value) tint else Color.Transparent)
                alpha(alpha.value)
                specularIntensity(0f)
                edgeSoftness(0.dp)
                edgeShadow(Color.Transparent)
                ambientResponse(0f)
              },
              performanceMode = null,
              expandLayerBounds = true,
              interactionSource = null,
            ),
          )
        }
      }
    }
    fun pixel() = captureRootPixels()[(55 * density).toInt(), (55 * density).toInt()]
    assertThat(pixel()).isEqualTo(Color.White)
    assertThat(effect.delegate).isInstanceOf<FallbackGlassDelegate>()
    source.value = Color.Blue
    assertThat(pixel()).isEqualTo(Color.White)
    authored.value = true
    val expected = tint.compositeOver(background.compositeOver(Color.White))
    var actual = pixel()
    assertThat(actual.red).isCloseTo(expected.red, 2f / 255f)
    assertThat(actual.green).isCloseTo(expected.green, 2f / 255f)
    assertThat(actual.blue).isCloseTo(expected.blue, 2f / 255f)
    alpha.value = 0.5f
    actual = pixel()
    val faded = expected.copy(alpha = 0.5f).compositeOver(Color.Green)
    assertThat(actual.red).isCloseTo(faded.red, 2f / 255f)
    assertThat(actual.green).isCloseTo(faded.green, 2f / 255f)
    assertThat(actual.blue).isCloseTo(faded.blue, 2f / 255f)
    alpha.value = 0f
    assertThat(pixel()).isEqualTo(Color.Green)
    alpha.value = 1f
    settings.value = GlassAccessibilitySettings()
    authored.value = true
    actual = pixel()
    val ordinary = tint.compositeOver(Color.Blue)
    assertThat(actual.red).isCloseTo(ordinary.red, 2f / 255f)
    assertThat(actual.blue).isCloseTo(ordinary.blue, 2f / 255f)
  }
}
