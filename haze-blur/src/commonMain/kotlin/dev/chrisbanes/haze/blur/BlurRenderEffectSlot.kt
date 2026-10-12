// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.asComposeRenderEffect

/**
 * A single-entry memo of one call site's last render-effect lookup, so an unchanged draw builds no
 * [RenderEffectParams] or [RenderEffectCacheKey], does no [BlurVisualEffect.renderEffectCache]
 * lookup, creates no Compose wrapper and reassigns nothing to a layer.
 *
 * [lookup] compares the raw inputs of the last [store]; it never normalizes them as
 * [renderEffectCacheKey] does, so it can miss for an irrelevant input but cannot return an effect
 * the cache would not. A miss takes the unchanged cache path, which returns the same platform
 * effect when the key is equal, so the wrapper and the layer assignment are still reused.
 *
 * Confined to the draw thread, like the delegate that owns it.
 */
internal open class BlurRenderEffectSlot {
  private var generation: Int = 0
  private var densityScale: Float = 0f
  private var params: RenderEffectParams? = null

  /** The platform effect the latest [lookup] hit or [store] saved. */
  var platformEffect: PlatformRenderEffect? = null
    private set

  private var wrappedPlatformEffect: PlatformRenderEffect? = null
  private var wrappedEffect: RenderEffect? = null
  private var appliedLayer: GraphicsLayer? = null
  private var appliedEffect: RenderEffect? = null

  /**
   * Returns the remembered effect when every input equals the last [store], or null.
   * [generation] is [BlurVisualEffect.renderEffectGeneration], so a cache clear always misses.
   */
  open fun lookup(
    generation: Int,
    densityScale: Float,
    blurRadius: Dp,
    noiseFactor: Float,
    scale: Float,
    colorEffects: List<HazeColorEffect>,
    colorEffectsAlphaModulate: Float,
    backgroundColor: Color,
    contentSize: Size,
    contentOffset: Offset,
    mask: Brush?,
    progressiveMask: Brush?,
    retainInputWhenMasked: Boolean,
    progressive: HazeProgressive?,
    blurTileMode: TileMode,
  ): PlatformRenderEffect? {
    val p = params ?: return null
    val effect = platformEffect ?: return null
    // NaN never equals itself, so a NaN input always misses and takes the slow path.
    return effect.takeIf {
      generation == this.generation &&
        densityScale == this.densityScale &&
        blurRadius == p.blurRadius &&
        noiseFactor == p.noiseFactor &&
        scale == p.scale &&
        contentSize == p.contentSize &&
        contentOffset == p.contentOffset &&
        colorEffectsAlphaModulate == p.colorEffectsAlphaModulate &&
        backgroundColor == p.backgroundColor &&
        retainInputWhenMasked == p.retainInputWhenMasked &&
        blurTileMode == p.blurTileMode &&
        (colorEffects === p.colorEffects || colorEffects == p.colorEffects) &&
        (mask === p.mask || mask == p.mask) &&
        (progressiveMask === p.progressiveMask || progressiveMask == p.progressiveMask) &&
        (progressive === p.progressive || progressive == p.progressive)
    }
  }

  fun store(
    generation: Int,
    densityScale: Float,
    params: RenderEffectParams,
    effect: PlatformRenderEffect,
  ) {
    this.generation = generation
    this.densityScale = densityScale
    this.params = params
    platformEffect = effect
  }

  /** Wraps [platform] for a layer, reusing the previous wrapper while [platform] is unchanged. */
  fun composeEffect(platform: PlatformRenderEffect?): RenderEffect? {
    if (platform == null) return null
    if (platform !== wrappedPlatformEffect) {
      wrappedEffect = wrap(platform)
      wrappedPlatformEffect = platform
    }
    return wrappedEffect
  }

  /**
   * Gives [layer] the wrapper for [platform], assigning only when [layer] or the wrapper differs
   * from the last [apply]. Every write to a layer this slot owns must go through here.
   */
  fun apply(layer: GraphicsLayer, platform: PlatformRenderEffect?) {
    val effect = composeEffect(platform)
    if (layer !== appliedLayer || effect !== appliedEffect) {
      assign(layer, effect)
      appliedLayer = layer
      appliedEffect = effect
    }
  }

  /** Drops the remembered effect, its wrapper and the layer, which release their native memory. */
  fun reset() {
    params = null
    platformEffect = null
    wrappedPlatformEffect = null
    wrappedEffect = null
    appliedLayer = null
    appliedEffect = null
  }

  protected open fun wrap(platform: PlatformRenderEffect): RenderEffect = platform.asComposeRenderEffect()

  protected open fun assign(layer: GraphicsLayer, effect: RenderEffect?) {
    layer.renderEffect = effect
  }
}
