// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

/**
 * An extremely simple LruCache that wraps a mutable map underneath. It is not thread-safe.
 */
internal class SimpleLruCache<K, V>(private val limit: Int) {

  private val map = mutableMapOf<K, CacheEntry<V>>()

  operator fun get(key: K): V? = map[key]?.also { it.updateAccessTime() }?.value

  operator fun set(key: K, value: V) {
    map[key] = CacheEntry(value)

    // Now remove the oldest items until we're below our limit again
    while (map.size > limit) {
      map.minByOrNull { it.value.lastAccessTime }?.also { map.remove(it.key) }
    }
  }

  fun clear() {
    map.clear()
  }
}

private class CacheEntry<V>(val value: V) {
  var lastAccessTime: Long = epochTimeMillis()

  fun updateAccessTime() {
    lastAccessTime = epochTimeMillis()
  }
}
