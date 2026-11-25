// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.TrimMemoryLevel

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal class ScrimBlurVisualEffectDelegate(
  val blurVisualEffect: BlurVisualEffect,
) : BlurVisualEffect.Delegate {
  // The masked tint layer, kept across draws; released when a draw stops using it, on detach and on trim.
  private val tintLayer = RetainedGraphicsLayer()

  override val drawsContentBehind: Boolean get() = true

  override fun DrawScope.draw(context: HazeEffectRuntimeDrawScope) {
    val scrimTint = blurVisualEffect.fallbackTint
      ?: blurVisualEffect.colorEffects?.firstOrNull()
        ?.boostForFallback(blurVisualEffect.blurRadius.takeOrElse { 0.dp })
      ?: return releaseLayers()

    drawScrim(
      colorEffect = scrimTint,
      context = context,
      mask = blurVisualEffect.mask,
      progressiveMask = blurVisualEffect.progressiveBrush,
      maskedLayer = tintLayer,
      alpha = blurVisualEffect.alpha,
    )
  }

  override fun detach() = releaseLayers()

  override fun clearRetainedOutput() = releaseLayers()

  // The layer is re-recorded at the current size on every draw, so a size change must not
  // release it (the default would forward to [clearRetainedOutput]).
  override fun invalidateRetainedOutput() = Unit

  override fun onTrimMemory(context: HazeEffectLifecycleScope, level: TrimMemoryLevel) {
    if (level.severity >= TrimMemoryLevel.MODERATE.severity) releaseLayers()
  }

  private fun releaseLayers() = tintLayer.release()
}
