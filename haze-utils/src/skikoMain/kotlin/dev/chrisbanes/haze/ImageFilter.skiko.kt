// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageFilter

@InternalHazeApi
public actual typealias PlatformRenderEffect = ImageFilter

@InternalHazeApi
public actual typealias PlatformColorFilter = ColorFilter

@InternalHazeApi
public actual fun createShaderImageFilter(shader: Shader, crop: Rect?): PlatformRenderEffect =
  ImageFilter.makeShader(shader, crop = crop?.toIRect())

@InternalHazeApi
public actual fun createBlendImageFilter(
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
public actual fun createColorFilterImageFilter(
  colorFilter: PlatformColorFilter,
  input: PlatformRenderEffect?,
  crop: Rect?,
): PlatformRenderEffect = ImageFilter.makeColorFilter(
  f = colorFilter,
  input = input,
  crop = crop?.toIRect(),
)

@InternalHazeApi
public actual fun createBlurImageFilter(
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
public actual fun createOffsetImageFilter(
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
public actual inline fun PlatformRenderEffect.then(other: PlatformRenderEffect): PlatformRenderEffect {
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
