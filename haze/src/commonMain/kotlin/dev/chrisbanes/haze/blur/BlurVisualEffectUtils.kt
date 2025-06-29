// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectNode
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.SimpleLruCache
import dev.chrisbanes.haze.requirePlatformContext
import dev.chrisbanes.haze.trace
import dev.chrisbanes.haze.unsynchronizedLazy

/**
 * Calculates the blur tile mode for a blur visual effect.
 */
internal fun BlurVisualEffect.calculateBlurTileMode(): TileMode = when (blurredEdgeTreatment) {
  BlurredEdgeTreatment.Unbounded -> TileMode.Decal
  else -> TileMode.Clamp
}

@OptIn(ExperimentalHazeApi::class)
internal fun BlurVisualEffect.getOrCreateRenderEffect(
  node: HazeEffectNode = requireNode(),
  inputScale: Float = calculateInputScaleFactor(node.inputScale),
  blurRadius: Dp = this.blurRadius.takeOrElse { 0.dp },
  noiseFactor: Float = this.noiseFactor,
  tints: List<HazeTint> = this.tints,
  tintAlphaModulate: Float = 1f,
  contentSize: Size = node.size,
  contentOffset: Offset = node.layerOffset,
  mask: Brush? = this.mask,
  progressive: HazeProgressive? = null,
  blurTileMode: TileMode = calculateBlurTileMode(),
): RenderEffect? = trace("HazeEffectNode-getOrCreateRenderEffect") {
  getOrCreateRenderEffect(
    node = node,
    params = RenderEffectParams(
      blurRadius = blurRadius,
      noiseFactor = noiseFactor,
      scale = inputScale,
      tints = tints,
      tintAlphaModulate = tintAlphaModulate,
      contentSize = contentSize,
      contentOffset = contentOffset,
      mask = mask,
      progressive = progressive,
      blurTileMode = blurTileMode,
    ),
  )
}

private val renderEffectCache by unsynchronizedLazy {
  SimpleLruCache<RenderEffectParams, RenderEffect>(10)
}

@Poko
internal class RenderEffectParams(
  val blurRadius: Dp,
  val noiseFactor: Float,
  val scale: Float,
  val contentSize: Size,
  val contentOffset: Offset,
  val tints: List<HazeTint> = emptyList(),
  val tintAlphaModulate: Float = 1f,
  val mask: Brush? = null,
  val progressive: HazeProgressive? = null,
  val blurTileMode: TileMode,
)

private fun getOrCreateRenderEffect(
  node: CompositionLocalConsumerModifierNode,
  params: RenderEffectParams,
): RenderEffect? {
  HazeLogger.d(HazeEffectNode.TAG) { "getOrCreateRenderEffect: $params" }
  val cached = renderEffectCache[params]
  if (cached != null) {
    HazeLogger.d(HazeEffectNode.TAG) { "getOrCreateRenderEffect. Returning cached: $params" }
    return cached
  }

  HazeLogger.d(HazeEffectNode.TAG) { "getOrCreateRenderEffect. Creating: $params" }
  return createBlurRenderEffect(
    context = node.requirePlatformContext(),
    density = node.requireDensity(),
    params = params,
  )?.also { renderEffectCache[params] = it }
}
