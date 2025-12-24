// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFold
import dev.chrisbanes.haze.HazeBlendMode
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.asComposeRenderEffect
import dev.chrisbanes.haze.blendForeground
import dev.chrisbanes.haze.createBlendColorFilter
import dev.chrisbanes.haze.createBlendImageFilter
import dev.chrisbanes.haze.createColorFilterImageFilter
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import dev.chrisbanes.haze.createShaderImageFilter
import dev.chrisbanes.haze.isRuntimeShaderRenderEffectSupported
import dev.chrisbanes.haze.then
import dev.chrisbanes.haze.toHazeBlendMode

private val VERTICAL_BLUR_SHADER by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(HazeBlurShaders.VERTICAL_BLUR_SKSL)
}

private val HORIZONTAL_BLUR_SHADER by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(HazeBlurShaders.HORIZONTAL_BLUR_SKSL)
}

@RequiresApi(31)
internal fun createRenderEffect(
  context: PlatformContext,
  density: Density,
  params: RenderEffectParams,
): RenderEffect {
  val blurRadius = params.blurRadius * params.scale
  require(blurRadius >= 0.dp) { "blurRadius needs to be equal or greater than 0.dp" }
  val size = ceil(params.contentSize * params.scale)
  val offset = (params.contentOffset * params.scale).round()

  val blurRadiusPx = with(density) { blurRadius.toPx() }
  val progressiveShader = params.progressive?.asBrush()?.toShader(size)

  val blur = if (progressiveShader != null && isRuntimeShaderRenderEffectSupported()) {
    // If we've been provided with a progressive/gradient blur shader, we need to use
    // our custom blur via a runtime shader (requires runtime shader support)
    createGradientBlurRenderEffect(blurRadiusPx, size, offset, progressiveShader)
  } else {
    // Platform-specific blur creation
    createBlurRenderEffect(blurRadiusPx, params)
  }

  val noise = createNoiseEffect(context, params.noiseFactor, progressiveShader, params.scale)

  return blur
    .blendForeground(foreground = noise, blendMode = HazeBlendMode.Softlight)
    .withTints(params.tints, size, offset, params.tintAlphaModulate, progressiveShader)
    .withMask(params.mask, size, offset)
    .asComposeRenderEffect()
}

/**
 * Creates the platform-specific noise effect.
 * - On Android: Uses a bitmap texture shader
 * - On Skiko: Uses a fractal noise shader
 */
internal expect fun createNoiseEffect(
  context: PlatformContext,
  noiseFactor: Float,
  mask: Shader?,
  scale: Float,
): PlatformRenderEffect

/**
 * Creates the platform-specific blur effect when no progressive mask is used.
 */
internal expect fun createBlurRenderEffect(
  blurRadiusPx: Float,
  params: RenderEffectParams,
): PlatformRenderEffect

private fun PlatformRenderEffect.withTints(
  tints: List<HazeTint>,
  size: Size,
  offset: Offset,
  alphaModulate: Float = 1f,
  mask: Shader? = null,
): PlatformRenderEffect = tints.fastFold(this) { acc, tint ->
  acc.withTint(tint, size, offset, alphaModulate, mask)
}

private fun PlatformRenderEffect.withTint(
  tint: HazeTint,
  size: Size,
  offset: Offset,
  alphaModulate: Float = 1f,
  mask: Shader? = null,
): PlatformRenderEffect {
  if (!tint.isSpecified) return this

  return when (tint) {
    is HazeTint.Brush -> withBrushTint(tint, size, offset, alphaModulate, mask)
    is HazeTint.Color -> withColorTint(tint, offset, alphaModulate, mask)
    else -> this
  }
}

/**
 * Applies a brush-based tint with optional mask and colorFilter.
 * 
 * Order: brush → alphaModulate → mask → colorFilter → blend
 */
private fun PlatformRenderEffect.withBrushTint(
  tint: HazeTint.Brush,
  size: Size,
  offset: Offset,
  alphaModulate: Float,
  mask: Shader?,
): PlatformRenderEffect {
  val tintBrush = tint.brush.toShader(size) ?: return this

  val brushEffect = if (alphaModulate >= 1f) {
    createShaderImageFilter(tintBrush)
  } else {
    // If we need to modulate the alpha, wrap it in a ColorFilter
    createColorFilterImageFilter(
      colorFilter = createBlendColorFilter(
        color = Color.Black.copy(alpha = alphaModulate).toArgb(),
        blendMode = HazeBlendMode.SrcIn,
      ),
      input = createShaderImageFilter(tintBrush),
    )
  }

  return applyMaskAndColorFilter(
    baseEffect = brushEffect,
    tintColorFilter = tint.colorFilter,
    blendMode = tint.blendMode,
    mask = mask,
    offset = offset,
  )
}

