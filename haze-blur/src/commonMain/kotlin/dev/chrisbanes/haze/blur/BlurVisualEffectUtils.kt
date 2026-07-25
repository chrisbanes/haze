// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.collection.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.trace

/**
 * Calculates the blur tile mode for a blur visual effect.
 */
internal fun BlurVisualEffect.calculateBlurTileMode(): TileMode = when (blurredEdgeTreatment) {
  androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded -> TileMode.Decal
  else -> TileMode.Clamp
}

@OptIn(ExperimentalHazeApi::class)
internal fun BlurVisualEffect.getOrCreateRenderEffect(
  context: VisualEffectContext,
  inputScale: Float = resolveInputScaleFactor(context.inputScale),
  blurRadius: Dp = this.blurRadius.takeOrElse { 0.dp },
  noiseFactor: Float = this.noiseFactor,
  colorEffects: List<HazeColorEffect> = this.colorEffects.orEmpty(),
  colorEffectsAlphaModulate: Float = 1f,
  contentSize: Size = context.size,
  contentOffset: Offset = context.layerOffset,
  mask: Brush? = this.mask,
  progressive: HazeProgressive? = null,
  blurTileMode: TileMode = calculateBlurTileMode(),
): RenderEffect? = trace("HazeEffectNode-getOrCreateRenderEffect") {
  getOrCreateRenderEffect(
    context = context,
    params = RenderEffectParams(
      blurRadius = blurRadius,
      noiseFactor = noiseFactor,
      scale = inputScale,
      colorEffects = colorEffects,
      colorEffectsAlphaModulate = colorEffectsAlphaModulate,
      contentSize = contentSize,
      contentOffset = contentOffset,
      mask = mask,
      progressive = progressive,
      blurTileMode = blurTileMode,
    ),
  )
}

private val renderEffectCache = lazy(mode = LazyThreadSafetyMode.NONE) {
  LruCache<RenderEffectCacheKey, RenderEffect>(maxSize = 50)
}

internal fun clearRenderEffectCache() {
  clearIfInitialized(renderEffectCache) { it.evictAll() }
}

internal inline fun <T> clearIfInitialized(lazyValue: Lazy<T>, clear: (T) -> Unit) {
  if (lazyValue.isInitialized()) {
    clear(lazyValue.value)
  }
}

@Poko
internal class RenderEffectParams(
  val blurRadius: Dp,
  val noiseFactor: Float,
  val scale: Float,
  val contentSize: Size,
  val contentOffset: Offset,
  val colorEffects: List<HazeColorEffect> = emptyList(),
  val colorEffectsAlphaModulate: Float = 1f,
  val mask: Brush? = null,
  val progressive: HazeProgressive? = null,
  val blurTileMode: TileMode,
)

internal data class RenderEffectCacheKey(
  val blurRadiusPx: Float,
  val noiseFactor: Float,
  val scale: Float,
  val contentSize: Size,
  val contentOffset: Offset,
  val colorEffects: List<HazeColorEffect>,
  val colorEffectsAlphaModulate: Float,
  val mask: Brush?,
  val progressive: HazeProgressive?,
  val blurTileMode: TileMode,
)

internal fun RenderEffectParams.resolveBlurRadiusPx(density: Density): Float =
  with(density) { (blurRadius * scale).toPx() }

internal fun RenderEffectParams.renderEffectCacheKey(density: Density): RenderEffectCacheKey {
  val hasBrushTint = colorEffects.any { it is HazeColorEffect.TintBrush }
  val hasOffsetColorEffect = colorEffects.any {
    it is HazeColorEffect.TintBrush || it is HazeColorEffect.ColorFilter
  }
  val usesContentSize = progressive != null || mask != null || hasBrushTint
  val usesContentOffset = progressive != null || mask != null || hasOffsetColorEffect

  return RenderEffectCacheKey(
    blurRadiusPx = resolveBlurRadiusPx(density),
    noiseFactor = if (noiseFactor.hasVisibleNoise()) noiseFactor else 0f,
    scale = scale,
    contentSize = if (usesContentSize) contentSize else Size.Zero,
    contentOffset = if (usesContentOffset) contentOffset else Offset.Zero,
    colorEffects = colorEffects,
    colorEffectsAlphaModulate = colorEffectsAlphaModulate,
    mask = mask,
    progressive = progressive,
    blurTileMode = blurTileMode,
  )
}

@OptIn(ExperimentalHazeApi::class)
private fun getOrCreateRenderEffect(context: VisualEffectContext, params: RenderEffectParams): RenderEffect? {
  HazeLogger.d(BlurVisualEffect.TAG) { "getOrCreateRenderEffect: $params" }
  val density = context.requireDensity()
  val cacheKey = params.renderEffectCacheKey(density)
  val cached = renderEffectCache.value[cacheKey]
  if (cached != null) {
    HazeLogger.d(BlurVisualEffect.TAG) { "getOrCreateRenderEffect. Returning cached: $params" }
    return cached
  }

  HazeLogger.d(BlurVisualEffect.TAG) { "getOrCreateRenderEffect. Creating: $params" }
  return trace("HazeBlur-createRenderEffect") {
    createRenderEffect(
      context = context.requirePlatformContext(),
      density = density,
      params = params,
    )
  }.also { effect ->
    renderEffectCache.value.put(cacheKey, effect)
  }
}
