// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

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
  boundsInLayer: Rect,
  layerSize: Size,
  mask: Brush?,
): RenderEffect? {
  log("HazeChildNode") {
    "createRenderEffect. blurRadiusPx=$blurRadiusPx, " +
      "noiseFactor=$noiseFactor, " +
      "tints=$tints, " +
      "boundsInLayer=$boundsInLayer, " +
      "layerSize=$layerSize"
  }

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
    .withTints(tints, boundsInLayer)
    .withBrush(mask, boundsInLayer, BlendMode.DST_IN)
    .asComposeRenderEffect()
}

private fun ImageFilter.withTints(tints: List<HazeTint>, bounds: Rect): ImageFilter {
  return tints.fold(this) { acc, tint ->
    acc.withTint(tint, bounds)
  }
}

private fun ImageFilter.withTint(tint: HazeTint?, bounds: Rect): ImageFilter = when {
  tint is HazeTint.Color && tint.color.alpha >= 0.005f -> {
    ImageFilter.makeColorFilter(
      f = ColorFilter.makeBlend(tint.color.toArgb(), tint.blendMode.toSkiaBlendMode()),
      input = this,
      crop = null,
    )
  }

  tint is HazeTint.Brush -> {
    withBrush(tint.brush, bounds, tint.blendMode.toSkiaBlendMode())
  }

  else -> this
}

private fun ImageFilter.withBrush(
  brush: Brush?,
  bounds: Rect,
  blendMode: BlendMode,
): ImageFilter {
  val shader = brush?.toShader(bounds.size) ?: return this

  return ImageFilter.makeBlend(
    blendMode = blendMode,
    fg = ImageFilter.makeOffset(
      dx = bounds.left,
      dy = bounds.top,
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
