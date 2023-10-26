// Copyright 2023, Christopher Banes and the project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

inline fun Modifier.haze(
  vararg area: Rect,
  color: Color,
  blurRadius: Float = 56f,
): Modifier = haze(
  areas = area.toList(),
  color = color,
  blurRadius = blurRadius,
)
