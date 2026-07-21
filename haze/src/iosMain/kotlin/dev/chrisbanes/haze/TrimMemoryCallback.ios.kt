// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import kotlinx.coroutines.DisposableHandle
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification

internal actual fun registerTrimMemoryCallback(
  context: PlatformContext,
  callback: (TrimMemoryLevel) -> Unit,
): DisposableHandle {
  val center = NSNotificationCenter.defaultCenter
  val observer = center.addObserverForName(
    UIApplicationDidReceiveMemoryWarningNotification,
    null,
    NSOperationQueue.mainQueue,
  ) {
    callback(TrimMemoryLevel.COMPLETE)
  }
  return DisposableHandle { center.removeObserver(observer) }
}
