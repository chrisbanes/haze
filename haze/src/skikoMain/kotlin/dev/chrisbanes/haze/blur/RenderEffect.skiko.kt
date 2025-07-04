// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.asBrush
import dev.chrisbanes.haze.ceil
import dev.chrisbanes.haze.round
import dev.chrisbanes.haze.toSkiaBlendMode
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

internal actual fun createBlurRenderEffect(
  context: PlatformContext,
  density: Density,
  params: RenderEffectParams,
): RenderEffect? {
  val blurRadius = params.blurRadius * params.scale
  require(blurRadius >= 0.dp) { "blurRadius needs to be equal or greater than 0.dp" }
  val size = ceil(params.contentSize * params.scale)
  val offset = (params.contentOffset * params.scale).round()

  val compositeShaderBuilder = RuntimeShaderBuilder(RUNTIME_SHADER).apply {
    uniform("noiseFactor", params.noiseFactor.coerceIn(0f, 1f))
  }

  val blurRadiusPx = with(density) { params.blurRadius.toPx() }

  val progressiveShader = params.progressive?.asBrush()?.toShader(size)
  val blur = if (progressiveShader != null) {
    // If we've been provided with a progressive/gradient blur shader, we need to use
    // our custom blur via a runtime shader
    createBlurImageFilterWithMask(
      blurRadiusPx = blurRadiusPx,
      size = size,
      offset = offset,
      mask = progressiveShader,
    )
  } else {
    createBlurImageFilter(blurRadiusPx, params.blurTileMode.toSkiaTileMode())
  }

  val noise = when {
    progressiveShader != null -> {
      ImageFilter.makeBlend(
        blendMode = BlendMode.SRC_IN,
        bg = ImageFilter.makeShader(progressiveShader, crop = null),
        fg = ImageFilter.makeShader(NOISE_SHADER, crop = null),
        crop = null,
      )
    }
    else -> {
      ImageFilter.makeShader(NOISE_SHADER, crop = null)
    }
  }

  return ImageFilter
    .makeRuntimeShader(
      runtimeShaderBuilder = compositeShaderBuilder,
      shaderNames = arrayOf("content", "blur", "noise"),
      inputs = arrayOf(null, blur, noise),
    )
    .withTints(params.tints, size, offset, params.tintAlphaModulate, progressiveShader)
    .withMask(params.mask, size, offset)
    .asComposeRenderEffect()
}

private fun ImageFilter.chainWith(imageFilter: ImageFilter): ImageFilter {
  return ImageFilter.makeCompose(imageFilter, this)
}

private fun ImageFilter.withTints(
  tints: List<HazeTint>,
  size: Size,
  offset: Offset,
  alphaModulate: Float = 1f,
  mask: Shader? = null,
): ImageFilter = tints.fold(this) { acc, tint ->
  acc.withTint(tint, size, offset, alphaModulate, mask)
}

private fun ImageFilter.withTint(
  tint: HazeTint,
  size: Size,
  offset: Offset,
  alphaModulate: Float = 1f,
  mask: Shader?,
): ImageFilter {
  if (!tint.isSpecified) return this

  val tintBrush = tint.brush?.toShader(size)
  if (tintBrush != null) {
    val brushEffect = when {
      alphaModulate >= 1f -> {
        ImageFilter.makeShader(tintBrush, crop = null)
      }

      else -> {
        // If we need to modulate the alpha, we'll need to wrap it in a ColorFilter
        ImageFilter.makeColorFilter(
          ColorFilter.makeBlend(Color.Black.copy(alpha = alphaModulate).toArgb(), BlendMode.SRC_IN),
          ImageFilter.makeShader(tintBrush, crop = null),
          crop = null,
        )
      }
    }

    return if (mask != null) {
      blendWith(
        ImageFilter.makeBlend(
          blendMode = BlendMode.SRC_IN,
          bg = ImageFilter.makeShader(mask, crop = null),
          fg = brushEffect,
          crop = null,
        ),
        blendMode = tint.blendMode.toSkiaBlendMode(),
        offset = offset,
      )
    } else {
      blendWith(
        foreground = brushEffect,
        blendMode = tint.blendMode.toSkiaBlendMode(),
        offset = offset,
      )
    }
  }

  val tintColor = when {
    alphaModulate < 1f -> tint.color.copy(alpha = tint.color.alpha * alphaModulate)
    else -> tint.color
  }
  if (tintColor.alpha >= 0.005f) {
    return if (mask != null) {
      blendWith(
        foreground = ImageFilter.makeColorFilter(
          f = ColorFilter.makeBlend(tintColor.toArgb(), BlendMode.SRC_IN),
          input = ImageFilter.makeShader(mask, crop = null),
          crop = null,
        ),
        blendMode = tint.blendMode.toSkiaBlendMode(),
        offset = offset,
      )
    } else {
      ImageFilter.makeColorFilter(
        ColorFilter.makeBlend(tintColor.toArgb(), tint.blendMode.toSkiaBlendMode()),
        this,
        crop = null,
      )
    }
  }

  return this
}

private fun ImageFilter.withMask(
  brush: Brush?,
  size: Size,
  offset: Offset,
  blendMode: BlendMode = BlendMode.DST_IN,
): ImageFilter {
  val shader = brush?.toShader(size) ?: return this
  return blendWith(
    foreground = ImageFilter.makeShader(shader, crop = null),
    blendMode = blendMode,
    offset = offset,
  )
}

private fun ImageFilter.blendWith(
  foreground: ImageFilter,
  blendMode: BlendMode,
  offset: Offset,
): ImageFilter = ImageFilter.makeBlend(
  blendMode = blendMode,
  fg = when {
    offset.isUnspecified -> foreground
    offset == Offset.Zero -> foreground
    // We need to offset the shader to the bounds
    else -> ImageFilter.makeOffset(offset.x, offset.y, foreground, crop = null)
  },
  bg = this,
  crop = null,
)

private fun createBlurImageFilter(
  blurRadiusPx: Float,
  tileMode: FilterTileMode,
  bounds: Rect? = null,
): ImageFilter {
  val sigma = BlurEffect.convertRadiusToSigma(blurRadiusPx)
  return ImageFilter.makeBlur(
    sigmaX = sigma,
    sigmaY = sigma,
    mode = tileMode,
    crop = bounds?.toIRect(),
  )
}

private fun createBlurImageFilterWithMask(
  blurRadiusPx: Float,
  size: Size,
  offset: Offset,
  mask: Shader,
): ImageFilter {
  fun shader(vertical: Boolean): ImageFilter {
    return ImageFilter.makeRuntimeShader(
      RuntimeShaderBuilder(if (vertical) VERTICAL_BLUR_SHADER else HORIZONTAL_BLUR_SHADER).apply {
        uniform("blurRadius", blurRadiusPx)
        uniform("crop", offset.x, offset.y, offset.x + size.width, offset.y + size.height)
        child("mask", mask)
      },
      shaderNames = arrayOf("content"),
      inputs = arrayOf(null),
    )
  }

  // Our blur runtime shader is separated, therefore requires two passes, one in each direction
  return shader(vertical = false).chainWith(shader(vertical = true))
}

private fun Brush.toShader(size: Size): Shader? = (this as? ShaderBrush)?.createShader(size)

private fun Rect.toIRect(): IRect =
  IRect.makeLTRB(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

private fun TileMode.toSkiaTileMode(): FilterTileMode = when (this) {
  TileMode.Decal -> FilterTileMode.DECAL
  TileMode.Mirror -> FilterTileMode.MIRROR
  TileMode.Repeated -> FilterTileMode.REPEAT
  else -> FilterTileMode.CLAMP
}
