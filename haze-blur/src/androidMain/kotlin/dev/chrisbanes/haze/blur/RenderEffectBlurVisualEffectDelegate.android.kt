// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class, ExperimentalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.toSize
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

internal actual fun BlurVisualEffect.invalidateRenderEffectOnInputChange() {
  if (Build.VERSION.SDK_INT == 31) {
    // API 31 can retain blurred pixels after a nested source layer changes, even when the
    // input is re-recorded. A fresh RenderEffect refreshes the output without replacing the
    // capture layer, which another effect may be sampling.
    clearRenderEffectCache()
  }
}

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
          progressiveMask = progressive.asBrush(),
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
  // The retained capture may have been drawn through the ordinary or masked path.
  // Record the unprocessed input so its prior effect and alpha do not compound here.
  contentLayer.renderEffect = null
  contentLayer.alpha = 1f

  val colorEffects = blurVisualEffect.colorEffects
  val noiseFactor = blurVisualEffect.noiseFactor
  val blurRadius = blurVisualEffect.blurRadius.takeOrElse { 0.dp }

  // Bands overlap, so fade their composite once: masking each band would not be equivalent.
  val userMask = blurVisualEffect.mask
  val layerSize = contentLayer.size.toSize()
  if (userMask != null) {
    drawContext.canvas.saveLayer(Rect(Offset.Zero, layerSize), Paint())
  }

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

  if (userMask != null) {
    // Cover the expanded layer so clamped mask values reach its outset, as in the shader path.
    translate(context.layerOffset) {
      drawRect(
        brush = userMask,
        topLeft = -context.layerOffset,
        size = layerSize,
        blendMode = BlendMode.DstIn,
      )
    }
    drawContext.canvas.restore()
  }
}
