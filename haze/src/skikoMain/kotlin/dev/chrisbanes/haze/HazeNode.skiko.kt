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
  size: Size,
  offsetInLayer: Offset,
  layerSize: Size,
  mask: Brush?,
): RenderEffect? {
  log("HazeChildNode") {
    "createRenderEffect. " +
      "blurRadiusPx=$blurRadiusPx, " +
      "noiseFactor=$noiseFactor, " +
      "tints=$tints, " +
      "size=$size, " +
      "offset=$offsetInLayer, " +
      "layerSize=$layerSize"
  }

  require(blurRadiusPx >= 0f) { "blurRadius needs to be equal or greater than 0f" }

  val compositeShaderBuilder = RuntimeShaderBuilder(RUNTIME_SHADER).apply {
    uniform("noiseFactor", noiseFactor.coerceIn(0f, 1f))
    child("noise", NOISE_SHADER)
  }

  // For CLAMP to work, we need to provide the crop rect
  val blurFilter = createBlurImageFilter(blurRadiusPx, layerSize.toRect())

  return ImageFilter
    .makeRuntimeShader(
      runtimeShaderBuilder = compositeShaderBuilder,
      shaderNames = arrayOf("content", "blur"),
      inputs = arrayOf(null, blurFilter),
    )
    .withTints(tints, tintAlphaModulate)
    .withBrush(mask, size, offsetInLayer, BlendMode.DST_IN)
    .asComposeRenderEffect()
}

private fun ImageFilter.withTints(tints: List<HazeTint>, alphaModulate: Float): ImageFilter {
  return tints.fold(this) { acc, tint ->
    acc.withTint(tint, alphaModulate)
  }
}

private fun ImageFilter.withTint(tint: HazeTint?, alphaModulate: Float): ImageFilter {
  if (tint != null) {
    val color = tint.color
    val modulated = color.copy(alpha = color.alpha * alphaModulate)

    if (modulated.alpha >= 0.005f) {
      return ImageFilter.makeColorFilter(
        f = ColorFilter.makeBlend(
          color = modulated.toArgb(),
          mode = tint.blendMode.toSkiaBlendMode(),
        ),
        input = this,
        crop = null,
      )
    }
  }

  return this
}

private fun ImageFilter.withBrush(
  brush: Brush?,
  size: Size,
  offset: Offset,
  blendMode: BlendMode,
): ImageFilter {
  val shader = brush?.toShader(size) ?: return this

  return ImageFilter.makeBlend(
    blendMode = blendMode,
    fg = ImageFilter.makeOffset(
      dx = offset.x,
      dy = offset.y,
      input = ImageFilter.makeShader(shader = shader, crop = null),
      crop = null,
    ),
    bg = this,
    crop = null,
  )
}

private fun createBlurImageFilter(blurRadiusPx: Float, bounds: Rect? = null): ImageFilter {
  val sigma = BlurEffect.convertRadiusToSigma(blurRadiusPx)
  return ImageFilter.makeBlur(
    sigmaX = sigma,
    sigmaY = sigma,
    mode = FilterTileMode.CLAMP,
    crop = bounds?.toIRect(),
  )
}

private fun Brush.toShader(size: Size): Shader? = when (this) {
  is ShaderBrush -> createShader(size)
  else -> null
}

private fun Rect.toIRect(): IRect =
  IRect.makeLTRB(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
