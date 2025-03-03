// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

internal actual fun DrawScope.canUseGraphicLayers(): Boolean = true

internal actual fun CompositionLocalConsumerModifierNode.createRenderEffect(params: RenderEffectParams): RenderEffect? {
  require(params.blurRadius >= 0.dp) { "blurRadius needs to be equal or greater than 0.dp" }

  val compositeShaderBuilder = RuntimeShaderBuilder(RUNTIME_SHADER).apply {
    uniform("noiseFactor", params.noiseFactor.coerceIn(0f, 1f))
    child("noise", NOISE_SHADER)
  }

  val blurRadiusPx = with(currentValueOf(LocalDensity)) { params.blurRadius.toPx() }

  val progressiveShader = params.progressive?.asBrush()?.toShader(params.contentBounds.size)
  val blur = if (progressiveShader != null) {
    // If we've been provided with a progressive/gradient blur shader, we need to use
    // our custom blur via a runtime shader
    createBlurImageFilterWithMask(
      blurRadiusPx = blurRadiusPx,
      bounds = params.contentBounds,
      mask = progressiveShader,
    )
  } else {
    createBlurImageFilter(blurRadiusPx = blurRadiusPx)
  }

  return ImageFilter
    .makeRuntimeShader(
      runtimeShaderBuilder = compositeShaderBuilder,
      shaderNames = arrayOf("content", "blur"),
      inputs = arrayOf(null, blur),
    )
    .withTints(params.tints, params.contentBounds, params.tintAlphaModulate, progressiveShader)
    .withMask(params.mask, params.contentBounds)
    .asComposeRenderEffect()
}

private fun ImageFilter.chainWith(imageFilter: ImageFilter): ImageFilter {
  return ImageFilter.makeCompose(imageFilter, this)
}

private fun ImageFilter.withTints(
  tints: List<HazeTint>,
  contentBounds: Rect,
  alphaModulate: Float = 1f,
  mask: Shader? = null,
): ImageFilter = tints.fold(this) { acc, tint ->
  acc.withTint(tint, contentBounds, alphaModulate, mask)
}

private fun ImageFilter.withTint(
  tint: HazeTint,
  contentBounds: Rect,
  alphaModulate: Float = 1f,
  mask: Shader?,
): ImageFilter {
  if (!tint.isSpecified) return this

  val tintBrush = tint.brush?.toShader(contentBounds.size)
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
        offset = contentBounds.topLeft,
      )
    } else {
      blendWith(
        foreground = brushEffect,
        blendMode = tint.blendMode.toSkiaBlendMode(),
        offset = contentBounds.topLeft,
      )
    }
  }

  val tintColor = when {
    alphaModulate < 1f -> tint.color.copy(alpha = tint.color.alpha * alphaModulate)
    else -> tint.color
  }
  if (tintColor.alpha >= 0.005f) {
    return if (mask != null) {
      return blendWith(
        foreground = ImageFilter.makeColorFilter(
          ColorFilter.makeBlend(tintColor.toArgb(), BlendMode.SRC_IN),
          ImageFilter.makeShader(mask, crop = null),
          crop = null,
        ),
        blendMode = tint.blendMode.toSkiaBlendMode(),
        offset = contentBounds.topLeft,
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
  contentBounds: Rect,
  blendMode: BlendMode = BlendMode.DST_IN,
): ImageFilter {
  val shader = brush?.toShader(contentBounds.size) ?: return this
  return blendWith(
    foreground = ImageFilter.makeShader(shader, crop = null),
    blendMode = blendMode,
    offset = contentBounds.topLeft,
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

private fun createBlurImageFilter(blurRadiusPx: Float, bounds: Rect? = null): ImageFilter {
  val sigma = BlurEffect.convertRadiusToSigma(blurRadiusPx)
  return ImageFilter.makeBlur(
    sigmaX = sigma,
    sigmaY = sigma,
    mode = FilterTileMode.CLAMP,
    crop = bounds?.toIRect(),
  )
}

private fun createBlurImageFilterWithMask(
  blurRadiusPx: Float,
  bounds: Rect,
  mask: Shader,
): ImageFilter {
  fun shader(vertical: Boolean): ImageFilter {
    return ImageFilter.makeRuntimeShader(
      RuntimeShaderBuilder(if (vertical) VERTICAL_BLUR_SHADER else HORIZONTAL_BLUR_SHADER).apply {
        uniform("blurRadius", blurRadiusPx)
        uniform("crop", bounds.left, bounds.top, bounds.right, bounds.bottom)
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
