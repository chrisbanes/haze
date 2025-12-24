// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.chrisbanes.haze

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ColorFilter

/**
 * Converts a Compose [ColorFilter] to an Android platform [PlatformColorFilter].
 */
@RequiresApi(Build.VERSION_CODES.Q)
@InternalHazeApi
public fun ColorFilter.toAndroidColorFilter(): PlatformColorFilter {
  // Access the internal nativeColorFilter property
  // Compose's ColorFilter has a nativeColorFilter property that holds the platform ColorFilter
  return (this as androidx.compose.ui.graphics.AndroidColorFilter).nativeColorFilter
}
