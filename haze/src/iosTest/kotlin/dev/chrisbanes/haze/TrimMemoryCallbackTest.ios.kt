// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlin.test.Test
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSThread
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification

class TrimMemoryCallbackTest {
  @Test
  fun memoryWarning_deliversCompleteUntilDisposed() {
    assertThat(NSThread.isMainThread).isTrue()
    val received = mutableListOf<TrimMemoryLevel>()
    val handle = registerTrimMemoryCallback(
      context = PlatformContext.INSTANCE,
      callback = received::add,
    )

    NSNotificationCenter.defaultCenter.postNotificationName(
      UIApplicationDidReceiveMemoryWarningNotification,
      null,
    )
    assertThat(received).isEqualTo(listOf(TrimMemoryLevel.COMPLETE))

    handle.dispose()
    NSNotificationCenter.defaultCenter.postNotificationName(
      UIApplicationDidReceiveMemoryWarningNotification,
      null,
    )
    assertThat(received).isEqualTo(listOf(TrimMemoryLevel.COMPLETE))
  }
}
