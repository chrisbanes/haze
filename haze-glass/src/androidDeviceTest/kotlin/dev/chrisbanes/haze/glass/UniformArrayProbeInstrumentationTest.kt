// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.chrisbanes.haze.asComposeRenderEffect
import org.junit.Rule
import org.junit.Test

/**
 * Probes whether `uniform float4 rects[2]` can be set with the platform
 * `RuntimeShader.setFloatUniform(String, FloatArray)` on a physical device.
 */
@SdkSuppress(minSdkVersion = 33)
@OptIn(ExperimentalTestApi::class)
class UniformArrayProbeInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun floatArrayUniform_isReadByShader() {
    // 8 floats: rects[0] = (3, 0, 0, 0), rects[1] = (0, 0, 7, 0)
    val matching = floatArrayOf(3f, 0f, 0f, 0f, 0f, 0f, 7f, 0f)
    val wrong = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 2f, 0f)

    val shader = RuntimeShader(SOURCE)
    assertThat(runCatching { shader.setFloatUniform("rects", matching) }.exceptionOrNull()).isNull()
    val control = RuntimeShader(SOURCE)
    assertThat(runCatching { control.setFloatUniform("rects", wrong) }.exceptionOrNull()).isNull()

    val effects = listOf(
      RenderEffect.createRuntimeShaderEffect(shader, "content"),
      RenderEffect.createRuntimeShaderEffect(control, "content"),
    )
    composeTestRule.setContent {
      Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Row {
          effects.forEachIndexed { index, effect ->
            Box(
              Modifier.size(32.dp).testTag("probe$index").graphicsLayer {
                clip = true
                renderEffect = effect.asComposeRenderEffect()
              }.background(Color.Red),
            )
          }
        }
      }
    }
    composeTestRule.waitForIdle()

    val colors = effects.indices.map { index ->
      val image = composeTestRule.onNodeWithTag("probe$index").captureToImage().toPixelMap()
      image[image.width / 2, image.height / 2]
    }
    assertThat(colors[0]).isEqualTo(Color.Green)
    assertThat(colors[1]).isEqualTo(Color.Red)
  }

  companion object {
    private const val SOURCE = "uniform shader content; uniform float4 rects[2]; " +
      "half4 main(float2 p) { return rects[1].z == 7.0 && rects[0].x == 3.0 ? half4(0, 1, 0, 1) : content.eval(p); }"
  }
}
