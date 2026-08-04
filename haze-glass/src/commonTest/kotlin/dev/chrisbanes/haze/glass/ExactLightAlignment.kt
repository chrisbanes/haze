// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

internal fun exactLightAlignment(position: Offset): Alignment = ExactLightAlignment(position)

private class ExactLightAlignment(
  private val position: Offset,
) : Alignment {
  override fun align(
    size: IntSize,
    space: IntSize,
    layoutDirection: LayoutDirection,
  ): IntOffset = IntOffset(position.x.roundToInt(), position.y.roundToInt())
}
