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
import assertk.assertions.each
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import assertk.assertions.prop
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
    // Keyed on the name, as a real unknown-child failure is; "a" binds before "b" (single-key order).
    val shader = SharedRuntimeShader(SKSL) { _, name, child ->
      if (name == "b") error("injected")
      bound[name] = child
    }
    val uniforms = RecordedUniforms().apply {
      setChildShader("a", real)
      setChildShader("b", real)
    }

    assertFailure { shader.createEffect(uniforms, arrayOf("a"), arrayOf(null)) }

    assertThat(bound["a"]).isNotNull().isNotSameInstanceAs(real)
  }

  @Test
  fun createEffect_nextEffectSeesOnlyItsOwnChildren() {
    val bound = mutableMapOf<String, Shader>()
    var failOnB = true
    var seenWhenBindingC: Map<String, Shader>? = null
    val real = child()
    val other = child()
    val shader = SharedRuntimeShader(SKSL) { _, name, child ->
      if (failOnB && name == "b") error("injected")
      if (child === other) seenWhenBindingC = bound.toMap()
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
    failOnB = false

    shader.createEffect(
      RecordedUniforms().apply { setChildShader("c", other) },
      arrayOf("c"),
      arrayOf(null),
    )

    // The second effect is built with no child left over from the failed one...
    assertThat(seenWhenBindingC).isNotNull().prop("values") { it.values.toList() }
      .each { it.isNotSameInstanceAs(real) }
    // ...and its own child is released once it is built.
    assertThat(bound["c"]).isNotNull().isNotSameInstanceAs(other)
  }

  private companion object {
    const val SKSL = "uniform shader a; uniform shader b; uniform shader c; half4 main(float2 p) { return half4(0); }"
  }
}