/**
 * Applies a color-based tint with optional mask and colorFilter.
 * 
 * Order: color → alphaModulate → mask → colorFilter → blend
 */
private fun PlatformRenderEffect.withColorTint(
  tint: HazeTint.Color,
  offset: Offset,
  alphaModulate: Float,
  mask: Shader?,
): PlatformRenderEffect {
  val tintColor = when {
    alphaModulate < 1f -> tint.color.copy(alpha = tint.color.alpha * alphaModulate)
    else -> tint.color
  }
  
  if (tintColor.alpha < 0.005f) return this

  val colorEffect = createBlendColorFilter(tintColor.toArgb(), tint.blendMode.toHazeBlendMode())
  
  val effectWithMask = if (mask != null) {
    createColorFilterImageFilter(
      colorFilter = createBlendColorFilter(tintColor.toArgb(), HazeBlendMode.SrcIn),
      input = createShaderImageFilter(mask),
    )
  } else {
    createColorFilterImageFilter(
      colorFilter = colorEffect,
      input = this,
    )
  }

  return applyColorFilterAndBlend(
    baseEffect = effectWithMask,
    tintColorFilter = tint.colorFilter,
    blendMode = tint.blendMode,
    mask = mask,
    offset = offset,
  )
}

/**
 * Applies mask and colorFilter to a brush effect, then blends with background.
 */
private fun PlatformRenderEffect.applyMaskAndColorFilter(
  baseEffect: PlatformRenderEffect,
  tintColorFilter: ColorFilter?,
  blendMode: BlendMode,
  mask: Shader?,
  offset: Offset,
): PlatformRenderEffect {
  val effectWithMask = if (mask != null) {
    createBlendImageFilter(
      blendMode = HazeBlendMode.SrcIn,
      background = createShaderImageFilter(mask),
      foreground = baseEffect,
    )
  } else {
    baseEffect
  }

  val effectWithColorFilter = if (tintColorFilter != null) {
    createColorFilterImageFilter(
      colorFilter = tintColorFilter.toPlatformColorFilter(),
      input = effectWithMask,
    )
  } else {
    effectWithMask
  }

  return blendForeground(
    foreground = effectWithColorFilter,
    blendMode = blendMode.toHazeBlendMode(),
    offset = offset,
  )
}

/**
 * Applies colorFilter and blends with background (used for color tints).
 */
private fun applyColorFilterAndBlend(
  baseEffect: PlatformRenderEffect,
  tintColorFilter: ColorFilter?,
  blendMode: BlendMode,
  mask: Shader?,
  offset: Offset,
): PlatformRenderEffect {
  val effectWithColorFilter = if (tintColorFilter != null) {
    createColorFilterImageFilter(
      colorFilter = tintColorFilter.toPlatformColorFilter(),
      input = baseEffect,
    )
  } else {
    baseEffect
  }

  return if (mask != null) {
    blendForeground(
      foreground = effectWithColorFilter,
      blendMode = blendMode.toHazeBlendMode(),
      offset = offset,
    )
  } else {
    effectWithColorFilter
  }
}

private fun PlatformRenderEffect.withMask(
  brush: Brush?,
  size: Size,
  offset: Offset,
  blendMode: HazeBlendMode = HazeBlendMode.DstIn,
): PlatformRenderEffect {
  val shader = brush?.toShader(size) ?: return this
  return blendForeground(
    foreground = createShaderImageFilter(shader),
    blendMode = blendMode,
    offset = offset,
  )
}

private fun createGradientBlurRenderEffect(
  blurRadiusPx: Float,
  size: Size,
  offset: Offset,
  mask: Shader,
): PlatformRenderEffect {
  fun shader(vertical: Boolean): PlatformRenderEffect = createRuntimeShaderRenderEffect(
    effect = if (vertical) VERTICAL_BLUR_SHADER else HORIZONTAL_BLUR_SHADER,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  ) {
    setFloatUniform("blurRadius", blurRadiusPx)
    setFloatUniform("crop", offset.x, offset.y, offset.x + size.width, offset.y + size.height)
    setChildShader("mask", mask)
  }

  // Our blur runtime shader is separated, therefore requires two passes, one in each direction
  return shader(vertical = false).then(shader(vertical = true))
}

private fun Brush.toShader(size: Size): Shader? = when (this) {
  is ShaderBrush -> createShader(size)
  else -> null
}
