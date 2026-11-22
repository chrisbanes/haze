// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedRuntimeShaderTest {
  @Test
  fun zeroFloats_isSharedPerSize() {
    val shader = SharedRuntimeShader("half4 main(float2 p) { return half4(0); }")

    assertThat(shader.zeroFloats(4)).isSameInstanceAs(shader.zeroFloats(4))
    assertThat(shader.zeroFloats(2)).isNotSameInstanceAs(shader.zeroFloats(4))
    assertThat(shader.zeroFloats(4).toList()).containsExactly(0f, 0f, 0f, 0f)
  }

  private fun child() = BitmapShader(
    Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
    Shader.TileMode.CLAMP,
    Shader.TileMode.CLAMP,
  )

  @Test
  fun createEffect_clearsBoundChildrenWhenALaterBindingFails() {
    val bound = mutableMapOf<String, Shader>()
    val real = child()
    val shader = SharedRuntimeShader(SKSL) { _, name, child ->
      if (child === real && name == "b") error("injected")
      bound[name] = child
    }
    val uniforms = RecordedUniforms().apply {
      setChildShader("a", real)
      setChildShader("b", real)
    }

    assertFailure { shader.createEffect(uniforms, arrayOf("a"), arrayOf(null)) }

    // Whatever was bound before the failure is back to the empty child.
    assertThat(bound.values.none { it === real }).isTrue()
    assertThat(bound.isNotEmpty()).isTrue()
  }

  @Test
  fun createEffect_nextEffectSeesOnlyItsOwnChildren() {
    val bound = mutableMapOf<String, Shader>()
    var fail = true
    val real = child()
    val shader = SharedRuntimeShader(SKSL) { _, name, child ->
      if (fail && name == "b" && child === real) error("injected")
      bound[name] = child
    }
    runCatching {
      shader.createEffect(
        RecordedUniforms().apply {
          setChildShader("a", real)
          setChildShader("b", real)
        },
        arrayOf("a"),
        arrayOf(null),
      )
    }
    fail = false

    val other = child()
    shader.createEffect(
      RecordedUniforms().apply { setChildShader("c", other) },
      arrayOf("c"),
      arrayOf(null),
    )

    assertThat(bound.filterValues { it === real }.isEmpty()).isTrue()
    assertThat(bound.filterValues { it === other }.isEmpty()).isTrue() // released after success
    assertThat(bound.containsKey("c")).isTrue()
  }

  private companion object {
    const val SKSL = "uniform shader a; uniform shader b; uniform shader c; half4 main(float2 p) { return half4(0); }"
  }
}
