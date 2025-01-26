// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.HazeEffectNode.Companion.TAG
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private const val USE_RUNTIME_SHADER = true

@RequiresApi(31)
internal actual fun HazeEffectNode.drawProgressiveEffect(
  drawScope: DrawScope,
  progressive: HazeProgressive,
  contentLayer: GraphicsLayer,
) {
  if (USE_RUNTIME_SHADER && Build.VERSION.SDK_INT >= 33) {
    with(drawScope) {
      contentLayer.renderEffect = getOrCreateRenderEffect(progressive = progressive)
      contentLayer.alpha = alpha

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
      contentLayer.renderEffect = getOrCreateRenderEffect(mask = progressive.asBrush())
      contentLayer.alpha = alpha

      // Finally draw the layer
      drawLayer(contentLayer)
    }
  }
}

private fun HazeEffectNode.drawLinearGradientProgressiveEffectUsingLayers(
  drawScope: DrawScope,
  progressive: HazeProgressive.LinearGradient,
  contentLayer: GraphicsLayer,
) = with(drawScope) {
  require(progressive.startIntensity in 0f..1f)
  require(progressive.endIntensity in 0f..1f)

  // Here we're going to calculate an appropriate amount of steps for the length.
  // We use a calculation of 60dp per step, which is a good balance between
  // quality vs performance
  val stepHeightPx = with(drawContext.density) { 60.dp.toPx() }
  val length = calculateLength(progressive.start, progressive.end, size)
  val steps = ceil(length / stepHeightPx).toInt().coerceAtLeast(2)

  val graphicsContext = currentValueOf(LocalGraphicsContext)

  val seq = when {
    progressive.endIntensity >= progressive.startIntensity -> 0..steps
    else -> steps downTo 0
  }

  val tints = resolveTints()
  val noiseFactor = resolveNoiseFactor()
  val blurRadius = resolveBlurRadius().takeOrElse { 0.dp } * calculateInputScaleFactor()

  for (i in seq) {
    val fraction = i / steps.toFloat()
    val intensity = lerp(
      progressive.startIntensity,
      progressive.endIntensity,
      progressive.easing.transform(fraction),
    )

    val layer = graphicsContext.createGraphicsLayer()
    layer.record(contentLayer.size) {
      drawLayer(contentLayer)
    }

    log(TAG) {
      "drawProgressiveEffect. " +
        "step=$i, " +
        "fraction=$fraction, " +
        "intensity=$intensity"
    }

    val min = min(progressive.startIntensity, progressive.endIntensity)
    val max = max(progressive.startIntensity, progressive.endIntensity)

    layer.renderEffect = getOrCreateRenderEffect(
      blurRadius = blurRadius * intensity,
      noiseFactor = noiseFactor,
      tints = tints,
      tintAlphaModulate = intensity,
      mask = Brush.linearGradient(
        lerp(min, max, (i - 2f) / steps) to Color.Transparent,
        lerp(min, max, (i - 1f) / steps) to Color.Black,
        lerp(min, max, (i + 0f) / steps) to Color.Black,
        lerp(min, max, (i + 1f) / steps) to Color.Transparent,
        start = progressive.start,
        end = progressive.end,
      ),
    )
    layer.alpha = alpha

    // Since we included a border around the content, we need to translate so that
    // we don't see it (but it still affects the RenderEffect)
    drawLayer(layer)

    graphicsContext.releaseGraphicsLayer(layer)
  }
}
