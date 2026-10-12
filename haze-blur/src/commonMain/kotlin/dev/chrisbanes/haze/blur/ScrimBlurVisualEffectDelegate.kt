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
  // Layers kept across draws; each is released when a draw stops using it, on detach and on trim.
  private val alphaLayer = RetainedGraphicsLayer()
  private val tintLayer = RetainedGraphicsLayer()

  override val drawsContentBehind: Boolean get() = true

  override fun DrawScope.draw(context: HazeEffectRuntimeDrawScope) {
    val scrimTint = blurVisualEffect.fallbackTint
      ?: blurVisualEffect.colorEffects?.firstOrNull()
        ?.boostForFallback(blurVisualEffect.blurRadius.takeOrElse { 0.dp })
      ?: return releaseLayers()

    withAlpha(alpha = blurVisualEffect.alpha, retainedLayer = alphaLayer, context = context) {
      drawScrim(
        colorEffect = scrimTint,
        context = context,
        mask = blurVisualEffect.mask,
        progressiveMask = blurVisualEffect.progressiveBrush,
        maskedLayer = tintLayer,
      )
    }
  }

  override fun detach() = releaseLayers()

  override fun onTrimMemory(context: HazeEffectLifecycleScope, level: TrimMemoryLevel) {
    if (level.severity >= TrimMemoryLevel.MODERATE.severity) releaseLayers()
  }

  private fun releaseLayers() {
    alphaLayer.release()
    tintLayer.release()
  }
}
