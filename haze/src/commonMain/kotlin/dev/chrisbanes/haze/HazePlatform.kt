// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

internal expect fun Modifier.haze(
  areas: List<Rect>,
  backgroundColor: Color,
  tint: Color,
  blurRadius: Dp,
): Modifier
