// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Paint

internal val PaintPool: Pool<Paint> = Pool(3)

internal fun Pool<Paint>.getOrCreate(): Paint = get() ?: Paint()

internal expect fun Paint.reset()

internal inline fun Pool<Paint>.usePaint(block: (Paint) -> Unit) {
  val paint = getOrCreate()
  try {
    block(paint)
  } finally {
    paint.reset()
    release(paint)
  }
}
