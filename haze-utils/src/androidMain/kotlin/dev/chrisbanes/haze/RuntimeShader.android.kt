// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Shader

/**
 * On Android, we use RuntimeShader directly rather than a RuntimeEffect.
 * This class wraps the SKSL to create RuntimeShader instances on demand.
 */
@InternalHazeApi
public actual class PlatformRuntimeEffect(internal val sksl: String)

@InternalHazeApi
public actual fun createRuntimeEffect(sksl: String): PlatformRuntimeEffect {
  return PlatformRuntimeEffect(sksl)
}

@ChecksSdkIntAtLeast(api = 33)
@InternalHazeApi
public actual fun isRuntimeShaderRenderEffectSupported(): Boolean = Build.VERSION.SDK_INT >= 33

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@InternalHazeApi
public actual fun createRuntimeShaderRenderEffect(
  effect: PlatformRuntimeEffect,
  shaderNames: Array<String>,
  inputs: Array<PlatformRenderEffect?>,
  uniforms: RuntimeShaderUniformProvider.() -> Unit,
): PlatformRenderEffect {
  val shader = RuntimeShader(effect.sksl)
  val provider = AndroidRuntimeShaderUniformProvider(shader)
  uniforms(provider)

  // Chain all input RenderEffects together using .then()
  val chainedInput = inputs.filterNotNull().reduceOrNull { acc, input ->
    acc.then(input)
  }

  return RenderEffect.createRuntimeShaderEffect(shader, "content").let { content ->
    chainedInput?.then(content) ?: content
  }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AndroidRuntimeShaderUniformProvider(
  private val shader: RuntimeShader,
) : RuntimeShaderUniformProvider {
  override fun setFloatUniform(name: String, value: Float) {
    shader.setFloatUniform(name, value)
  }

  override fun setFloatUniform(name: String, value1: Float, value2: Float) {
    shader.setFloatUniform(name, value1, value2)
  }

  override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
    shader.setFloatUniform(name, value1, value2, value3, value4)
  }

  override fun setChildShader(name: String, shader: Shader) {
    this.shader.setInputShader(name, shader)
  }
}
