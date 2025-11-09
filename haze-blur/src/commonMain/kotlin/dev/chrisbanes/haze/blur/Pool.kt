// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.collection.mutableScatterSetOf

internal class Pool<T>(private val maxSize: Int) {
  private val pool = mutableScatterSetOf<T>()

  fun get(): T? = when {
    pool.isNotEmpty() -> pool.first().also(pool::remove)
    else -> null
  }

  fun release(instance: T) {
    if (pool.size < maxSize) {
      pool += instance
    }
  }
}
