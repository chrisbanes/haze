// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import dev.chrisbanes.haze.RuntimeShaderUniformProvider

/** Records each array uniform by reference in [arrays], and accepts every other uniform. */
internal class ArrayCapturingUniforms(
  val arrays: MutableMap<String, FloatArray> = mutableMapOf(),
) : RuntimeShaderUniformProvider {
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

/** A four-member merged field, then a two-member one, for checking that array uploads are reused. */
internal val scratchReuseFields = listOf(
  mergedField(*Array(4) { mergedMember(it * 20f, 0f, 10f, 10f, radius = 3f) }),
  mergedField(*Array(2) { mergedMember(it * 20f, 0f, 10f, 10f, radius = 3f) }),
)
