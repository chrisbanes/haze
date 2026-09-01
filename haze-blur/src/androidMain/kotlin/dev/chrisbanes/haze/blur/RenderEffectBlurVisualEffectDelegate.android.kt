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
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.HazeProgressive as RootHazeProgressive
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.asBrush
import dev.chrisbanes.haze.asComposeRenderEffect
import dev.chrisbanes.haze.withGraphicsLayer

@OptIn(InternalHazeApi::class)
private const val USE_RUNTIME_SHADER = true

@RequiresApi(31)
internal actual fun RenderEffectBlurVisualEffectDelegate.drawProgressiveEffect(
  drawScope: DrawScope,
  progressive: RootHazeProgressive,
  contentLayer: GraphicsLayer,
  context: HazeEffectRuntimeDrawScope,
  inputScale: Float,
) {
  if (USE_RUNTIME_SHADER && Build.VERSION.SDK_INT >= 33) {
    with(drawScope) {
      contentLayer.renderEffect = blurVisualEffect
        .getOrCreateRenderEffect(
          context = context,
          inputScale = inputScale,
          progressive = progressive,
        )
        .asComposeRenderEffect()
      contentLayer.alpha = blurVisualEffect.alpha

      // Finally draw the layer
      drawLayer(contentLayer)
    }
  } else if (
    progressive is RootHazeProgressive.LinearGradient &&
    shouldDrawProgressiveWithLayers(progressive, inputScale)
  ) {
    // Full-resolution linear gradients use our slower, layered approximation.
    drawLinearGradientProgressiveEffectUsingLayers(
      drawScope = drawScope,
      progressive = progressive,
      contentLayer = contentLayer,
      context = context,
      inputScale = inputScale,
    )
  } else {
    // Otherwise draw the masked blur over its input, preserving unblurred regions.
    with(drawScope) {
      contentLayer.renderEffect = blurVisualEffect
        .getOrCreateRenderEffect(
          context = context,
          inputScale = inputScale,
          mask = progressive.asBrush(),
          retainInputWhenMasked = progressive is RootHazeProgressive.LinearGradient,
        )
        .asComposeRenderEffect()
      contentLayer.alpha = blurVisualEffect.alpha

      // Finally draw the layer
      drawLayer(contentLayer)
    }
  }
}

private fun RenderEffectBlurVisualEffectDelegate.drawLinearGradientProgressiveEffectUsingLayers(
  drawScope: DrawScope,
  progressive: RootHazeProgressive.LinearGradient,
  contentLayer: GraphicsLayer,
  context: HazeEffectRuntimeDrawScope,
  inputScale: Float,
) = with(drawScope) {
  val colorEffects = blurVisualEffect.colorEffects
  val noiseFactor = blurVisualEffect.noiseFactor
  val blurRadius = blurVisualEffect.blurRadius.takeOrElse { 0.dp }

  drawProgressiveWithMultipleLayers(progressive) { mask, intensity ->
    context.withGraphicsLayer { layer ->
      layer.record(contentLayer.size) {
        drawLayer(contentLayer)
      }

      HazeLogger.d(RenderEffectBlurVisualEffectDelegate.TAG) {
        "drawLinearGradientProgressiveEffectUsingLayers. mask=$mask, intensity=$intensity"
      }

      layer.renderEffect = blurVisualEffect
        .getOrCreateRenderEffect(
          context = context,
          inputScale = inputScale,
          blurRadius = blurRadius * intensity,
          noiseFactor = noiseFactor,
          colorEffects = colorEffects.orEmpty(),
          colorEffectsAlphaModulate = intensity,
          mask = mask,
        )
        .asComposeRenderEffect()
      layer.alpha = blurVisualEffect.alpha

      // Since we included a border around the content, we need to translate so that
      // we don't see it (but it still affects the RenderEffect)
      drawLayer(layer)
    }
  }
}
