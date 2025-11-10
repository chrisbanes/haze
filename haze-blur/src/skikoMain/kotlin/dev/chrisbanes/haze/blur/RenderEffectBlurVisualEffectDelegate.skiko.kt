// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import dev.chrisbanes.haze.VisualEffectContext

internal actual fun RenderEffectBlurVisualEffectDelegate.drawProgressiveEffect(
  context: VisualEffectContext,
  drawScope: DrawScope,
  progressive: HazeProgressive,
  contentLayer: GraphicsLayer,
) = with(drawScope) {
  contentLayer.renderEffect = visualEffect.getOrCreateRenderEffect(context, progressive = progressive)
  contentLayer.alpha = visualEffect.alpha

  // Finally draw the layer
  drawLayer(contentLayer)
}
