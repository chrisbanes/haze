// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.ColorFilter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Converts a Compose [ColorFilter] to a Skia platform [PlatformColorFilter].
 * 
 * Uses reflection to access the internal nativeColorFilter property since
 * Compose doesn't provide a public API for this conversion.
 */
@InternalHazeApi
public actual fun ColorFilter.toPlatformColorFilter(): PlatformColorFilter {
  return try {
    // Use Kotlin reflection for better Kotlin/Native compatibility
    val nativeProperty = ColorFilter::class.memberProperties
      .firstOrNull { it.name == "nativeColorFilter" }
      ?: throw NoSuchFieldException("nativeColorFilter property not found")
    
    nativeProperty.isAccessible = true
    nativeProperty.get(this) as PlatformColorFilter
  } catch (e: NoSuchFieldException) {
    throw IllegalStateException("Unable to convert ColorFilter: nativeColorFilter property not found", e)
  } catch (e: IllegalAccessException) {
    throw IllegalStateException("Unable to convert ColorFilter: cannot access nativeColorFilter", e)
  } catch (e: ClassCastException) {
    throw IllegalStateException("Unable to convert ColorFilter: unexpected type", e)
  }
}
