// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze

import android.content.ComponentCallbacks
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.ContextWrapper
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isFalse
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrimMemoryCallbackAndroidHostTest {
  @Test
  fun registerTrimMemoryCallback_deliversMappedLevelsAndStopsAfterDisposal() {
    val context = RecordingContext()
    val received = mutableListOf<TrimMemoryLevel>()

    val handle = registerTrimMemoryCallback(
      context = context,
      lifecycle = null,
      callback = received::add,
    )

    assertThat(context.registered).isTrue()
    val callback = context.registeredCallback
    callback.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
    callback.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
    callback.onLowMemory()
    assertThat(received).containsExactly(
      TrimMemoryLevel.MODERATE,
      TrimMemoryLevel.UI_HIDDEN,
      TrimMemoryLevel.COMPLETE,
    )

    handle.dispose()

    assertThat(context.unregisteredCallback).isSameInstanceAs(callback)
    assertThat(context.registered).isFalse()
    context.dispatchTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    assertThat(received).containsExactly(
      TrimMemoryLevel.MODERATE,
      TrimMemoryLevel.UI_HIDDEN,
      TrimMemoryLevel.COMPLETE,
    )
  }
}

private class RecordingContext : ContextWrapper(null) {
  private var callback: ComponentCallbacks? = null
  var unregisteredCallback: ComponentCallbacks? = null
    private set

  val registered: Boolean get() = callback != null
  val registeredCallback: ComponentCallbacks2 get() = checkNotNull(callback as? ComponentCallbacks2)

  override fun registerComponentCallbacks(callback: ComponentCallbacks) {
    check(this.callback == null)
    this.callback = callback
  }

  override fun unregisterComponentCallbacks(callback: ComponentCallbacks) {
    check(this.callback === callback)
    this.callback = null
    unregisteredCallback = callback
  }

  fun dispatchTrimMemory(level: Int) {
    (callback as? ComponentCallbacks2)?.onTrimMemory(level)
  }

  override fun getApplicationContext(): Context = this
  override fun getResources() = throw UnsupportedOperationException()
  override fun getPackageName(): String = "dev.chrisbanes.haze.test"
}
