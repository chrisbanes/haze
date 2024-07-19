// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

internal actual fun HazeEffectNode.drawEffect(
  drawScope: DrawScope,
  effect: HazeEffect,
  graphicsLayer: GraphicsLayer?,
) = with(drawScope) {
  drawLayer(requireNotNull(graphicsLayer))
}

internal actual val USE_GRAPHICS_LAYERS: Boolean = true

internal actual fun HazeEffectNode.createRenderEffect(
  effect: HazeEffect,
  density: Density,
): RenderEffect? {
  val compositeShaderBuilder = RuntimeShaderBuilder(RUNTIME_SHADER).apply {
    uniform("noiseFactor", effect.noiseFactor)
    child("noise", NOISE_SHADER)
  }
  // For CLAMP to work, we need to provide the crop rect
  val blurRadiusPx = with(density) { effect.blurRadiusOrZero.toPx() }
  val blurFilter = createBlurImageFilter(blurRadiusPx, effect.size.toRect())

  return ImageFilter.makeRuntimeShader(
    runtimeShaderBuilder = compositeShaderBuilder,
    shaderNames = arrayOf("content", "blur"),
    inputs = arrayOf(null, blurFilter),
  )
    .withTint(effect.tint, BlendMode.SRC_ATOP)
    .withMask(effect.mask, effect.size)
    .asComposeRenderEffect()
}

private fun ImageFilter.withTint(
  tint: Color,
  blendMode: BlendMode,
): ImageFilter = when {
  tint.alpha >= 0.005f -> {
    // If we have an tint with a non-zero alpha value, wrap the effect with a color filter
    ImageFilter.makeColorFilter(
      f = ColorFilter.makeBlend(tint.toArgb(), blendMode),
      input = this,
      crop = null,
    )
  }

  else -> this
}

private fun ImageFilter.withMask(mask: Brush?, size: Size): ImageFilter {
  val shader = mask?.toShader(size) ?: return this

  return ImageFilter.makeBlend(
    blendMode = BlendMode.DST_IN,
    bg = this,
    fg = ImageFilter.makeShader(shader = shader, crop = null),
    crop = null,
  )
}

private fun createBlurImageFilter(blurRadiusPx: Float, cropRect: Rect? = null): ImageFilter {
  val sigma = BlurEffect.convertRadiusToSigma(blurRadiusPx)
  return ImageFilter.makeBlur(
    sigmaX = sigma,
    sigmaY = sigma,
    mode = FilterTileMode.CLAMP,
    crop = cropRect?.toIRect(),
  )
}

private fun Brush.toShader(size: Size): Shader? = when (this) {
  is ShaderBrush -> createShader(size)
  else -> null
}

private fun Rect.toIRect(): IRect =
  IRect.makeLTRB(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
