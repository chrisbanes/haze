// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Shader
import kotlin.jvm.JvmInline
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

@InternalHazeApi
public actual typealias PlatformRuntimeEffect = RuntimeEffect

@InternalHazeApi
public actual fun createRuntimeEffect(sksl: String): PlatformRuntimeEffect {
  return RuntimeEffect.makeForShader(sksl)
}

@InternalHazeApi
public actual fun isRuntimeShaderRenderEffectSupported(): Boolean = true

@InternalHazeApi
public actual fun createRuntimeShaderRenderEffect(
  effect: PlatformRuntimeEffect,
  shaderNames: Array<String>,
  inputs: Array<PlatformRenderEffect?>,
  uniforms: RuntimeShaderUniformProvider.() -> Unit,
): PlatformRenderEffect {
  val builder = RuntimeShaderBuilder(effect)
  SkikoRuntimeShaderUniformProvider(builder).also(uniforms)

  return ImageFilter.makeRuntimeShader(
    runtimeShaderBuilder = builder,
    shaderNames = shaderNames,
    inputs = inputs,
  )
}

@JvmInline
private value class SkikoRuntimeShaderUniformProvider(
  private val builder: RuntimeShaderBuilder,
) : RuntimeShaderUniformProvider {
  override fun setFloatUniform(name: String, value: Float) {
    builder.uniform(name, value)
  }

  override fun setFloatUniform(name: String, value1: Float, value2: Float) {
    builder.uniform(name, value1, value2)
  }

  override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
    builder.uniform(name, value1, value2, value3, value4)
  }

  override fun setChildShader(name: String, shader: Shader) {
    builder.child(name, shader)
  }
}
