// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.DisposableHandle

internal expect fun registerTrimMemoryCallback(
  context: PlatformContext,
  lifecycle: Lifecycle?,
  callback: (TrimMemoryLevel) -> Unit,
): DisposableHandle

internal fun registerLifecycleTrimMemoryCallback(
  lifecycle: Lifecycle?,
  callback: (TrimMemoryLevel) -> Unit,
): DisposableHandle {
  if (lifecycle == null) return DisposableHandle {}

  val observer = LifecycleEventObserver { _, event ->
    if (event == Lifecycle.Event.ON_STOP) {
      callback(TrimMemoryLevel.MODERATE)
    }
  }
  lifecycle.addObserver(observer)
  return DisposableHandle { lifecycle.removeObserver(observer) }
}
