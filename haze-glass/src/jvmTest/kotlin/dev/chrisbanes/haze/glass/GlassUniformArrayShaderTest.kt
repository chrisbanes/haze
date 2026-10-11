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

/** Proves `float4[4]` uniforms read in a count-guarded loop through the Skiko uniform provider. */
class GlassUniformArrayShaderTest {
  private val source = createRuntimeEffect(UniformArrayProbe.SOURCE)

  @Test
  fun floatArrayUniform_readsOneToFourMembers() {
    for (count in 1..4) {
      val effect = createRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null)) {
        UniformArrayProbe.set(this, count)
      }
      assertThat(draw(effect), "count $count").isEqualTo(Color.Green)
    }
  }

  @Test
  fun floatArrayUniform_countGuardIgnoresLaterSlots() {
    val effect = createRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null)) {
      UniformArrayProbe.set(this, count = 3)
      setFloatUniform("expected", floatArrayOf(UniformArrayProbe.expected(4)))
    }
    assertThat(draw(effect)).isEqualTo(Color.Red)
  }

  private fun draw(effect: PlatformRenderEffect): Color {
    val image = ImageBitmap(8, 8)
    val bounds = Rect(0f, 0f, 8f, 8f)
    with(Canvas(image)) {
      saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = effect })
      drawRect(bounds, Paint().apply { color = Color.Blue })
      restore()
    }
    return image.toPixelMap()[4, 4]
  }
}
