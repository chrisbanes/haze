// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas

@InternalHazeApi
internal actual fun createHazeBackdropRenderer(): HazeBackdropRenderer =
  UnavailableHazeBackdropRenderer

private object UnavailableHazeBackdropRenderer : HazeBackdropRenderer {
  override fun isSupported(canvas: Canvas): Boolean = false

  override fun configure(
    bounds: Rect,
    clip: Rect?,
    effect: PlatformRenderEffect,
  ): Boolean = false

  override fun draw(canvas: Canvas): Boolean = false

  override fun release() = Unit
}
