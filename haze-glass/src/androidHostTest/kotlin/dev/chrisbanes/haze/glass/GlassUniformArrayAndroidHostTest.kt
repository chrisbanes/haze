// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import dev.chrisbanes.haze.asComposeRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Proves `uniform float4 values[4]` with a constant-bound loop and a count guard, set through
 * [RuntimeShaderUniformProvider.setFloatUniform] with a [FloatArray].
 *
 * The source never samples a child, because Robolectric SDK 33 rejects `content.eval`.
 */
@RunWith(RobolectricTestRunner::class)
class GlassUniformArrayAndroidHostTest : ScreenshotTest() {
  // Robolectric SDK 33 has no native RenderEffect.createRuntimeShaderEffect, so the RenderEffect
  // path starts at SDK 35. The rim adapter below renders the same source at SDK 33.
  @Test
  @Config(sdk = [35])
  fun recordedUniforms_sdk35_readsOneToFourMembers() = assertRecordedUniforms()

  @Test
  @Config(sdk = [37])
  fun recordedUniforms_sdk37_readsOneToFourMembers() = assertRecordedUniforms()

  @Test
  @Config(sdk = [33])
  fun rimAdapter_sdk33_readsOneToFourMembers() = assertRimAdapter()

  @Test
  @Config(sdk = [37])
  fun rimAdapter_sdk37_readsOneToFourMembers() = assertRimAdapter()

  @Test
  @Config(sdk = [37])
  fun recordedUniforms_shorterRecordedSetZeroesArray() {
    val source = createRuntimeEffect(UniformArrayProbe.SOURCE)
    val full = createRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null)) {
      UniformArrayProbe.set(this, count = 4)
    }
    // Records only the count, so the shared shader must zero the array set by `full`.
    val shorter = createRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null)) {
      setFloatUniform("count", floatArrayOf(4f))
      setFloatUniform("expected", floatArrayOf(0f))
    }
    assertThat(drawEffects(listOf(full, shorter, full))).isEqualTo(List(3) { Color.Green })
  }

  private fun assertRecordedUniforms() {
    val source = createRuntimeEffect(UniformArrayProbe.SOURCE)
    val effects = (1..4).map { count ->
      createRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null)) {
        UniformArrayProbe.set(this, count)
      }
    }
    val wrong = createRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null)) {
      UniformArrayProbe.set(this, count = 4)
      setFloatUniform("expected", floatArrayOf(UniformArrayProbe.expected(3)))
    }
    assertThat(drawEffects(effects + wrong)).isEqualTo(List(4) { Color.Green } + Color.Red)
  }

  private fun assertRimAdapter() {
    val shaders = (1..4).map { count ->
      RuntimeShader(UniformArrayProbe.SOURCE).also { UniformArrayProbe.set(rimProvider(it), count) }
    }
    val wrong = RuntimeShader(UniformArrayProbe.SOURCE).also {
      val provider = rimProvider(it)
      UniformArrayProbe.set(provider, count = 4)
      provider.setFloatUniform("expected", floatArrayOf(UniformArrayProbe.expected(3)))
    }
    assertThat(drawShaders(shaders + wrong)).isEqualTo(List(4) { Color.Green } + Color.Red)
  }

  private fun drawEffects(effects: List<RenderEffect>): List<Color> = drawCells(effects.size) { index ->
    Modifier.graphicsLayer {
      clip = true
      renderEffect = effects[index].asComposeRenderEffect()
    }.background(Color.Blue)
  }

  private fun drawShaders(shaders: List<RuntimeShader>): List<Color> = drawCells(shaders.size) { index ->
    Modifier.drawBehind {
      drawIntoCanvas {
        it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, Paint().apply { shader = shaders[index] })
      }
    }
  }

  private fun drawCells(count: Int, cell: (Int) -> Modifier): List<Color> {
    var result = emptyList<Color>()
    runScreenshotTest {
      setContent {
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
          Row {
            repeat(count) { index ->
              Box(Modifier.size(16.dp).testTag("cell$index").then(cell(index)))
            }
          }
        }
      }
      composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
      waitForIdle()
      val pixels = captureRootPixels()
      result = List(count) { index ->
        val bounds = onNodeWithTag("cell$index").fetchSemanticsNode().boundsInRoot
        pixels[bounds.center.x.roundToInt(), bounds.center.y.roundToInt()]
      }
    }
    return result
  }

  private fun rimProvider(shader: RuntimeShader): RuntimeShaderUniformProvider {
    val constructor = Class.forName("dev.chrisbanes.haze.glass.GlassRimUniformProvider")
      .getDeclaredConstructor(RuntimeShader::class.java)
    constructor.isAccessible = true
    val instance = constructor.newInstance(shader)
    assertThat(instance).isInstanceOf<RuntimeShaderUniformProvider>()
    return instance as RuntimeShaderUniformProvider
  }
}
