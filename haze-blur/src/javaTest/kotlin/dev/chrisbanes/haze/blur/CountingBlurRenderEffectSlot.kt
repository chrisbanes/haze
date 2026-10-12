// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

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
import dev.chrisbanes.haze.PlatformRenderEffect

/** Counts how often a draw consults, wraps for and assigns to a layer through a slot. */
internal class CountingBlurRenderEffectSlot : BlurRenderEffectSlot() {
  var lookups = 0
    private set
  var wraps = 0
    private set
  var assigns = 0
    private set

  override fun lookup(
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
    lookups++
    return super.lookup(
      generation, densityScale, blurRadius, noiseFactor, scale, colorEffects,
      colorEffectsAlphaModulate, backgroundColor, contentSize, contentOffset, mask,
      progressiveMask, retainInputWhenMasked, progressive, blurTileMode,
    )
  }

  override fun wrap(platform: PlatformRenderEffect): RenderEffect {
    wraps++
    return super.wrap(platform)
  }

  override fun assign(layer: GraphicsLayer, effect: RenderEffect?) {
    assigns++
    super.assign(layer, effect)
  }
}
