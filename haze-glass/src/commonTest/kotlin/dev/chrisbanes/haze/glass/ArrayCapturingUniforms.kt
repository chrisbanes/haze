// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.RuntimeShaderUniformProvider

/** Records each array uniform by reference in [arrays], and accepts every other uniform. */
internal class ArrayCapturingUniforms : RuntimeShaderUniformProvider {
  val arrays = mutableMapOf<String, FloatArray>()

  override fun setColorUniform(name: String, color: Color) = Unit

  override fun setFloatUniform(name: String, value: Float) = Unit

  override fun setFloatUniform(name: String, value1: Float, value2: Float) = Unit

  override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) = Unit

  override fun setFloatUniform(name: String, values: FloatArray) {
    arrays[name] = values
  }

  override fun setIntUniform(name: String, value: Int) = Unit

  override fun setChildShader(name: String, shader: Shader) = Unit
}

/**
 * Asserts that [upload] sends the same merged-field arrays through [uniforms] for a four-member field
 * and then a two-member one.
 */
internal fun assertReusesMergedFieldArrays(uniforms: ArrayCapturingUniforms, upload: (GlassMergedField) -> Unit) {
  val uploads = listOf(4, 2).map { count ->
    uniforms.arrays.clear()
    upload(mergedField(*Array(count) { mergedMember(it * 20f, 0f, 10f, 10f, radius = 3f) }))
    uniforms.arrays.toMap()
  }
  assertThat(uploads[0].keys).contains("memberRects")
  assertThat(uploads[1].keys).isEqualTo(uploads[0].keys)
  for ((name, array) in uploads[0]) assertThat(uploads[1].getValue(name), name).isSameInstanceAs(array)
}
