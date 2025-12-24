// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.ColorFilter

/**
 * Converts a Compose [ColorFilter] to a Skia platform [PlatformColorFilter].
 */
@InternalHazeApi
public fun ColorFilter.toAndroidColorFilter(): PlatformColorFilter {
  // Access the internal nativeColorFilter property
  // Compose's ColorFilter has a nativeColorFilter property that holds the platform ColorFilter
  return (this as androidx.compose.ui.graphics.SkiaColorFilter).nativeColorFilter
}
