// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer

internal actual fun RenderEffectBlurEffect.drawProgressiveEffect(
  drawScope: DrawScope,
  progressive: HazeProgressive,
  contentLayer: GraphicsLayer,
) = with(drawScope) {
  contentLayer.renderEffect = node.getOrCreateRenderEffect(progressive = progressive)
  contentLayer.alpha = node.alpha

  // Finally draw the layer
  drawLayer(contentLayer)
}
