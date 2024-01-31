// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Path
import androidx.core.util.Pools

internal inline fun <T> Pools.Pool<T>.acquireOrCreate(block: () -> T): T = acquire() ?: block()

internal fun Pools.Pool<Path>.acquireOrCreate(): Path = acquireOrCreate(::Path)

internal fun Pools.Pool<Path>.releasePath(path: Path) {
  path.rewind()
  release(path)
}

internal inline fun Pools.Pool<Path>.usePath(block: (Path) -> Unit) {
  val path = acquireOrCreate()
  try {
    block(path)
  } finally {
    releasePath(path)
  }
}
