// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.drawscope.DrawScope

internal actual fun HazeSourceNode.clearHazeAreaLayerOnStop() = Unit

internal actual fun HazeEffectNode.selectBlurEffect(drawScope: DrawScope): BlurEffect = when {
  blurEnabled -> RenderEffectBlurEffect
  else -> ScrimBlurEffect
}
