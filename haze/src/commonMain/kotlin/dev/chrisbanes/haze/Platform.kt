// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

internal expect fun createHazeNode(
  state: HazeState,
  style: HazeStyle,
): HazeNode

internal expect fun CompositionLocalConsumerModifierNode.calculateWindowOffset(): Offset
