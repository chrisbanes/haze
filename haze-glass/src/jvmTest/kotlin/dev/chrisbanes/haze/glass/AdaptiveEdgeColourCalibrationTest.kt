// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

class AdaptiveEdgeColourCalibrationTest {
  @Test
  fun taggedSrgbChild_reportsWorkingColourConvention() {
    val shader = RuntimeEffect.makeForShader(
      """
      uniform shader content;
      half4 main(float2 p) {
        half4 sample = content.eval(p);
        half3 decoded = toLinearSrgb(sample.rgb / sample.a);
        return half4(sample.r / sample.a, decoded.r, 0, 1);
      }
      """.trimIndent(),
    )
    val filter = createRuntimeShaderRenderEffect(shader, arrayOf("content"), arrayOf(null)) {}
    val image = ImageBitmap(8, 8)
    assertThat(image.colorSpace).isEqualTo(ColorSpaces.Srgb)
    val bounds = Rect(0f, 0f, 8f, 8f)
    val paint = Paint().apply { skiaPaint.imageFilter = filter }
    with(Canvas(image)) {
      saveLayer(bounds, paint)
      drawRect(bounds, Paint().apply { color = Color(0.5f, 0.5f, 0.5f) })
      restore()
    }
    val pixel = image.toPixelMap()[4, 4]
    System.getenv("HAZE_T2_ARTIFACT_DIR")?.let { output ->
      File(output).mkdirs()
      File(output, "t1-skiko-colour-probe.txt").writeText(
        "sRGB input=0.5; raw=${pixel.red}; toLinearSrgb=${pixel.green}; output alpha=${pixel.alpha}\n",
      )
    }
    assertThat(abs(pixel.red - 128f / 255f)).isLessThanOrEqualTo(1f / 255f)
    val linear = Math.pow((128.0 / 255.0 + 0.055) / 1.055, 2.4).toFloat()
    assertThat(abs(pixel.green - linear)).isLessThanOrEqualTo(2f / 255f)
    assertThat(abs(pixel.alpha - 1f)).isLessThanOrEqualTo(1f / 255f)
  }
}
