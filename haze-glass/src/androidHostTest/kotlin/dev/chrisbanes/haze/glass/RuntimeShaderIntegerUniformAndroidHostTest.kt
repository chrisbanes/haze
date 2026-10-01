// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotNull
import dev.chrisbanes.haze.asComposeRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class RuntimeShaderIntegerUniformAndroidHostTest : ScreenshotTest() {
  @Test
  @Config(sdk = [35])
  fun nativeSetter_acceptsIntegerAndRejectsFloat() {
    val shader = RuntimeShader(MODE_SOURCE)
    shader.setIntUniform("mode", 1)
    assertThat(runCatching { shader.setFloatUniform("mode", 1f) }.exceptionOrNull()).isNotNull().isInstanceOf<IllegalArgumentException>()
  }

  @Test
  @Config(sdk = [35])
  fun hazeFactory_acceptsInteger() {
    hazeEffect(1)
  }

  @Test
  fun nativeModeOne_rendersGreen() = assertEffectColor(nativeEffect(1), Color.Green)

  @Test
  fun nativeModeZero_rendersRedContent() = assertEffectColor(nativeEffect(0), Color.Red)

  @Test
  fun unfilteredContent_rendersRed() = assertEffectColor(null, Color.Red)

  @Test
  fun nativeTransparentEffect_revealsMagentaUnderlay() {
    val shader = RuntimeShader(TRANSPARENT_SOURCE).apply { setIntUniform("mode", 0) }
    assertEffectColor(RenderEffect.createRuntimeShaderEffect(shader, "content"), Color.Magenta)
  }

  @Test
  fun unfilteredMagenta_rendersMagenta() = assertEffectColor(null, Color.Magenta, Color.Magenta)

  @Test
  fun hazeModeOne_rendersGreen() = assertEffectColor(hazeEffect(1), Color.Green)

  private fun hazeEffect(mode: Int): RenderEffect = createRuntimeShaderRenderEffect(
    createRuntimeEffect(MODE_SOURCE),
    arrayOf("content"),
    arrayOf(null),
  ) { setIntUniform("mode", mode) }

  private fun nativeEffect(mode: Int): RenderEffect = RenderEffect.createRuntimeShaderEffect(
    RuntimeShader(MODE_SOURCE).apply { setIntUniform("mode", mode) },
    "content",
  )

  private fun assertEffectColor(effect: RenderEffect?, expected: Color, content: Color = Color.Red) = runScreenshotTest {
    setContent {
      Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp).background(Color.Magenta))
        Box(
          Modifier.size(32.dp).testTag("filtered")
            .graphicsLayer { renderEffect = effect?.asComposeRenderEffect() }
            .background(content),
        )
      }
    }
    composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
    waitForIdle()
    val bounds = onNodeWithTag("filtered").fetchSemanticsNode().boundsInRoot
    val actual = captureRootPixels()[bounds.center.x.roundToInt(), bounds.center.y.roundToInt()]
    assertColor(actual, expected)
  }

  private fun assertColor(actual: Color, expected: Color) {
    assertThat(abs(actual.red - expected.red), "red: $actual").isLessThanOrEqualTo(1f / 255f)
    assertThat(abs(actual.green - expected.green), "green: $actual").isLessThanOrEqualTo(1f / 255f)
    assertThat(abs(actual.blue - expected.blue), "blue: $actual").isLessThanOrEqualTo(1f / 255f)
    assertThat(abs(actual.alpha - expected.alpha), "alpha: $actual").isLessThanOrEqualTo(1f / 255f)
  }

  companion object {
    private const val MODE_SOURCE = "uniform shader content; uniform int mode; half4 main(float2 p) { return mode == 1 ? half4(0, 1, 0, 1) : content.eval(p); }"
    private const val TRANSPARENT_SOURCE = "uniform shader content; uniform int mode; half4 main(float2 p) { return mode == 0 ? half4(0) : content.eval(p); }"
  }
}
