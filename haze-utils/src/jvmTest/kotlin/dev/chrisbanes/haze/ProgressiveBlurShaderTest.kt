// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface

class ProgressiveBlurShaderTest {
  @Test
  fun defaultShader_guardsOnlyOnWindowsJvm() {
    val windows = System.getProperty("os.name").startsWith("Windows")
    assertThat(avoidProgressiveBlurLoopBreak).isEqualTo(windows)
    assertThat(makeProgressiveBlurSksl(vertical = false))
      .isEqualTo(makeProgressiveBlurSksl(vertical = false, avoidLoopBreak = windows))
  }

  @Test
  fun guardedAndEarlyExitLoops_renderIdenticalPixels() {
    RuntimeEffect.makeForShader("half4 main(float2 p) { return half4(fract(p.x / 8.0), fract(p.y / 8.0), 0.5, 1.0); }").use { contentEffect ->
      RuntimeEffect.makeForShader("half4 main(float2 p) { return half4(clamp(p.y / 24.0, 0.0, 1.0)); }").use { maskEffect ->
        RuntimeShaderBuilder(contentEffect).makeShader().use { content ->
          RuntimeShaderBuilder(maskEffect).makeShader().use { mask ->
            for (vertical in listOf(false, true)) {
              for (radius in listOf(0f, 1f, 24f, 25f, 149f, 150f, 151f)) {
                fun render(guarded: Boolean): List<Byte> {
                  RuntimeEffect.makeForShader(makeProgressiveBlurSksl(vertical, guarded)).use { effect ->
                    val builder = RuntimeShaderBuilder(effect)
                    builder.uniform("blurRadius", radius)
                    builder.uniform("crop", 0.25f, 0.25f, 31.75f, 23.75f)
                    builder.uniform("maskOrigin", 0f, 0f)
                    builder.child("content", content)
                    builder.child("mask", mask)
                    builder.makeShader().use { shader ->
                      Surface.makeRasterN32Premul(32, 24).use { surface ->
                        Paint().use { paint ->
                          paint.shader = shader
                          surface.canvas.drawRect(Rect.makeWH(32f, 24f), paint)
                        }
                        surface.makeImageSnapshot().use { image ->
                          image.encodeToData()!!.use { data -> return data.bytes.toList() }
                        }
                      }
                    }
                  }
                }
                assertThat(render(true), "vertical=$vertical radius=$radius").isEqualTo(render(false))
              }
            }
          }
        }
      }
    }
  }
}
