// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import kotlin.concurrent.getOrSet

internal actual fun createHazeNode(
  state: HazeState,
  backgroundColor: Color,
  tint: Color,
  blurRadius: Dp,
  noiseFactor: Float,
): HazeNode = HazeNodeBase(state, backgroundColor, tint, blurRadius, noiseFactor)

internal actual fun CompositionLocalConsumerModifierNode.calculateWindowOffset(): Offset {
  val view = currentValueOf(LocalView)
  val loc = tmpArray.getOrSet { IntArray(2) }
  view.getLocationOnScreen(loc)
  return Offset(loc[0].toFloat(), loc[1].toFloat())
}

private val tmpArray = ThreadLocal<IntArray>()
