// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

internal actual fun DrawScope.useGraphicLayers(): Boolean = true

internal actual fun HazeChildNode.createRenderEffect(
  blurRadiusPx: Float,
  noiseFactor: Float,
  tints: List<HazeTint>,
  tintAlphaModulate: Float,
  contentSize: Size,
  contentOffset: Offset,
  layerSize: Size,
  mask: Brush?,
  progressive: Brush?,
): RenderEffect? {
  log("HazeChildNode") {
    "createRenderEffect. " +
      "blurRadiusPx=$blurRadiusPx, " +
      "noiseFactor=$noiseFactor, " +
      "tints=$tints, " +
      "contentSize=$contentSize, " +
      "contentOffset=$contentOffset, " +
      "layerSize=$layerSize"
  }

  require(blurRadiusPx >= 0f) { "blurRadius needs to be equal or greater than 0f" }

  val compositeShaderBuilder = RuntimeShaderBuilder(RUNTIME_SHADER).apply {
    uniform("noiseFactor", noiseFactor.coerceIn(0f, 1f))
    child("noise", NOISE_SHADER)
  }

  val progressiveShader = progressive?.toShader(contentSize)
  val blur = if (progressiveShader != null) {
    // If we've been provided with a progressive/gradient blur shader, we need to use
    // our custom blur via a runtime shader
    createBlurImageFilterWithMask(
      blurRadiusPx = blurRadiusPx,
      bounds = Rect(contentOffset, contentSize),
      mask = progressiveShader,
    )
  } else {
    createBlurImageFilter(blurRadiusPx = blurRadiusPx, bounds = layerSize.toRect())
  }

  return ImageFilter
    .makeRuntimeShader(
      runtimeShaderBuilder = compositeShaderBuilder,
      shaderNames = arrayOf("content", "blur"),
      inputs = arrayOf(null, blur),
    )
    .withTints(tints, tintAlphaModulate, progressiveShader, contentOffset)
    .withMask(mask, contentSize, contentOffset)
    .asComposeRenderEffect()
}

private fun ImageFilter.chainWith(imageFilter: ImageFilter): ImageFilter {
  return ImageFilter.makeCompose(imageFilter, this)
}

private fun ImageFilter.withTints(
  tints: List<HazeTint>,
  alphaModulate: Float = 1f,
  mask: Shader? = null,
  maskOffset: Offset = Offset.Zero,
): ImageFilter = tints.fold(this) { acc, tint ->
  acc.withTint(tint, alphaModulate, mask, maskOffset)
}

private fun ImageFilter.withTint(
  tint: HazeTint,
  alphaModulate: Float = 1f,
  mask: Shader?,
  maskOffset: Offset,
): ImageFilter {
  if (!tint.color.isSpecified) return this
  val color = when {
    alphaModulate < 1f -> tint.color.copy(alpha = tint.color.alpha * alphaModulate)
    else -> tint.color
  }
  if (color.alpha < 0.005f) return this

  return if (mask != null) {
    blendWith(
      foreground = ImageFilter.makeColorFilter(
        f = ColorFilter.makeBlend(color.toArgb(), BlendMode.SRC_IN),
        input = ImageFilter.makeShader(shader = mask, crop = null),
        crop = null,
      ),
      blendMode = tint.blendMode.toSkiaBlendMode(),
      offset = maskOffset,
    )
  } else {
    ImageFilter.makeColorFilter(
      f = ColorFilter.makeBlend(color.toArgb(), tint.blendMode.toSkiaBlendMode()),
      input = this,
      crop = null,
    )
  }
}

private fun ImageFilter.withMask(
  brush: Brush?,
  size: Size,
  offset: Offset,
  blendMode: BlendMode = BlendMode.DST_IN,
): ImageFilter {
  val shader = brush?.toShader(size) ?: return this
  return blendWith(ImageFilter.makeShader(shader, crop = null), blendMode, offset)
}

private fun ImageFilter.blendWith(
  foreground: ImageFilter,
  blendMode: BlendMode,
  offset: Offset = Offset.Zero,
): ImageFilter = ImageFilter.makeBlend(
  blendMode = blendMode,
  fg = ImageFilter.makeOffset(offset.x, offset.y, foreground, crop = null),
  bg = this,
  crop = null,
)

private fun createBlurImageFilter(blurRadiusPx: Float, bounds: Rect): ImageFilter {
  val sigma = BlurEffect.convertRadiusToSigma(blurRadiusPx)
  return ImageFilter.makeBlur(
    sigmaX = sigma,
    sigmaY = sigma,
    mode = FilterTileMode.CLAMP,
    crop = bounds.toIRect(),
  )
}

private fun createBlurImageFilterWithMask(
  blurRadiusPx: Float,
  bounds: Rect,
  mask: Shader,
): ImageFilter {
  fun shader(vertical: Boolean): ImageFilter {
    return ImageFilter.makeRuntimeShader(
      RuntimeShaderBuilder(BLUR_SHADER).apply {
        uniform("blurRadius", blurRadiusPx)
        uniform("direction", if (vertical) 1 else 0)
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

private fun Brush.toShader(size: Size): Shader? = when (this) {
  is ShaderBrush -> createShader(size)
  else -> null
}

private fun Rect.toIRect(): IRect =
  IRect.makeLTRB(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
