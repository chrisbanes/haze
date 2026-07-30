// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.DisposableHandle

internal actual fun registerTrimMemoryCallback(
  context: PlatformContext,
  lifecycle: Lifecycle?,
  callback: (TrimMemoryLevel) -> Unit,
): DisposableHandle = registerLifecycleTrimMemoryCallback(lifecycle, callback)
