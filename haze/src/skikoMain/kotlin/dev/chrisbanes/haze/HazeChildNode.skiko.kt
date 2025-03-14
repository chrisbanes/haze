// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer

internal actual fun HazeEffectNode.drawProgressiveEffect(
  drawScope: DrawScope,
  progressive: HazeProgressive,
  contentLayer: GraphicsLayer,
) = with(drawScope) {
  contentLayer.renderEffect = getOrCreateRenderEffect(progressive = progressive)
  contentLayer.alpha = alpha

  // Finally draw the layer
  drawLayer(contentLayer)
}
