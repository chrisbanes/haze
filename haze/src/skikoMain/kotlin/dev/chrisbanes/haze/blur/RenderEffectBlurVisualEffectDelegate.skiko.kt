// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import dev.chrisbanes.haze.HazeProgressive

internal actual fun RenderEffectBlurVisualEffectDelegate.drawProgressiveEffect(
  drawScope: DrawScope,
  progressive: HazeProgressive,
  contentLayer: GraphicsLayer,
) = with(drawScope) {
  contentLayer.renderEffect = blurVisualEffect.getOrCreateRenderEffect(progressive = progressive)
  contentLayer.alpha = blurVisualEffect.alpha

  // Finally draw the layer
  drawLayer(contentLayer)
}
