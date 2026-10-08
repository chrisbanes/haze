// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "ktlint:standard:argument-list-wrapping")

package dev.chrisbanes.haze

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Build
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.DisposableHandle

internal actual fun registerTrimMemoryCallback(
  context: PlatformContext,
  lifecycle: Lifecycle?,
  callback: (TrimMemoryLevel) -> Unit,
): DisposableHandle {
  val cb = object : ComponentCallbacks2 {
    override fun onTrimMemory(level: Int) {
      callback(level.toTrimMemoryLevel())
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit
    override fun onLowMemory() = callback(TrimMemoryLevel.COMPLETE)
  }

  context.registerComponentCallbacks(cb)
  return DisposableHandle { context.unregisterComponentCallbacks(cb) }
}

internal fun Int.toTrimMemoryLevel(sdkInt: Int = Build.VERSION.SDK_INT): TrimMemoryLevel = when {
  this >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> TrimMemoryLevel.COMPLETE
  this >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> TrimMemoryLevel.MODERATE
  // API 34+ stops delivering MODERATE and COMPLETE, so BACKGROUND is the strongest signal left.
  this >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND && sdkInt >= 34 -> TrimMemoryLevel.MODERATE
  this >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> TrimMemoryLevel.BACKGROUND
  this >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> TrimMemoryLevel.UI_HIDDEN
  this >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> TrimMemoryLevel.COMPLETE
  this >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> TrimMemoryLevel.MODERATE
  this >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> TrimMemoryLevel.BACKGROUND
  else -> TrimMemoryLevel.UI_HIDDEN
}
