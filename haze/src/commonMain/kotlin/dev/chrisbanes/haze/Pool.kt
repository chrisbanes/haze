// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Path

internal inline fun <T : Any> Pool<T>.acquireOrCreate(block: () -> T): T = acquire() ?: block()

internal class Pool<T>(
  private val onAcquire: (T) -> Unit = {},
  private val onRelease: (T) -> Unit = {},
) {
  private val items = mutableSetOf<T>()

  fun pooled(): Set<T> = items.toSet()

  fun acquire(): T? {
    return items.firstOrNull()
      ?.also(onAcquire)
      ?.also(items::remove)
  }

  fun release(item: T) {
    onRelease(item)
    items.add(item)
  }

  fun destroy() {
    items.clear()
  }
}

internal inline fun <T : Any> Pool<T>.use(
  factory: () -> T,
  block: (T) -> Unit,
) {
  val item = acquireOrCreate(factory)
  try {
    block(item)
  } finally {
    release(item)
  }
}

internal inline fun Pool<Path>.usePath(block: (Path) -> Unit) = use(::Path, block)

/**
 * A simple object path for [Path]s. They're fairly expensive so it makes sense to
 * re-use instances.
 */
internal val pathPool by lazy { Pool(Path::rewind) }
