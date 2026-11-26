// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.asBrush

@OptIn(InternalHazeApi::class)
internal fun HazeProgressive.toShader(size: Size): Shader {
  return when (val brush = asBrush()) {
    is ShaderBrush -> brush.createShader(size)
    is SolidColor -> LinearGradientShader(Offset.Zero, Offset(size.width, 0f), listOf(brush.value, brush.value))
  }
}
