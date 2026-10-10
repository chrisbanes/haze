// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Probes whether `uniform float4 rects[2]` can be set with the platform
 * `RuntimeShader.setFloatUniform(String, FloatArray)` and read back by the shader.
 */
@RunWith(RobolectricTestRunner::class)
class UniformArrayProbeAndroidHostTest : ScreenshotTest() {
  // API 33 SkSL samples children with sample(child, p), so it gets its own source.
  @Test
  @Config(sdk = [33])
  fun sdk33_floatArrayUniform_isReadByShader() = assertArrayUniformProbe(source("sample(content, p)"))

  @Test
  @Config(sdk = [33])
  fun sdk33_evalMethodSyntax_isRejected() {
    val exception = runCatching { RuntimeShader(source(EVAL_CALL)) }.exceptionOrNull()
    assertThat(exception).isNotNull().isInstanceOf<IllegalArgumentException>()
    assertThat(exception!!.message.orEmpty()).contains("cannot swizzle value of type 'shader'")
  }

  @Test
  @Config(sdk = [37])
  fun sdk37_floatArrayUniform_isReadByShader() = assertArrayUniformProbe(source(EVAL_CALL))

  private fun assertArrayUniformProbe(source: String) {
    // 8 floats: rects[0] = (3, 0, 0, 0), rects[1] = (0, 0, 7, 0)
    val matching = floatArrayOf(3f, 0f, 0f, 0f, 0f, 0f, 7f, 0f)
    val wrong = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 2f, 0f)
    val red = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
    try {
      val paints = listOf(matching, wrong).map { rects ->
        val shader = RuntimeShader(source)
        assertThat(runCatching { shader.setFloatUniform("rects", rects) }.exceptionOrNull()).isNull()
        shader.setInputShader("content", BitmapShader(red, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        Paint().apply { setShader(shader) }
      }
      val colors = drawCenters(paints)
      assertThat(colors[0]).isEqualTo(Color.Green)
      assertThat(colors[1]).isEqualTo(Color.Red)
    } finally {
      red.recycle()
    }
  }

  private fun drawCenters(paints: List<Paint>): List<Color> {
    var result = emptyList<Color>()
    runScreenshotTest {
      setContent {
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
          Row {
            paints.forEachIndexed { index, paint ->
              Box(
                Modifier.size(32.dp).testTag("probe$index").drawBehind {
                  drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
                },
              )
            }
          }
        }
      }
      composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
      waitForIdle()
      val pixels = captureRootPixels()
      result = paints.indices.map { index ->
        val bounds = onNodeWithTag("probe$index").fetchSemanticsNode().boundsInRoot
        pixels[bounds.center.x.roundToInt(), bounds.center.y.roundToInt()]
      }
    }
    return result
  }

  companion object {
    private const val EVAL_CALL = "content.eval(p)"

    private fun source(contentCall: String) = "uniform shader content; uniform float4 rects[2]; " +
      "half4 main(float2 p) { return rects[1].z == 7.0 && rects[0].x == 3.0 ? half4(0, 1, 0, 1) : $contentCall; }"
  }
}
