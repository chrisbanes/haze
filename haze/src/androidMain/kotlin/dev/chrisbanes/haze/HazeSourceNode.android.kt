// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch

internal actual fun HazeSourceNode.clearHazeAreaLayerOnStop() {
  // TODO: Move to LocalActivity in Compose 1.8.0
  val activity = currentValueOf(LocalContext).findActivityOrNull() ?: return
  coroutineScope.launch {
    activity.lifecycle.currentStateFlow.collect { state ->
      if (state <= Lifecycle.State.CREATED) {
        // When the UI host is stopped, release the GraphicsLayer. Android seems to have a issue
        // tracking layers re-paints after an Activity stop + start. Clearing the layer once
        // we're no longer visible fixes it 🤷: https://github.com/chrisbanes/haze/issues/497
        area.releaseLayer()
      }
    }
  }
}

private tailrec fun Context.findActivityOrNull(): ComponentActivity? = when (this) {
  is ComponentActivity -> this
  is ContextWrapper -> baseContext.findActivityOrNull()
  else -> null
}
