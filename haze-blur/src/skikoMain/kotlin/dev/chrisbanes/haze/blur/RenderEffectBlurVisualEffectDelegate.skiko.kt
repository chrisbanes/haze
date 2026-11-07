// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.toSize
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.scaledContentScale

internal actual fun BlurVisualEffect.invalidateRenderEffectOnInputChange() = Unit

@OptIn(InternalHazeApi::class)
internal actual fun RenderEffectBlurVisualEffectDelegate.drawProgressiveEffect(
  drawScope: DrawScope,
  progressive: HazeProgressive,
  contentLayer: GraphicsLayer,
  context: HazeEffectRuntimeDrawScope,
  inputScale: Float,
) = with(drawScope) {
  effectSlot.apply(
    contentLayer,
    blurVisualEffect.getOrCreateRenderEffect(
      context = context,
      inputScale = inputScale,
      captureScale = scaledContentScale(context.layerSize, inputScale),
      captureSize = contentLayer.size.toSize(),
      progressive = progressive,
      slot = effectSlot,
    ),
  )
  contentLayer.alpha = blurVisualEffect.alpha

  // Finally draw the layer
  drawLayer(contentLayer)
}

@OptIn(InternalHazeApi::class)
internal actual fun RenderEffectBlurVisualEffectDelegate.drawLogicalCustomStyle(
  drawScope: DrawScope,
  contentLayer: GraphicsLayer,
  context: HazeEffectRuntimeDrawScope,
  inputScale: Float,
): Boolean = false
