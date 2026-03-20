// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.jvm.JvmInline
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

@InternalHazeApi
public actual typealias PlatformRuntimeEffect = RuntimeEffect

@InternalHazeApi
public actual typealias PlatformRenderEffect = ImageFilter

@InternalHazeApi
public actual typealias PlatformColorFilter = ColorFilter

@InternalHazeApi
public actual fun createRuntimeEffect(sksl: String): PlatformRuntimeEffect {
  return RuntimeEffect.makeForShader(sksl)
}

@InternalHazeApi
public actual fun createShaderRenderEffect(shader: Shader, crop: Rect?): PlatformRenderEffect =
  ImageFilter.makeShader(shader, crop = crop?.toIRect())

@InternalHazeApi
public actual fun createBlendRenderEffect(
  blendMode: HazeBlendMode,
  background: PlatformRenderEffect,
  foreground: PlatformRenderEffect,
  crop: Rect?,
): PlatformRenderEffect = ImageFilter.makeBlend(
  blendMode = blendMode.toSkiaBlendMode(),
  bg = background,
  fg = foreground,
  crop = crop?.toIRect(),
)

@InternalHazeApi
public actual fun createColorFilterRenderEffect(
  colorFilter: PlatformColorFilter,
  input: PlatformRenderEffect?,
  crop: Rect?,
): PlatformRenderEffect = ImageFilter.makeColorFilter(
  f = colorFilter,
  input = input,
  crop = crop?.toIRect(),
)

@InternalHazeApi
public actual fun createBlurRenderEffect(
  radiusX: Float,
  radiusY: Float,
  tileMode: TileMode,
  input: PlatformRenderEffect?,
  crop: Rect?,
): PlatformRenderEffect? {
  if (radiusX <= 0f && radiusY <= 0f) {
    return null
  }

  return ImageFilter.makeBlur(
    sigmaX = BlurEffect.convertRadiusToSigma(radiusX),
    sigmaY = BlurEffect.convertRadiusToSigma(radiusY),
    mode = when (tileMode) {
      TileMode.Clamp -> FilterTileMode.CLAMP
      TileMode.Repeated -> FilterTileMode.REPEAT
      TileMode.Mirror -> FilterTileMode.MIRROR
      TileMode.Decal -> FilterTileMode.DECAL
      else -> FilterTileMode.CLAMP
    },
    input = input,
    crop = crop?.toIRect(),
  )
}

@InternalHazeApi
public actual fun createOffsetRenderEffect(
  offsetX: Float,
  offsetY: Float,
  input: PlatformRenderEffect?,
  crop: Rect?,
): PlatformRenderEffect = ImageFilter.makeOffset(
  dx = offsetX,
  dy = offsetY,
  input = input,
  crop = crop?.toIRect(),
)

@InternalHazeApi
public actual fun PlatformRenderEffect.then(other: PlatformRenderEffect): PlatformRenderEffect {
  return ImageFilter.makeCompose(other, this)
}

@InternalHazeApi
public actual fun PlatformRenderEffect.asComposeRenderEffect(): RenderEffect {
  return asComposeRenderEffect()
}

private fun Rect.toIRect(): IRect = IRect.makeLTRB(
  l = left.toInt(),
  t = top.toInt(),
  r = right.toInt(),
  b = bottom.toInt(),
)

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
