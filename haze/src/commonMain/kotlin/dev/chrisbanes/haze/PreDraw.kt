// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import kotlinx.coroutines.DisposableHandle

internal fun interface OnPreDrawListener {
  operator fun invoke()
}

internal expect fun CompositionLocalConsumerModifierNode.doOnPreDraw(
  listener: OnPreDrawListener,
): DisposableHandle
