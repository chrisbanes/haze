// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeShaderBuilder

internal actual fun HazeNode.drawEffect(
  drawScope: DrawScope,
  effect: HazeEffect,
  graphicsLayer: GraphicsLayer?,
) = with(drawScope) {
  drawLayer(requireNotNull(graphicsLayer))
}

internal actual fun HazeNode.useGraphicsLayers(): Boolean = true

internal actual fun HazeNode.createRenderEffect(effect: HazeEffect, density: Density): RenderEffect? {
  val compositeShaderBuilder = RuntimeShaderBuilder(RUNTIME_SHADER).apply {
    uniform("noiseFactor", effect.noiseFactor)
    child("noise", NOISE_SHADER)
  }
  // For CLAMP to work, we need to provide the crop rect
  val blurRadiusPx = with(density) { effect.blurRadius.toPx() }
  val blurFilter = createBlurImageFilter(blurRadiusPx, effect.bounds.size.toRect())

  val filter = ImageFilter.makeRuntimeShader(
    runtimeShaderBuilder = compositeShaderBuilder,
    shaderNames = arrayOf("content", "blur"),
    inputs = arrayOf(null, blurFilter),
  )

  return filter.asComposeRenderEffect()
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

private fun Rect.toIRect(): IRect =
  IRect.makeLTRB(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
