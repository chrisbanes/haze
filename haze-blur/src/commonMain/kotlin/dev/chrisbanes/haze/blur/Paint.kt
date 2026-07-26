// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.Paint

private const val MAX_PAINT_POOL_SIZE = 3

internal val PaintPool: ArrayDeque<Paint> = ArrayDeque(MAX_PAINT_POOL_SIZE)

internal expect fun Paint.reset()

internal inline fun <R> ArrayDeque<Paint>.usePaint(block: (Paint) -> R): R {
  val paint = removeLastOrNull() ?: Paint()
  return try {
    block(paint)
  } finally {
    paint.reset()
    if (size < MAX_PAINT_POOL_SIZE) {
      addLast(paint)
    }
  }
}
