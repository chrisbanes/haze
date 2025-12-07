// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Shader

/**
 * Platform-specific runtime effect type for compiling shaders.
 * - On Android (API 33+): Uses `android.graphics.RuntimeShader` directly
 * - On Skiko: `org.jetbrains.skia.RuntimeEffect`
 */
@InternalHazeApi
public expect class PlatformRuntimeEffect

/**
 * Creates a [PlatformRuntimeEffect] from SKSL shader code.
 */
@InternalHazeApi
public expect fun createRuntimeEffect(sksl: String): PlatformRuntimeEffect

@InternalHazeApi
public expect fun isRuntimeShaderRenderEffectSupported(): Boolean

/**
 * Creates a runtime shader [PlatformRenderEffect] with the given effect and child shaders.
 *
 * @param effect The runtime effect to use.
 * @param shaderNames Names of the shader uniforms in the SKSL code.
 * @param inputs The input render effects to use for each shader. Use null for the content shader.
 * @param uniforms Block to configure uniforms on the shader.
 */
@InternalHazeApi
public expect fun createRuntimeShaderRenderEffect(
  effect: PlatformRuntimeEffect,
  shaderNames: Array<String>,
  inputs: Array<PlatformRenderEffect?>,
  uniforms: RuntimeShaderUniformProvider.() -> Unit = {},
): PlatformRenderEffect

/**
 * Interface for setting uniforms on runtime shaders.
 */
@InternalHazeApi
public interface RuntimeShaderUniformProvider {
  /**
   * Sets a float uniform.
   */
  public fun setFloatUniform(name: String, value: Float)

  /**
   * Sets a float2 uniform.
   */
  public fun setFloatUniform(name: String, value1: Float, value2: Float)

  /**
   * Sets a float4 uniform.
   */
  public fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float)

  /**
   * Sets a child shader uniform.
   */
  public fun setChildShader(name: String, shader: Shader)
}
