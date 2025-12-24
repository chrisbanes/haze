// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ColorFilter

/**
 * Converts a Compose [ColorFilter] to an Android platform [PlatformColorFilter].
 * 
 * Uses reflection to access the internal nativeColorFilter property since
 * Compose doesn't provide a public API for this conversion.
 */
@RequiresApi(Build.VERSION_CODES.Q)
@InternalHazeApi
public actual fun ColorFilter.toPlatformColorFilter(): PlatformColorFilter {
  return try {
    // Try to access nativeColorFilter via reflection
    val nativeField = ColorFilter::class.java.getDeclaredField("nativeColorFilter")
    nativeField.isAccessible = true
    nativeField.get(this) as PlatformColorFilter
  } catch (e: NoSuchFieldException) {
    throw IllegalStateException("Unable to convert ColorFilter: nativeColorFilter field not found", e)
  } catch (e: IllegalAccessException) {
    throw IllegalStateException("Unable to convert ColorFilter: cannot access nativeColorFilter", e)
  } catch (e: ClassCastException) {
    throw IllegalStateException("Unable to convert ColorFilter: unexpected type", e)
  }
}
