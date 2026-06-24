// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import kotlinx.coroutines.DisposableHandle

// TODO: Move to iosMain and implement memory-pressure observers.
// iOS: UIApplicationDidReceiveMemoryWarningNotification

internal actual fun registerTrimMemoryCallback(
  context: PlatformContext,
  callback: (TrimMemoryLevel) -> Unit,
): DisposableHandle = DisposableHandle {}
