// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.test.Test

class RuntimeShaderIntegerUniformJvmTest {
  @Test
  fun modeOne_rendersGreen() = assertThat(draw(effect(1))).isEqualTo(Color.Green)

  @Test
  fun modeZero_rendersRedContent() = assertThat(draw(effect(0))).isEqualTo(Color.Red)

  private fun effect(mode: Int) = createRuntimeShaderRenderEffect(
    createRuntimeEffect("uniform shader content; uniform int mode; half4 main(float2 p) { return mode == 1 ? half4(0, 1, 0, 1) : content.eval(p); }"),
    arrayOf("content"),
    arrayOf(null),
  ) { setIntUniform("mode", mode) }

  private fun draw(effect: PlatformRenderEffect): Color {
    val image = ImageBitmap(8, 8)
    val bounds = Rect(0f, 0f, 8f, 8f)
    with(Canvas(image)) {
      saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = effect })
      drawRect(bounds, Paint().apply { color = Color.Red })
      restore()
    }
    return image.toPixelMap()[4, 4]
  }
}
