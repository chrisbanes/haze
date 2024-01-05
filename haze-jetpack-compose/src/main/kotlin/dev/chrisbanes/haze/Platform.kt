// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

internal fun createHazeNode(
  state: HazeState,
  backgroundColor: Color,
  tint: Color,
  blurRadius: Dp,
  noiseFactor: Float,
): HazeNode = when {
  Build.VERSION.SDK_INT >= 32 -> {
    // We can't currently use this impl on API 31 due to
    // https://github.com/chrisbanes/haze/issues/77
    HazeNodeRenderEffect(
      state = state,
      backgroundColor = backgroundColor,
      tint = tint,
      blurRadius = blurRadius,
      noiseFactor = noiseFactor,
    )
  }

  else -> {
    HazeNodeBase(
      state = state,
      backgroundColor = backgroundColor,
      tint = tint,
      blurRadius = blurRadius,
      noiseFactor = noiseFactor,
    )
  }
}
