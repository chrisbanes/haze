// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.VisualEffectContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal class RenderEffectBlurVisualEffectDelegate(
  val blurVisualEffect: BlurVisualEffect,
) : BlurVisualEffect.Delegate {
  private var renderEffect: RenderEffect? = null
  private var scaledContentLayer: GraphicsLayer? = null
  private var lastScaledLayerSize: Size? = null
  private var graphicsContext: GraphicsContext? = null

  override fun DrawScope.draw(context: VisualEffectContext) {
    // Calculate scaled layer size to detect size changes (needs re-allocation)
    val scaleFactor = blurVisualEffect.calculateInputScaleFactor(context.inputScale)
    val currentScaledSize = (context.layerSize * scaleFactor).roundToIntSize().let {
      Size(it.width.toFloat(), it.height.toFloat())
    }

    // Allocate the scaled content layer once, re-recording into it each frame
    if (scaledContentLayer == null || scaledContentLayer!!.isReleased || lastScaledLayerSize != currentScaledSize) {
      graphicsContext = context.requireGraphicsContext()
      scaledContentLayer?.let { graphicsContext!!.releaseGraphicsLayer(it) }
      scaledContentLayer = graphicsContext!!.createGraphicsLayer()
      lastScaledLayerSize = currentScaledSize
    }

    val layer = scaledContentLayer!!

    // Always re-record — the source area layers change each frame during scrolling.
    // Returns null when the scaled size is 0 (e.g., window minimized).
    val resultLayer = createScaledContentLayer(
      context = context,
      scaleFactor = scaleFactor,
      layerSize = context.layerSize,
      layerOffset = context.layerOffset,
      backgroundColor = blurVisualEffect.backgroundColor,
      existingLayer = layer,
    ) ?: return

    val clip = blurVisualEffect.shouldClip()
    resultLayer.clip = clip

    drawScaledContent(
      offset = -context.layerOffset,
      scaledSize = size * scaleFactor,
      clip = clip,
    ) {
      val p = blurVisualEffect.progressive
      if (p != null) {
        drawProgressiveEffect(
          drawScope = this,
          progressive = p,
          contentLayer = layer,
          context = context,
        )
      } else {
        updateRenderEffectIfDirty(context)
        layer.renderEffect = renderEffect
        layer.alpha = blurVisualEffect.alpha
        drawLayer(layer)
      }
    }
  }

  override fun detach() {
    scaledContentLayer?.let { layer ->
      graphicsContext?.releaseGraphicsLayer(layer)
    }
    scaledContentLayer = null
    lastScaledLayerSize = null
    graphicsContext = null
  }

  private fun updateRenderEffectIfDirty(context: VisualEffectContext) {
    // Always resolve the current RenderEffect using the memoized cache keyed by params.
    // This ensures that changes coming from either the effect itself OR the hosting node
    // (e.g., size, layer offset, input scale, etc.) will be reflected without relying on
    // the effect's local dirty flags only.
    renderEffect = blurVisualEffect.getOrCreateRenderEffect(context)
  }

  companion object {
    const val TAG = "RenderEffectBlurVisualEffectDelegate"
  }
}

internal expect fun RenderEffectBlurVisualEffectDelegate.drawProgressiveEffect(
  drawScope: DrawScope,
  progressive: HazeProgressive,
  contentLayer: GraphicsLayer,
  context: VisualEffectContext,
)

internal fun DrawScope.drawProgressiveWithMultipleLayers(
  progressive: HazeProgressive.LinearGradient,
  stepHeight: Dp = 64.dp,
  block: (mask: Brush, intensity: Float) -> Unit,
) {
  require(progressive.startIntensity in 0f..1f)
  require(progressive.endIntensity in 0f..1f)

  // Here we're going to calculate an appropriate amount of steps for the length.
  // We use a calculation of 60dp per step, which is a good balance between
  // quality vs performance
  val stepHeightPx = with(drawContext.density) { stepHeight.toPx() }
  val length = calculateLength(progressive.start, progressive.end, size)
  val steps = ceil(length / stepHeightPx).toInt().coerceAtLeast(2)

  val seq = when {
    progressive.endIntensity >= progressive.startIntensity -> 0..steps
    else -> steps downTo 0
  }

  for (i in seq) {
    val fraction = i / steps.toFloat()

    val intensity = lerp(
      progressive.startIntensity,
      progressive.endIntensity,
      progressive.easing.transform(fraction),
    )

    val min = min(progressive.startIntensity, progressive.endIntensity)
    val max = max(progressive.startIntensity, progressive.endIntensity)

    val mask = Brush.linearGradient(
      lerp(min, max, (i - 2f) / steps) to Color.Transparent,
      lerp(min, max, (i - 1f) / steps) to Color.Black,
      lerp(min, max, (i + 0f) / steps) to Color.Black,
      lerp(min, max, (i + 1f) / steps) to Color.Transparent,
      start = progressive.start,
      end = progressive.end,
    )

    block(mask, intensity)
  }
}
