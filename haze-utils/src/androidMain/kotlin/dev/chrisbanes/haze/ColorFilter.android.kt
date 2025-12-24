// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidColorFilter

/**
 * Converts a Compose [ColorFilter] to an Android platform [PlatformColorFilter].
 */
@RequiresApi(Build.VERSION_CODES.Q)
@InternalHazeApi
public actual fun ColorFilter.toPlatformColorFilter(): PlatformColorFilter {
  return asAndroidColorFilter()
}
