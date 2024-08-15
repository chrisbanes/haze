// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.node.invalidateDraw

internal actual fun HazeEffectNode.observeInvalidationTick() {
  // Yes, this is very very gross. The HazeNode will update the contentLayer in it's draw
  // function. HazeNode and also updates `state.invalidateTick` as a way for HazeChild[ren] to
  // know when the contentLayer has been updated. All fine so far, but for us to draw with the
  // updated `contentLayer` we need to invalidate. Invalidating ourselves will trigger us to
  // draw, but it will also trigger `contentLayer` to invalidate, and here's an infinite
  // draw loop we trigger.
  //
  // This is a huge giant hack, but by skipping every other invalidation caused by a
  // `invalidationTick` change, we break the loop.
  val tick = state.invalidateTick
  if (tick != lastInvalidationTick && tick % 2 == 0) {
    invalidateDraw()
  }
  lastInvalidationTick = tick
}
