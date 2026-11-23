// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.graphics.Paint as FrameworkPaint
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativePaint

internal actual fun Paint.reset() {
  nativePaint.reset()
  // Compose caches shader, filter, path effect and blend mode, and the native reset drops the
  // flags Compose's Paint() sets, so restore both to match a fresh Paint().
  nativePaint.flags = nativePaint.flags or
    FrameworkPaint.ANTI_ALIAS_FLAG or FrameworkPaint.FILTER_BITMAP_FLAG or FrameworkPaint.DITHER_FLAG
  shader = null
  colorFilter = null
  pathEffect = null
  blendMode = BlendMode.SrcOver
}
