// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.toAndroidColorSpace

/**
 * On Android, we use RuntimeShader directly rather than a RuntimeEffect.
 * This class wraps the SKSL and the [SharedRuntimeShader] compiled from it.
 */
@InternalHazeApi
public actual class PlatformRuntimeEffect(internal val sksl: String) {
  private val lock = Any()
  private var sharedShader: Any? = null

  /** The shader compiled from [sksl], compiling it on first use. */
  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  internal fun sharedShader(): SharedRuntimeShader = synchronized(lock) {
    sharedShader as SharedRuntimeShader? ?: SharedRuntimeShader(sksl).also { sharedShader = it }
  }
}

@InternalHazeApi
public actual fun createRuntimeEffect(sksl: String): PlatformRuntimeEffect {
  return PlatformRuntimeEffect(sksl)
}

/** Returns whether the device supports Android runtime-shader render effects. */
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
  val recorded = RecordedUniforms().apply(uniforms)
  val shader = wrapRuntimeShaderConstruction {
    effect.sharedShader()
  }
  return shader.createEffect(recorded, shaderNames, inputs)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@InternalHazeApi
public actual fun createMutableRuntimeShaderRenderEffect(
  effect: PlatformRuntimeEffect,
  shaderNames: Array<String>,
  inputs: Array<PlatformRenderEffect?>,
): MutableRuntimeShaderRenderEffect = wrapRuntimeShaderConstruction {
  AndroidMutableRuntimeShaderRenderEffect(
    effect = effect,
    shaderNames = shaderNames,
    inputs = inputs,
  )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AndroidMutableRuntimeShaderRenderEffect(
  effect: PlatformRuntimeEffect,
  private val shaderNames: Array<String>,
  inputs: Array<PlatformRenderEffect?>,
) : MutableRuntimeShaderRenderEffect {
  private val uniforms = RecordedUniforms()
  private var inputs = inputs.copyOf()

  // Compiled now, as constructing a RuntimeShader here used to, so an invalid shader still fails when
  // the effect is created rather than on its first update.
  private val shader = effect.sharedShader()

  override fun updateUniforms(
    uniforms: RuntimeShaderUniformProvider.() -> Unit,
  ): PlatformRenderEffect {
    uniforms(this.uniforms)
    return shader.createEffect(this.uniforms, shaderNames, inputs)
  }

  override fun updateInputs(
    inputs: Array<PlatformRenderEffect?>,
    uniforms: RuntimeShaderUniformProvider.() -> Unit,
  ): PlatformRenderEffect {
    this.inputs = inputs.copyOf()
    return updateUniforms(uniforms)
  }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createAndroidRuntimeShaderRenderEffect(
  shader: RuntimeShader,
  shaderNames: Array<String>,
  inputs: Array<PlatformRenderEffect?>,
): PlatformRenderEffect {
  require(shaderNames.isNotEmpty()) {
    "shaderNames must contain at least one shader uniform name"
  }
  require(shaderNames.size >= inputs.size) {
    "shaderNames (${shaderNames.size}) must be >= inputs (${inputs.size})"
  }

  // Android's createRuntimeShaderEffect only supports a single content input.
  // Find the content shader name — the one with a null input (receives rendered content).
  val contentIndex = inputs.indexOfFirst { it == null }
  val contentShaderName = when {
    contentIndex >= 0 -> shaderNames.getOrNull(contentIndex) ?: shaderNames.first()
    else -> shaderNames.first()
  }

  return wrapRuntimeShaderConstruction {
    // Chain any non-null input RenderEffects.
    val chainedInput = inputs.filterNotNull().reduceOrNull { acc, input ->
      acc.then(input)
    }
    RenderEffect.createRuntimeShaderEffect(shader, contentShaderName).let { content ->
      chainedInput?.then(content) ?: content
    }
  }
}

/**
 * A [RuntimeShader] shared by every effect created from the same SkSL.
 *
 * Constructing a [RuntimeShader] compiles its SkSL, which is expensive and happens on the main
 * thread as effects first draw. A [RenderEffect] copies the shader's uniforms and children when it
 * is created, so effects can share one compiled shader if each gives it the state a new shader would
 * have before creating a [RenderEffect]: its own uniforms and children, zero for uniforms only other
 * effects set, and no children otherwise. A child cannot be unset, so between effects every child is
 * [EmptyChildShader], which samples as transparent black just as an unset child does, and which
 * keeps the shared shader from holding on to a child after the effect that set it is gone.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class SharedRuntimeShader(sksl: String) {
  private val shader = RuntimeShader(sksl)
  private val floatUniformSizes = HashMap<String, Int>()
  private val colorUniforms = HashSet<String>()
  private val childNames = HashSet<String>()

  @Synchronized
  fun createEffect(
    uniforms: RecordedUniforms,
    shaderNames: Array<String>,
    inputs: Array<PlatformRenderEffect?>,
  ): PlatformRenderEffect {
    for ((name, size) in floatUniformSizes) {
      if (name !in uniforms.floats) shader.setFloatUniform(name, FloatArray(size))
    }
    for (name in colorUniforms) {
      if (name !in uniforms.colors) shader.setColorUniform(name, 0)
    }
    for ((name, values) in uniforms.floats) {
      shader.setFloatUniform(name, values)
      floatUniformSizes[name] = values.size
    }
    for ((name, color) in uniforms.colors) {
      shader.setColorUniform(name, color)
      colorUniforms += name
    }
    for ((name, child) in uniforms.children) {
      shader.setInputShader(name, child)
      childNames += name
    }
    try {
      return createAndroidRuntimeShaderRenderEffect(shader, shaderNames, inputs)
    } finally {
      for (name in childNames) shader.setInputShader(name, EmptyChildShader)
    }
  }
}

/** A child shader that samples as transparent black everywhere, as an unset child does. */
private val EmptyChildShader: android.graphics.Shader by lazy {
  android.graphics.BitmapShader(
    android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888),
    android.graphics.Shader.TileMode.CLAMP,
    android.graphics.Shader.TileMode.CLAMP,
  )
}

/** Uniforms set by one effect, applied to its [SharedRuntimeShader] each time it creates an effect. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class RecordedUniforms : RuntimeShaderUniformProvider {
  val floats = HashMap<String, FloatArray>()
  val colors = HashMap<String, android.graphics.Color>()
  val children = HashMap<String, Shader>()

  override fun setColorUniform(name: String, color: Color) {
    colors[name] = android.graphics.Color.valueOf(
      color.red,
      color.green,
      color.blue,
      color.alpha,
      color.colorSpace.toAndroidColorSpace(),
    )
  }

  override fun setFloatUniform(name: String, value: Float) {
    floats[name] = floatArrayOf(value)
  }

  override fun setFloatUniform(name: String, value1: Float, value2: Float) {
    floats[name] = floatArrayOf(value1, value2)
  }

  override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
    floats[name] = floatArrayOf(value1, value2, value3, value4)
  }

  override fun setIntUniform(name: String, value: Int) {
    floats[name] = floatArrayOf(value.toFloat())
  }

  override fun setChildShader(name: String, shader: Shader) {
    children[name] = shader
  }
}
