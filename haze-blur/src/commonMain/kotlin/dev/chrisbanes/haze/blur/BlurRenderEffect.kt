// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
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
import dev.chrisbanes.haze.createBlendRenderEffect
import dev.chrisbanes.haze.createBlurRenderEffect
import dev.chrisbanes.haze.createColorFilterRenderEffect
import dev.chrisbanes.haze.createOffsetRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import dev.chrisbanes.haze.createShaderRenderEffect
import dev.chrisbanes.haze.isRuntimeShaderRenderEffectSupported
import dev.chrisbanes.haze.then
import dev.chrisbanes.haze.toHazeBlendMode
import dev.chrisbanes.haze.toPlatformColorFilter

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
    createBlurRenderEffect(
      radiusX = blurRadiusPx,
      radiusY = blurRadiusPx,
      tileMode = params.blurTileMode,
    ) ?: createOffsetRenderEffect(0f, 0f)
  }

  val noise = createNoiseEffect(context, params.noiseFactor, progressiveShader, params.scale)

  return blur
    .blendForeground(foreground = noise, blendMode = HazeBlendMode.Softlight)
    .withTints(params.colorEffects, size, offset, params.colorEffectsAlphaModulate, progressiveShader)
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

private fun PlatformRenderEffect.withTints(
  effects: List<HazeColorEffect>,
  size: Size,
  offset: Offset,
  alphaModulate: Float = 1f,
  mask: Shader? = null,
): PlatformRenderEffect = effects.fastFold(this) { acc, effect ->
  acc.withColorEffect(effect, size, offset, alphaModulate, mask)
}

private fun PlatformRenderEffect.withColorEffect(
  effect: HazeColorEffect,
  size: Size,
  offset: Offset,
  alphaModulate: Float = 1f,
  mask: Shader? = null,
): PlatformRenderEffect {
  if (!effect.isSpecified) return this

  return when (effect) {
    is HazeColorEffect.TintBrush -> withBrushTint(effect, size, offset, alphaModulate, mask)
    is HazeColorEffect.TintColor -> withColorTint(effect, offset, alphaModulate, mask)
    is HazeColorEffect.ColorFilter -> withColorFilter(effect, size, offset, mask)
    else -> this
  }
}

/**
 * Applies a brush-based tint with optional mask.
 *
 * Order: brush → alphaModulate → mask → blend
 */
private fun PlatformRenderEffect.withBrushTint(
  effect: HazeColorEffect.TintBrush,
  size: Size,
  offset: Offset,
  alphaModulate: Float,
  mask: Shader?,
): PlatformRenderEffect {
  val tintBrush = effect.brush.toShader(size) ?: return this

  val brushEffect = if (alphaModulate >= 1f) {
    createShaderRenderEffect(tintBrush)
  } else {
    // If we need to modulate the alpha, wrap it in a ColorFilter
    createColorFilterRenderEffect(
      colorFilter = createBlendColorFilter(
        color = Color.Black.copy(alpha = alphaModulate).toArgb(),
        blendMode = HazeBlendMode.SrcIn,
      ),
      input = createShaderRenderEffect(tintBrush),
    )
  }

  return applyMaskAndBlend(
    baseEffect = brushEffect,
    blendMode = effect.blendMode,
    mask = mask,
    offset = offset,
  )
}

/**
 * Applies a color-based tint with optional mask.
 *
 * Order: color → alphaModulate → mask → blend
 */
private fun PlatformRenderEffect.withColorTint(
  effect: HazeColorEffect.TintColor,
  offset: Offset,
  alphaModulate: Float,
  mask: Shader?,
): PlatformRenderEffect {
  val tintColor = when {
    alphaModulate < 1f -> effect.color.copy(alpha = effect.color.alpha * alphaModulate)
    else -> effect.color
  }

  if (tintColor.alpha < 0.005f) return this

  val colorEffect = createBlendColorFilter(tintColor.toArgb(), effect.blendMode.toHazeBlendMode())

  val effectWithMask = if (mask != null) {
    createColorFilterRenderEffect(
      colorFilter = createBlendColorFilter(tintColor.toArgb(), HazeBlendMode.SrcIn),
      input = createShaderRenderEffect(mask),
    )
  } else {
    createColorFilterRenderEffect(
      colorFilter = colorEffect,
      input = this,
    )
  }

  return if (mask != null) {
    blendForeground(
      foreground = effectWithMask,
      blendMode = effect.blendMode.toHazeBlendMode(),
      offset = offset,
    )
  } else {
    effectWithMask
  }
}

/**
 * Applies a color filter effect with optional mask.
 *
 * Order: colorFilter → mask → blend
 */
private fun PlatformRenderEffect.withColorFilter(
  effect: HazeColorEffect.ColorFilter,
  size: Size,
  offset: Offset,
  mask: Shader?,
): PlatformRenderEffect {
  val filterEffect = createColorFilterRenderEffect(
    colorFilter = effect.colorFilter.toPlatformColorFilter(),
    input = this,
  )

  return applyMaskAndBlend(
    baseEffect = filterEffect,
    blendMode = effect.blendMode,
    mask = mask,
    offset = offset,
  )
}

/**
 * Applies mask and blends with background.
 */
private fun PlatformRenderEffect.applyMaskAndBlend(
  baseEffect: PlatformRenderEffect,
  blendMode: BlendMode,
  mask: Shader?,
  offset: Offset,
): PlatformRenderEffect {
  val effectWithMask = if (mask != null) {
    createBlendRenderEffect(
      blendMode = HazeBlendMode.SrcIn,
      background = createShaderRenderEffect(mask),
      foreground = baseEffect,
    )
  } else {
    baseEffect
  }

  return blendForeground(
    foreground = effectWithMask,
    blendMode = blendMode.toHazeBlendMode(),
    offset = offset,
  )
}

private fun PlatformRenderEffect.withMask(
  brush: Brush?,
  size: Size,
  offset: Offset,
  blendMode: HazeBlendMode = HazeBlendMode.DstIn,
): PlatformRenderEffect {
  val shader = brush?.toShader(size) ?: return this
  return blendForeground(
    foreground = createShaderRenderEffect(shader),
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
