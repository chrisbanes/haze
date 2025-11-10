// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.VisualEffectContext

@OptIn(InternalHazeApi::class)
private const val USE_RUNTIME_SHADER = true

@RequiresApi(31)
internal actual fun RenderEffectBlurVisualEffectDelegate.drawProgressiveEffect(
  context: VisualEffectContext,
  drawScope: DrawScope,
  progressive: HazeProgressive,
  contentLayer: GraphicsLayer,
) {
  if (USE_RUNTIME_SHADER && Build.VERSION.SDK_INT >= 33) {
    with(drawScope) {
      contentLayer.renderEffect = visualEffect.getOrCreateRenderEffect(
        context = context,
        progressive = progressive,
      )
      contentLayer.alpha = visualEffect.alpha

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
      context = context,
    )
  } else {
    // Otherwise we convert it to a mask
    with(drawScope) {
      contentLayer.renderEffect = visualEffect.getOrCreateRenderEffect(
        context = context,
        mask = progressive.asBrush(),
      )
      contentLayer.alpha = visualEffect.alpha

      // Finally draw the layer
      drawLayer(contentLayer)
    }
  }
}

private fun RenderEffectBlurVisualEffectDelegate.drawLinearGradientProgressiveEffectUsingLayers(
  context: VisualEffectContext,
  drawScope: DrawScope,
  progressive: HazeProgressive.LinearGradient,
  contentLayer: GraphicsLayer,
) = with(drawScope) {
  val tints = visualEffect.tints
  val noiseFactor = visualEffect.noiseFactor
  val blurRadius = visualEffect.blurRadius.takeOrElse { 0.dp } *
    visualEffect.calculateInputScaleFactor(context)

  drawProgressiveWithMultipleLayers(progressive) { mask, intensity ->
    context.withGraphicsLayer { layer ->
      layer.record(contentLayer.size) {
        drawLayer(contentLayer)
      }

      HazeLogger.d(RenderEffectBlurVisualEffectDelegate.TAG) {
        "drawLinearGradientProgressiveEffectUsingLayers. mask=$mask, intensity=$intensity"
      }

      layer.renderEffect = visualEffect.getOrCreateRenderEffect(
        context = context,
        blurRadius = blurRadius * intensity,
        noiseFactor = noiseFactor,
        tints = tints,
        tintAlphaModulate = intensity,
        mask = mask,
      )
      layer.alpha = visualEffect.alpha

      // Since we included a border around the content, we need to translate so that
      // we don't see it (but it still affects the RenderEffect)
      drawLayer(layer)
    }
  }
}
