// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.collection.LruCache
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class BlurVisualEffectUtilsTest {

  @Test
  fun clearIfInitialized_doesNotInitializeLazyValue() {
    var initialized = false
    val lazyCache = lazy(mode = LazyThreadSafetyMode.NONE) {
      initialized = true
      LruCache<Int, Int>(1)
    }

    clearIfInitialized(lazyCache) { it.evictAll() }

    assertThat(initialized).isFalse()
  }

  @Test
  fun clearIfInitialized_clearsInitializedValue() {
    val lazyCache = lazy(mode = LazyThreadSafetyMode.NONE) {
      LruCache<Int, Int>(2)
    }
    lazyCache.value.put(1, 1)

    var cleared = false
    clearIfInitialized(lazyCache) {
      cleared = true
      it.evictAll()
    }

    assertThat(cleared).isTrue()
    assertThat(lazyCache.value[1] == null).isTrue()
  }
}
