// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer

internal actual fun HazeChildNode.drawLinearGradientProgressiveEffect(
  drawScope: DrawScope,
  progressive: HazeProgressive.LinearGradient,
  contentLayer: GraphicsLayer,
) = with(drawScope) {
  contentLayer.renderEffect = getOrCreateRenderEffect(progressive = progressive.asBrush())
  contentLayer.alpha = alpha

  // Finally draw the layer
  drawLayer(contentLayer)
}
