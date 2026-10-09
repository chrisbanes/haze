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
import androidx.compose.ui.graphics.LinearGradient
import androidx.compose.ui.graphics.RadialGradient
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.SweepGradient
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFold
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.asBrush
import dev.chrisbanes.haze.blendForeground
import dev.chrisbanes.haze.createBlendColorFilter
import dev.chrisbanes.haze.createBlendRenderEffect
import dev.chrisbanes.haze.createBlurRenderEffect
import dev.chrisbanes.haze.createColorFilterRenderEffect
import dev.chrisbanes.haze.createOffsetRenderEffect
import dev.chrisbanes.haze.createProgressiveBlurRenderEffect
import dev.chrisbanes.haze.createShaderRenderEffect
import dev.chrisbanes.haze.isRuntimeShaderRenderEffectSupported
import dev.chrisbanes.haze.then
import dev.chrisbanes.haze.toPlatformColorFilter

@RequiresApi(31)
internal fun createRenderEffect(
  context: PlatformContext,
  density: Density,
  params: RenderEffectParams,
): PlatformRenderEffect {
  val blurRadius = params.blurRadius * params.scale
  require(blurRadius >= 0.dp) { "blurRadius needs to be equal or greater than 0.dp" }
  val size = ceil(params.contentSize * params.scale)
  val offset = (params.contentOffset * params.scale).round()

  val blurRadiusPx = params.resolveBlurRadiusPx(density)
  val progressiveShader = params.progressive?.asBrush()?.toShader(params.contentSize, params.scale)

  val input: PlatformRenderEffect? = if (
    params.backgroundColor.isSpecified && params.backgroundColor.alpha > 0f
  ) {
    Brush.linearGradient(listOf(params.backgroundColor, params.backgroundColor))
      .toShader(size)
      ?.let(::createShaderRenderEffect)
      ?.let { background ->
        createBlendRenderEffect(
          blendMode = BlendMode.SrcOver,
          background = background,
          foreground = createOffsetRenderEffect(0f, 0f),
        )
      }
  } else {
    null
  }

  val blur = if (progressiveShader != null && isRuntimeShaderRenderEffectSupported()) {
    // If we've been provided with a progressive/gradient blur shader, we need to use
    // our custom blur via a runtime shader (requires runtime shader support)
    val progressive = createProgressiveBlurRenderEffect(
      blurRadiusPx = blurRadiusPx,
      size = size,
      offset = offset,
      mask = progressiveShader,
    )
    input?.then(progressive) ?: progressive
  } else {
    // Platform-specific blur creation
    createBlurRenderEffect(
      radiusX = blurRadiusPx,
      radiusY = blurRadiusPx,
      tileMode = params.blurTileMode,
      input = input,
    ) ?: input ?: createOffsetRenderEffect(0f, 0f)
  }

  val combinedNoiseTintEffect = params.combinedNoiseTintColor()?.let { tintColor ->
    createCombinedNoiseTintRenderEffectOrNull(
      context = context,
      input = blur,
      noiseFactor = params.noiseFactor,
      tintColor = tintColor,
      scale = params.scale,
    )
  }

  val styled = combinedNoiseTintEffect ?: run {
    val blurWithNoise = if (params.noiseFactor.hasVisibleNoise()) {
      blur.blendForeground(
        foreground = createNoiseEffect(
          context = context,
          noiseFactor = params.noiseFactor,
          mask = progressiveShader,
          scale = params.scale,
        ),
        blendMode = BlendMode.Softlight,
        // Only the mask lives in content coordinates. Unmasked noise stays in layer coordinates;
        // offsetting it would shift it off the expanded layer.
        offset = if (progressiveShader != null) offset else Offset.Zero,
      )
    } else {
      blur
    }

    blurWithNoise.withTints(
      params.colorEffects,
      params.contentSize,
      params.scale,
      offset,
      params.colorEffectsAlphaModulate,
      progressiveShader,
    )
  }

  val progressiveMasked = styled
    .withMask(params.progressiveMask, params.contentSize, params.scale, offset)
    .let { masked ->
      if (params.retainInputWhenMasked && params.progressiveMask != null) {
        createOffsetRenderEffect(0f, 0f).blendForeground(masked, BlendMode.SrcOver)
      } else {
        masked
      }
    }

  // The user's mask fades the complete output, including any retained input.
  return progressiveMasked.withMask(params.mask, params.contentSize, params.scale, offset)
}

internal fun Float.hasVisibleNoise(): Boolean = this > 0f

private fun RenderEffectParams.combinedNoiseTintColor(): Color? {
  if (!noiseFactor.hasVisibleNoise() || progressive != null || mask != null || progressiveMask != null) {
    return null
  }
  val tint = colorEffects.singleOrNull() as? TintColorHazeColorEffect ?: return null
  if (tint.blendMode != BlendMode.SrcOver) return null

  return tint.resolveColor(colorEffectsAlphaModulate)
}

internal expect fun createCombinedNoiseTintRenderEffectOrNull(
  context: PlatformContext,
  input: PlatformRenderEffect,
  noiseFactor: Float,
  tintColor: Color,
  scale: Float,
): PlatformRenderEffect?

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
  scale: Float,
  offset: Offset,
  alphaModulate: Float = 1f,
  mask: Shader? = null,
): PlatformRenderEffect = effects.fastFold(this) { acc, effect ->
  acc.withColorEffect(effect, size, scale, offset, alphaModulate, mask)
}

