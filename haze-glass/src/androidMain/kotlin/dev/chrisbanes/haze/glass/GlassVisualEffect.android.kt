// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.isRuntimeShaderRenderEffectSupported

internal actual fun isRuntimeShaderGlassSupported(): Boolean =
  isRuntimeShaderRenderEffectSupported()

internal actual fun DrawScope.isGlassHardwareCanvas(): Boolean =
  drawContext.canvas.nativeCanvas.isHardwareAccelerated
