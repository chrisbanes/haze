// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class, ExperimentalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.withGraphicsLayer

@OptIn(InternalHazeApi::class)
private const val USE_RUNTIME_SHADER = true

@RequiresApi(31)
internal actual fun RenderEffectBlurVisualEffectDelegate.drawProgressiveEffect(
  drawScope: DrawScope,
  progressive: HazeProgressive,
  contentLayer: GraphicsLayer,
  context: VisualEffectContext,
) {
  if (USE_RUNTIME_SHADER && Build.VERSION.SDK_INT >= 33) {
    with(drawScope) {
      contentLayer.renderEffect = blurVisualEffect.getOrCreateRenderEffect(context = context, progressive = progressive)
      contentLayer.alpha = blurVisualEffect.alpha

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
      contentLayer.renderEffect = blurVisualEffect.getOrCreateRenderEffect(context = context, mask = progressive.asBrush())
      contentLayer.alpha = blurVisualEffect.alpha

      // Finally draw the layer
      drawLayer(contentLayer)
    }
  }
}

private fun RenderEffectBlurVisualEffectDelegate.drawLinearGradientProgressiveEffectUsingLayers(
  drawScope: DrawScope,
  progressive: HazeProgressive.LinearGradient,
  contentLayer: GraphicsLayer,
  context: VisualEffectContext,
) = with(drawScope) {
  val colorEffects = blurVisualEffect.colorEffects
  val noiseFactor = blurVisualEffect.noiseFactor
  val blurRadius = blurVisualEffect.blurRadius.takeOrElse { 0.dp } *
    blurVisualEffect.calculateInputScaleFactor(context.inputScale)

  drawProgressiveWithMultipleLayers(progressive) { mask, intensity ->
    context.withGraphicsLayer { layer ->
      layer.record(contentLayer.size) {
        drawLayer(contentLayer)
      }

      HazeLogger.d(RenderEffectBlurVisualEffectDelegate.TAG) {
        "drawLinearGradientProgressiveEffectUsingLayers. mask=$mask, intensity=$intensity"
      }

      layer.renderEffect = blurVisualEffect.getOrCreateRenderEffect(
        context = context,
        blurRadius = blurRadius * intensity,
        noiseFactor = noiseFactor,
        colorEffects = colorEffects,
        colorEffectsAlphaModulate = intensity,
        mask = mask,
      )
      layer.alpha = blurVisualEffect.alpha

      // Since we included a border around the content, we need to translate so that
      // we don't see it (but it still affects the RenderEffect)
      drawLayer(layer)
    }
  }
}
