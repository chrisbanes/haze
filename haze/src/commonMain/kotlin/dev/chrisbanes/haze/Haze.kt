// Copyright 2023, Christopher Banes and the project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

expect fun Modifier.haze(
  areas: List<Rect>,
  color: Color,
  blurRadius: Float = 56f,
): Modifier
