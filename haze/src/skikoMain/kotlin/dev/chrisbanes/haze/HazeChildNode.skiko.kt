// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse

internal actual fun HazeChildNode.drawLinearGradientProgressiveEffect(
  drawScope: DrawScope,
  progressive: HazeProgressive.LinearGradient,
  contentLayer: GraphicsLayer,
) = with(drawScope) {
  contentLayer.renderEffect = createRenderEffect(
    blurRadiusPx = resolveBlurRadius().takeOrElse { 0.dp }.toPx(),
    noiseFactor = resolveNoiseFactor(),
    tints = resolveTints(),
    contentSize = size,
    contentOffset = contentOffset,
    layerSize = layerSize,
    mask = mask,
    progressive = progressive.asBrush(),
  )
  contentLayer.alpha = alpha

  // Finally draw the layer
  drawLayer(contentLayer)
}
