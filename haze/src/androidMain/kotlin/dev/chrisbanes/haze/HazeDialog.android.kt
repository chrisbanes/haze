// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
actual fun HazeDialog(
  hazeState: HazeState,
  onDismissRequest: () -> Unit,
  properties: DialogProperties,
  content: @Composable () -> Unit,
) {
  ForceInvalidationEffect(hazeState)

  Dialog(
    onDismissRequest = onDismissRequest,
    properties = properties,
    content = content,
  )
}

@Composable
private fun ForceInvalidationEffect(hazeState: HazeState) {
  val view = LocalView.current

  DisposableEffect(view) {
    val vto = view.viewTreeObserver

    val listener = ViewTreeObserver.OnPreDrawListener {
      for (area in hazeState.areas) {
        // Force the `hazeEffect` attached to each area to invalidate. This allows any
        // `hazeEffect`s used in the Dialog below, to update whenever 'this' view is drawn.
        // We're basically tying the invalidation scopes together.
        // It's a little heavy handed, but works.
        area.forcedInvalidationTick++
      }
      true
    }

    vto.addOnPreDrawListener(listener)

    onDispose {
      (vto.takeIf { it.isAlive } ?: view.viewTreeObserver)
        .removeOnPreDrawListener(listener)
    }
  }
}
