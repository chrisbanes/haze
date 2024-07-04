// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Path

internal inline fun <T : Any> MutableSet<T>.acquireOrCreate(block: () -> T): T =
  acquire() ?: block()

internal fun <T : Any> MutableSet<T>.acquire(): T? {
  val acquired = firstOrNull()
  if (acquired != null) {
    remove(acquired)
  }
  return acquired
}

internal fun MutableSet<Path>.acquireOrCreate(): Path = acquireOrCreate(::Path)

internal fun MutableSet<Path>.releasePath(path: Path) {
  path.rewind()
  add(path)
}

internal inline fun MutableSet<Path>.usePath(block: (Path) -> Unit) {
  val path = acquireOrCreate()
  try {
    block(path)
  } finally {
    releasePath(path)
  }
}
