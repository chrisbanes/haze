// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier

internal fun Modifier.testHazeEffect(
  state: HazeState,
  selection: HazeSourceSelection = HazeSourceSelection.Behind,
  retention: HazeSourceRetention = HazeSourceRetention.KeepLastFrame,
): Modifier = hazeEffect(
  factory = TestPassthroughEffectFactory,
  input = HazeInput.Sources(
    state = state,
    selection = selection,
    retention = retention,
  ),
  style = Unit,
)

private data object TestPassthroughEffectFactory : HazeEffectFactory<Unit> {
  override fun createRenderer(): HazeEffectRenderer<Unit> = TestPassthroughEffectRenderer()
}

private class TestPassthroughEffectRenderer : HazeEffectRenderer<Unit> {
  override fun HazeEffectDrawScope.draw(style: Unit) {
    drawInput()
  }
}
