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
    size = size,
    offsetInLayer = layerOffset,
    layerSize = layerSize,
    mask = mask,
    progressive = progressive.asBrush(),
  )
  contentLayer.alpha = alpha

  // Since we included a border around the content, we need to translate so that
  // we don't see it (but it still affects the RenderEffect)
  drawLayer(contentLayer)
}
