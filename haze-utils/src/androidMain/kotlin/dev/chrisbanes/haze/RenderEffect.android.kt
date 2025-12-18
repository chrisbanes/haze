// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import android.graphics.ColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader as AndroidShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect

@InternalHazeApi
public actual typealias PlatformRenderEffect = RenderEffect

@InternalHazeApi
public actual typealias PlatformColorFilter = ColorFilter

@RequiresApi(31)
@InternalHazeApi
public actual fun createShaderRenderEffect(
  shader: Shader,
  crop: Rect?,
): PlatformRenderEffect {
  // Android RenderEffect.createShaderEffect doesn't support crop
  return RenderEffect.createShaderEffect(shader)
}

@RequiresApi(31)
@InternalHazeApi
public actual fun createBlendRenderEffect(
  blendMode: HazeBlendMode,
  background: PlatformRenderEffect,
  foreground: PlatformRenderEffect,
  crop: Rect?,
): PlatformRenderEffect = RenderEffect.createBlendModeEffect(background, foreground, blendMode.toAndroidBlendMode())

@RequiresApi(31)
@InternalHazeApi
public actual fun createColorFilterRenderEffect(
  colorFilter: PlatformColorFilter,
  input: PlatformRenderEffect?,
  crop: Rect?,
): PlatformRenderEffect = when {
  input != null -> RenderEffect.createColorFilterEffect(colorFilter, input)
  else -> RenderEffect.createColorFilterEffect(colorFilter)
}

@RequiresApi(31)
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

  val edgeTreatment = when (tileMode) {
    TileMode.Clamp -> AndroidShader.TileMode.CLAMP
    TileMode.Repeated -> AndroidShader.TileMode.REPEAT
    TileMode.Mirror -> AndroidShader.TileMode.MIRROR
    TileMode.Decal -> AndroidShader.TileMode.DECAL
    else -> AndroidShader.TileMode.CLAMP
  }

  return try {
    // On Android we use the native blur effect directly for better performance
    val blurEffect = RenderEffect.createBlurEffect(radiusX, radiusY, edgeTreatment)
    if (input != null) {
      RenderEffect.createChainEffect(blurEffect, input)
    } else {
      blurEffect
    }
  } catch (e: IllegalArgumentException) {
    throw IllegalArgumentException(
      "Error whilst creating blur effect. " +
        "This is likely because this device does not support a blur radius of" +
        " x=${radiusX}px, y=${radiusY}px",
      e,
    )
  }
}

@RequiresApi(31)
@InternalHazeApi
public actual fun createOffsetRenderEffect(
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
