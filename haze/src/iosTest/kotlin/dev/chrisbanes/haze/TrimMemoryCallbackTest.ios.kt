// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSThread
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification

class TrimMemoryCallbackTest {
  @Test
  fun memoryWarning_deliversCompleteUntilDisposed() {
    assertTrue(NSThread.isMainThread)
    val received = mutableListOf<TrimMemoryLevel>()
    val handle = registerTrimMemoryCallback(
      context = PlatformContext.INSTANCE,
      callback = received::add,
    )

    NSNotificationCenter.defaultCenter.postNotificationName(
      UIApplicationDidReceiveMemoryWarningNotification,
      null,
    )
    assertEquals(listOf(TrimMemoryLevel.COMPLETE), received)

    handle.dispose()
    NSNotificationCenter.defaultCenter.postNotificationName(
      UIApplicationDidReceiveMemoryWarningNotification,
      null,
    )
    assertEquals(listOf(TrimMemoryLevel.COMPLETE), received)
  }
}
