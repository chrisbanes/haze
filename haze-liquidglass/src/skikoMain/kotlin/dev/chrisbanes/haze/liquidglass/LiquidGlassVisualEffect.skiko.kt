// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.isRuntimeShaderRenderEffectSupported

@OptIn(ExperimentalHazeApi::class)
internal actual fun LiquidGlassVisualEffect.updateDelegate(
  context: VisualEffectContext,
  drawScope: DrawScope,
) {
  val wantsRuntime = isRuntimeShaderRenderEffectSupported()
  delegate = when {
    wantsRuntime && delegate !is RuntimeShaderLiquidGlassDelegate -> RuntimeShaderLiquidGlassDelegate(this)
    !wantsRuntime && delegate !is FallbackLiquidGlassDelegate -> FallbackLiquidGlassDelegate(this)
    else -> delegate
  }
}
