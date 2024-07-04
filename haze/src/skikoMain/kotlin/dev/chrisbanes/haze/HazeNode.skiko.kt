// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.unit.toIntRect
import androidx.compose.ui.unit.toRect
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeShaderBuilder

internal actual fun CompositionLocalConsumerModifierNode.calculateWindowOffset(): Offset {
  // The Skiko-backed platforms don't use native windows for dialogs, etc
  return Offset.Zero
}

internal actual fun CompositionLocalConsumerModifierNode.updateRenderEffect(effect: Effect) {
  val compositeShaderBuilder = RuntimeShaderBuilder(RUNTIME_SHADER).apply {
    val tint = effect.tint
    uniform("color", tint.red, tint.green, tint.blue, 1f)
    uniform("colorShift", tint.alpha)
    uniform("noiseFactor", effect.noiseFactor)
    child("noise", NOISE_SHADER)
  }
  // For CLAMP to work, we need to provide the crop rect
  val blurFilter = createBlurImageFilter(effect.blurRadiusPx, effect.layer.size.toIntRect().toRect())

  val filter = ImageFilter.makeRuntimeShader(
    runtimeShaderBuilder = compositeShaderBuilder,
    shaderNames = arrayOf("content", "blur"),
    inputs = arrayOf(null, blurFilter),
  )

  effect.layer.renderEffect = filter.asComposeRenderEffect()
}

private fun createBlurImageFilter(
  blurRadiusPx: Float,
  cropRect: Rect? = null,
): ImageFilter {
  val sigma = BlurEffect.convertRadiusToSigma(blurRadiusPx)
  return ImageFilter.makeBlur(
    sigmaX = sigma,
    sigmaY = sigma,
    mode = FilterTileMode.CLAMP,
    crop = cropRect?.toIRect(),
  )
}

private fun Rect.toIRect(): IRect {
  return IRect.makeLTRB(
    left.toInt(),
    top.toInt(),
    right.toInt(),
    bottom.toInt(),
  )
}