private fun PlatformRenderEffect.withColorEffect(
  effect: HazeColorEffect,
  size: Size,
  scale: Float,
  offset: Offset,
  alphaModulate: Float = 1f,
  mask: Shader? = null,
): PlatformRenderEffect {
  return when (effect) {
    is TintBrushHazeColorEffect -> withBrushTint(effect, size, scale, offset, alphaModulate, mask)
    is TintColorHazeColorEffect -> withColorTint(effect, offset, alphaModulate, mask)
    is ColorFilterHazeColorEffect -> withColorFilter(effect, offset, mask)
  }
}

/**
 * Applies a brush-based tint with optional mask.
 *
 * Order: brush → alphaModulate → mask → blend
 */
private fun PlatformRenderEffect.withBrushTint(
  effect: TintBrushHazeColorEffect,
  size: Size,
  scale: Float,
  offset: Offset,
  alphaModulate: Float,
  mask: Shader?,
): PlatformRenderEffect {
  val brush = effect.brush
  if (brush is SolidColor) {
    return withColorTint(TintColorHazeColorEffect(brush.value, effect.blendMode), offset, alphaModulate, mask)
  }
  val tintBrush = brush.toShader(size, scale) ?: return this

  val brushEffect = if (alphaModulate >= 1f) {
    createShaderRenderEffect(tintBrush)
  } else {
    // If we need to modulate the alpha, wrap it in a ColorFilter
    createColorFilterRenderEffect(
      colorFilter = createBlendColorFilter(
        color = Color.Black.copy(alpha = alphaModulate).toArgb(),
        blendMode = BlendMode.DstIn,
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
  effect: TintColorHazeColorEffect,
  offset: Offset,
  alphaModulate: Float,
  mask: Shader?,
): PlatformRenderEffect {
  if (effect.blendMode == BlendMode.Dst) return this
  val tintColor = effect.resolveColor(alphaModulate) ?: return this

  val tintArgb = tintColor.toArgb()
  if (mask != null) {
    val maskedTint = createColorFilterRenderEffect(
      colorFilter = createBlendColorFilter(tintArgb, BlendMode.SrcIn),
      input = createShaderRenderEffect(mask),
    )
    return blendForeground(
      foreground = maskedTint,
      blendMode = effect.blendMode,
      offset = offset,
    )
  }
  if (effect.blendMode == BlendMode.DstIn && (tintArgb ushr 24) == 255) return this
  return createColorFilterRenderEffect(
    colorFilter = createBlendColorFilter(tintArgb, effect.blendMode),
    input = this,
  )
}

private fun TintColorHazeColorEffect.resolveColor(alphaModulate: Float): Color? {
  val resolved = when {
    alphaModulate < 1f -> color.copy(alpha = color.alpha * alphaModulate)
    else -> color
  }
  return resolved.takeIf { it.alpha >= 0.005f }
}

/**
 * Applies a color filter effect with optional mask.
 *
 * Order: colorFilter → mask → blend
 */
private fun PlatformRenderEffect.withColorFilter(
  effect: ColorFilterHazeColorEffect,
  offset: Offset,
  mask: Shader?,
): PlatformRenderEffect {
  val filterEffect = createColorFilterRenderEffect(
    colorFilter = effect.colorFilter.toPlatformColorFilter(),
    input = this,
  )

  // Filtered input already uses expanded-layer coordinates. Only the independently
  // authored mask needs to move from node-local coordinates into that layer.
  val maskedFilter = if (mask != null) {
    createBlendRenderEffect(
      blendMode = BlendMode.SrcIn,
      background = createShaderRenderEffect(mask).let {
        createOffsetRenderEffect(offset.x, offset.y, it)
      },
      foreground = filterEffect,
    )
  } else {
    filterEffect
  }

  return blendForeground(
    foreground = maskedFilter,
    blendMode = effect.blendMode,
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
      blendMode = BlendMode.SrcIn,
      background = createShaderRenderEffect(mask),
      foreground = baseEffect,
    )
  } else {
    baseEffect
  }

  return blendForeground(
    foreground = effectWithMask,
    blendMode = blendMode,
    offset = offset,
  )
}

private fun PlatformRenderEffect.withMask(
  brush: Brush?,
  size: Size,
  scale: Float,
  offset: Offset,
  blendMode: BlendMode = BlendMode.DstIn,
): PlatformRenderEffect {
  val shader = brush?.toShader(size, scale) ?: return this
  return blendForeground(
    foreground = createShaderRenderEffect(shader),
    blendMode = blendMode,
    offset = offset,
  )
}

private fun Brush.toShader(size: Size, scale: Float = 1f): Shader? = when {
  this is ShaderBrush -> createScaledShader(size, scale)
  this is SolidColor -> Brush.linearGradient(listOf(value, value)).toShader(size, scale)
  else -> null
}

internal fun Brush.isCustomShaderBrush(): Boolean = this is ShaderBrush &&
  this !is LinearGradient && this !is RadialGradient && this !is SweepGradient

/** Resolves brush geometry in content coordinates before scaling the generated shader. */
internal expect fun ShaderBrush.createScaledShader(size: Size, scale: Float): Shader

internal expect fun BlurVisualEffect.constrainInputScaleForBrushes(scale: Float): Float
