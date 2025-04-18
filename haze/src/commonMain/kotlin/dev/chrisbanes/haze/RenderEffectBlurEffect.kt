// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalHazeApi::class)
internal class RenderEffectBlurEffect(
  internal val node: HazeEffectNode,
) : BlurEffect {
  private var renderEffect: RenderEffect? = null

  override fun DrawScope.drawEffect() {
    createAndDrawScaledContentLayer(node) { layer ->
      val p = node.progressive
      if (p != null) {
        drawProgressiveEffect(
          drawScope = this,
          progressive = p,
          contentLayer = layer,
        )
      } else {
        // First make sure that the RenderEffect is updated (if necessary)
        updateRenderEffectIfDirty(node)

        layer.renderEffect = renderEffect
        layer.alpha = node.alpha

        // Since we included a border around the content, we need to translate so that
        // we don't see it (but it still affects the RenderEffect)
        drawLayer(layer)
      }
    }
  }

  private fun updateRenderEffectIfDirty(node: HazeEffectNode) {
    if (renderEffect == null || node.dirtyTracker.any(DirtyFields.RenderEffectAffectingFlags)) {
      renderEffect = node.getOrCreateRenderEffect()
    }
  }
}

internal expect fun RenderEffectBlurEffect.drawProgressiveEffect(
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
