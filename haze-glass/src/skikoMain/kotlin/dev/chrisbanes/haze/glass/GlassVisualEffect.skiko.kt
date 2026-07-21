// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.VisualEffectContext

@OptIn(ExperimentalHazeApi::class)
internal actual fun GlassVisualEffect.updateDelegate(
  context: VisualEffectContext,
  drawScope: DrawScope,
): GlassVisualEffect.Delegate {
  val wantsRuntime =
    preparedRenderBudget is GlassRenderBudgetDecision.Runtime &&
      preparedRender != null
  return when {
    wantsRuntime && delegate !is RuntimeShaderGlassDelegate -> RuntimeShaderGlassDelegate(this)
    !wantsRuntime && delegate !is FallbackGlassDelegate -> FallbackGlassDelegate(this)
    else -> delegate
  }
}

internal actual fun isRuntimeShaderGlassSupported(): Boolean = true
