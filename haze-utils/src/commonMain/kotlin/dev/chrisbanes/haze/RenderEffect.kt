// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.TileMode

/**
 * Platform-specific render effect type.
 * - On Android (API 31+): `android.graphics.RenderEffect`
 * - On Skiko: `org.jetbrains.skia.ImageFilter`
 */
@InternalHazeApi
public expect class PlatformRenderEffect

/**
 * Platform-specific color filter type.
 * - On Android: `android.graphics.ColorFilter`
 * - On Skiko: `org.jetbrains.skia.ColorFilter`
 */
@InternalHazeApi
public expect class PlatformColorFilter

/**
 * Creates a [PlatformRenderEffect] from a shader.
 */
@InternalHazeApi
public expect fun createShaderImageFilter(shader: Shader, crop: Rect? = null): PlatformRenderEffect

/**
 * Creates a blend [PlatformRenderEffect] from two render effects.
 */
@InternalHazeApi
public expect fun createBlendImageFilter(
  blendMode: HazeBlendMode,
  background: PlatformRenderEffect,
  foreground: PlatformRenderEffect,
  crop: Rect? = null,
): PlatformRenderEffect

/**
 * Creates a color filter [PlatformRenderEffect].
 */
@InternalHazeApi
public expect fun createColorFilterImageFilter(
  colorFilter: PlatformColorFilter,
  input: PlatformRenderEffect? = null,
  crop: Rect? = null,
): PlatformRenderEffect

/**
 * Creates a blur [PlatformRenderEffect].
 * @param radiusX Blur radius in the X direction (pixels)
 * @param radiusY Blur radius in the Y direction (pixels)
 */
@InternalHazeApi
public expect fun createBlurImageFilter(
  radiusX: Float,
  radiusY: Float,
  tileMode: TileMode,
  input: PlatformRenderEffect? = null,
  crop: Rect? = null,
): PlatformRenderEffect

/**
 * Creates an offset [PlatformRenderEffect].
 */
@InternalHazeApi
public expect fun createOffsetImageFilter(
  offsetX: Float,
  offsetY: Float,
  input: PlatformRenderEffect? = null,
  crop: Rect? = null,
): PlatformRenderEffect

/**
 * Chains this render effect with another, composing them.
 */
@InternalHazeApi
public expect fun PlatformRenderEffect.then(other: PlatformRenderEffect): PlatformRenderEffect

/**
 * Blends this render effect with a foreground effect.
 */
@InternalHazeApi
public fun PlatformRenderEffect.blendForeground(
  foreground: PlatformRenderEffect,
  blendMode: HazeBlendMode,
  offset: Offset = Offset.Unspecified,
): PlatformRenderEffect = createBlendImageFilter(
  blendMode = blendMode,
  background = this,
  foreground = when {
    offset.isUnspecified -> foreground
    offset == Offset.Zero -> foreground
    else -> createOffsetImageFilter(offset.x, offset.y, foreground)
  },
)

/**
 * Converts this platform render effect to a Compose [RenderEffect].
 */
@InternalHazeApi
public expect fun PlatformRenderEffect.asComposeRenderEffect(): RenderEffect
