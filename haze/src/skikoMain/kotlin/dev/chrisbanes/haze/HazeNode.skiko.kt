// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeShaderBuilder

internal actual fun HazeNode.drawEffect(
  drawScope: DrawScope,
  effect: Effect,
  graphicsLayer: GraphicsLayer?,
) = with(drawScope) {
  drawLayer(requireNotNull(graphicsLayer))
}

internal actual fun HazeNode.usingGraphicsLayers(): Boolean = true

internal actual fun HazeNode.updateRenderEffect(effect: Effect) {
  val compositeShaderBuilder = RuntimeShaderBuilder(RUNTIME_SHADER).apply {
    val tint = effect.tint
    uniform("color", tint.red, tint.green, tint.blue, 1f)
    uniform("colorShift", tint.alpha)
    uniform("noiseFactor", effect.noiseFactor)
    child("noise", NOISE_SHADER)
  }
  // For CLAMP to work, we need to provide the crop rect
  val blurRadiusPx = with(currentValueOf(LocalDensity)) { effect.blurRadius.toPx() }
  val blurFilter = createBlurImageFilter(blurRadiusPx, effect.bounds.size.toRect())

  val filter = ImageFilter.makeRuntimeShader(
    runtimeShaderBuilder = compositeShaderBuilder,
    shaderNames = arrayOf("content", "blur"),
    inputs = arrayOf(null, blurFilter),
  )

  effect.renderEffect = filter.asComposeRenderEffect()
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
