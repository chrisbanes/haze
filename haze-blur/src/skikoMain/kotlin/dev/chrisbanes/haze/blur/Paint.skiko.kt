// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.skiaPaint

internal actual fun Paint.reset() {
  // Compose caches these in Kotlin, so reset them to a fresh Paint()'s values through its setters.
  blendMode = BlendMode.SrcOver
  style = PaintingStyle.Fill
  strokeCap = StrokeCap.Butt
  strokeJoin = StrokeJoin.Round
  strokeMiterLimit = 0f
  filterQuality = FilterQuality.Medium
  shader = null
  colorFilter = null
  pathEffect = null
  // Reset the native paint last: its defaults (Miter join, limit 4) differ from Compose's.
  skiaPaint.reset()
  // Skia's Paint() enables anti-aliasing, but its native reset() clears it.
  isAntiAlias = true
  // shortcut: the internal alpha multiplier has no public setter, so a paint reused under a
  // ModulateAlpha layer compounds alpha; reset it if Compose exposes it.
}
