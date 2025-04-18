// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.HazeEffectNode.Companion.TAG

private const val USE_RUNTIME_SHADER = true

@RequiresApi(31)
internal actual fun RenderEffectBlurEffect.drawProgressiveEffect(
  drawScope: DrawScope,
  progressive: HazeProgressive,
  contentLayer: GraphicsLayer,
) {
  if (USE_RUNTIME_SHADER && Build.VERSION.SDK_INT >= 33) {
    with(drawScope) {
      contentLayer.renderEffect = node.getOrCreateRenderEffect(progressive = progressive)
      contentLayer.alpha = node.alpha

      // Finally draw the layer
      drawLayer(contentLayer)
    }
  } else if (progressive is HazeProgressive.LinearGradient && !progressive.preferPerformance) {
    // If it's a linear gradient, and the 'preferPerformance' flag is not enabled, we can use
    // our slow approximated version
    drawLinearGradientProgressiveEffectUsingLayers(
      drawScope = drawScope,
      progressive = progressive,
      contentLayer = contentLayer,
    )
  } else {
    // Otherwise we convert it to a mask
    with(drawScope) {
      contentLayer.renderEffect = node.getOrCreateRenderEffect(mask = progressive.asBrush())
      contentLayer.alpha = node.alpha

      // Finally draw the layer
      drawLayer(contentLayer)
    }
  }
}

private fun RenderEffectBlurEffect.drawLinearGradientProgressiveEffectUsingLayers(
  drawScope: DrawScope,
  progressive: HazeProgressive.LinearGradient,
  contentLayer: GraphicsLayer,
) = with(drawScope) {
  val tints = node.resolveTints()
  val noiseFactor = node.resolveNoiseFactor()
  val blurRadius = node.resolveBlurRadius().takeOrElse { 0.dp } *
    node.calculateInputScaleFactor()

  drawProgressiveWithMultipleLayers(progressive) { mask, intensity ->
    node.withGraphicsLayer { layer ->
      layer.record(contentLayer.size) {
        drawLayer(contentLayer)
      }

      HazeLogger.d(TAG) {
        "drawLinearGradientProgressiveEffectUsingLayers. mask=$mask, intensity=$intensity"
      }

      layer.renderEffect = node.getOrCreateRenderEffect(
        blurRadius = blurRadius * intensity,
        noiseFactor = noiseFactor,
        tints = tints,
        tintAlphaModulate = intensity,
        mask = mask,
      )
      layer.alpha = node.alpha

      // Since we included a border around the content, we need to translate so that
      // we don't see it (but it still affects the RenderEffect)
      drawLayer(layer)
    }
  }
}
