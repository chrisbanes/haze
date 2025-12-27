// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import android.graphics.ColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect

@InternalHazeApi
public actual typealias PlatformRenderEffect = RenderEffect

@InternalHazeApi
public actual typealias PlatformColorFilter = ColorFilter

@RequiresApi(31)
@InternalHazeApi
public actual fun createShaderImageFilter(shader: Shader, crop: Rect?): PlatformRenderEffect {
  // Android RenderEffect.createShaderEffect doesn't support crop
  return RenderEffect.createShaderEffect(shader)
}

@RequiresApi(31)
@InternalHazeApi
public actual fun createBlendImageFilter(
  blendMode: HazeBlendMode,
  background: PlatformRenderEffect,
  foreground: PlatformRenderEffect,
  crop: Rect?,
): PlatformRenderEffect = RenderEffect.createBlendModeEffect(background, foreground, blendMode.toAndroidBlendMode())

@RequiresApi(31)
@InternalHazeApi
public actual fun createColorFilterImageFilter(
  colorFilter: PlatformColorFilter,
  input: PlatformRenderEffect?,
  crop: Rect?,
): PlatformRenderEffect = when {
  input != null -> RenderEffect.createColorFilterEffect(colorFilter, input)
  else -> RenderEffect.createColorFilterEffect(colorFilter)
}

@RequiresApi(31)
@InternalHazeApi
public actual fun createBlurImageFilter(
  radiusX: Float,
  radiusY: Float,
  tileMode: TileMode,
  input: PlatformRenderEffect?,
  crop: Rect?,
): PlatformRenderEffect {
  val edgeTreatment = when (tileMode) {
    TileMode.Clamp -> Shader.TileMode.CLAMP
    TileMode.Repeated -> Shader.TileMode.REPEAT
    TileMode.Mirror -> Shader.TileMode.MIRROR
    TileMode.Decal -> Shader.TileMode.DECAL
    else -> Shader.TileMode.CLAMP
  }
  val blurEffect = RenderEffect.createBlurEffect(radiusX, radiusY, edgeTreatment)
  return if (input != null) {
    RenderEffect.createChainEffect(blurEffect, input)
  } else {
    blurEffect
  }
}

@RequiresApi(31)
@InternalHazeApi
public actual fun createOffsetImageFilter(
  offsetX: Float,
  offsetY: Float,
  input: PlatformRenderEffect?,
  crop: Rect?,
): PlatformRenderEffect = when {
  input != null -> RenderEffect.createOffsetEffect(offsetX, offsetY, input)
  else -> RenderEffect.createOffsetEffect(offsetX, offsetY)
}

@RequiresApi(31)
@InternalHazeApi
public actual inline fun PlatformRenderEffect.then(other: PlatformRenderEffect): PlatformRenderEffect {
  return RenderEffect.createChainEffect(other, this)
}

@RequiresApi(31)
@InternalHazeApi
public actual fun PlatformRenderEffect.asComposeRenderEffect(): androidx.compose.ui.graphics.RenderEffect {
  return this.asComposeRenderEffect()
}
