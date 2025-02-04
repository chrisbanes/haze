// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.node.currentValueOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

internal actual fun HazeSourceNode.clearHazeAreaLayerOnStop() {
  val lifecycleOwner = currentValueOf(LocalLifecycleOwner)
  coroutineScope.launch {
    lifecycleOwner.lifecycle.currentStateFlow.collect { state ->
      if (state <= Lifecycle.State.CREATED) {
        // When the UI host is stopped, release the GraphicsLayer. Android seems to have a issue
        // tracking layers re-paints after an Activity stop + start. Clearing the layer once
        // we're no longer visible fixes it 🤷: https://github.com/chrisbanes/haze/issues/497
        area.releaseLayer()
      }
    }
  }
}
