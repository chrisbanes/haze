// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.DirtyFields
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.calculateLength
import dev.chrisbanes.haze.createAndDrawScaledContentLayer
import dev.chrisbanes.haze.lerp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalHazeApi::class)
internal class RenderEffectBlurVisualEffectDelegate(
  val blurVisualEffect: BlurVisualEffect,
) : BlurVisualEffect.Delegate {
  private var renderEffect: RenderEffect? = null

  override fun DrawScope.draw() {
    val node = blurVisualEffect.requireNode()

    blurVisualEffect.createAndDrawScaledContentLayer(
      drawScope = this,
      node = node,
      scale = blurVisualEffect.calculateInputScaleFactor(node.inputScale),
      backgroundColor = blurVisualEffect.backgroundColor,
      areas = node.areas,
      positionOnScreen = node.positionOnScreen,
      layerSize = node.layerSize,
      layerOffset = node.layerOffset,
      clip = blurVisualEffect.shouldClip(),
    ) { layer ->
      val p = blurVisualEffect.progressive
      if (p != null) {
        drawProgressiveEffect(
          drawScope = this,
          progressive = p,
          contentLayer = layer,
        )
      } else {
        // First make sure that the RenderEffect is updated (if necessary)
        updateRenderEffectIfDirty()

        layer.renderEffect = renderEffect
        layer.alpha = blurVisualEffect.alpha

        // Since we included a border around the content, we need to translate so that
        // we don't see it (but it still affects the RenderEffect)
        drawLayer(layer)
      }
    }
  }

  private fun updateRenderEffectIfDirty() {
    if (renderEffect == null || blurVisualEffect.dirtyTracker.any(DirtyFields.RenderEffectAffectingFlags)) {
      renderEffect = blurVisualEffect.getOrCreateRenderEffect()
    }
  }
}

internal expect fun RenderEffectBlurVisualEffectDelegate.drawProgressiveEffect(
  drawScope: DrawScope,
  progressive: HazeProgressive,
  contentLayer: GraphicsLayer,
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
