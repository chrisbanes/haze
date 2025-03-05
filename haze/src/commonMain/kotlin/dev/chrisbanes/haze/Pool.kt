// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

internal open class Pool<T>(private val maxSize: Int) {
  private val pool = mutableListOf<T>()

  fun get(): T? = if (pool.isNotEmpty()) pool.removeAt(0) else null

  fun release(instance: T) {
    pool.add(instance)
    maintainSize()
  }

  private fun maintainSize() {
    while (pool.size > maxSize) {
      pool.removeAt(0)
    }
  }
}
