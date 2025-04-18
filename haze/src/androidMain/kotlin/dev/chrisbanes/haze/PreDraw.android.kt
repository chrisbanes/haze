// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.view.ViewTreeObserver
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.DisposableHandle

internal actual fun CompositionLocalConsumerModifierNode.doOnPreDraw(
  listener: OnPreDrawListener,
): DisposableHandle {
  val view = currentValueOf(LocalView)

  val preDrawListener = ViewTreeObserver.OnPreDrawListener {
    listener()
    true
  }

  val vto = view.viewTreeObserver
  vto.addOnPreDrawListener(preDrawListener)

  return DisposableHandle {
    val aliveVto = vto.takeIf { it.isAlive } ?: view.viewTreeObserver
    aliveVto.removeOnPreDrawListener(preDrawListener)
  }
}
