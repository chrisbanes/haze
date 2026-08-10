// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.DisposableHandle

/**
 * AppKit has no equivalent of UIKit's `UIApplicationDidReceiveMemoryWarningNotification`, so macOS
 * follows the desktop targets and trims on lifecycle stop instead.
 */
internal actual fun registerTrimMemoryCallback(
  context: PlatformContext,
  lifecycle: Lifecycle?,
  callback: (TrimMemoryLevel) -> Unit,
): DisposableHandle = registerLifecycleTrimMemoryCallback(lifecycle, callback)
