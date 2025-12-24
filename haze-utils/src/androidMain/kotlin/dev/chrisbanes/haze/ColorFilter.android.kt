// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidColorFilter

/**
 * Converts a Compose [ColorFilter] to an Android platform [PlatformColorFilter].
 */
@InternalHazeApi
public actual fun ColorFilter.toPlatformColorFilter(): PlatformColorFilter = asAndroidColorFilter()
