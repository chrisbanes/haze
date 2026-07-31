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
 * A platform runtime shader or its native render-effect graph could not be constructed.
 *
 * This exception is limited to native construction calls so callers can recover without hiding
 * failures from uniform configuration or content drawing.
 */
@InternalHazeApi
public class RuntimeShaderRenderEffectException(
  cause: Throwable,
) : RuntimeException("Unable to construct a runtime shader render effect", cause)

@OptIn(InternalHazeApi::class)
internal inline fun <T> wrapRuntimeShaderConstruction(block: () -> T): T = try {
  block()
} catch (failure: RuntimeShaderRenderEffectException) {
  throw failure
} catch (failure: RuntimeException) {
  throw RuntimeShaderRenderEffectException(failure)
}

/**
 * Creates a [PlatformRuntimeEffect] from SKSL shader code.
 */
@InternalHazeApi
public expect fun createRuntimeEffect(sksl: String): PlatformRuntimeEffect

/** Returns whether runtime-shader render effects are supported on the current platform. */
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

/** A runtime shader render effect whose uniforms can be updated without recompiling its source. */
@InternalHazeApi
public interface MutableRuntimeShaderRenderEffect {
  /** Applies [uniforms] and returns the render effect containing the updated values. */
  public fun updateUniforms(
    uniforms: RuntimeShaderUniformProvider.() -> Unit,
  ): PlatformRenderEffect
}

/**
 * Creates a mutable-uniform runtime shader render effect with the given child shaders.
 *
 * Platform implementations may retain different resources while preserving the same update
 * semantics.
 */
@InternalHazeApi
public expect fun createMutableRuntimeShaderRenderEffect(
  effect: PlatformRuntimeEffect,
  shaderNames: Array<String>,
  inputs: Array<PlatformRenderEffect?>,
): MutableRuntimeShaderRenderEffect

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
   * Sets an int uniform.
   */
  public fun setIntUniform(name: String, value: Int)

  /**
   * Sets a child shader uniform.
   */
  public fun setChildShader(name: String, shader: Shader)
}
