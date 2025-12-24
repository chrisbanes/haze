// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.ColorFilter

/**
 * Converts a Compose [ColorFilter] to a platform [PlatformColorFilter].
 *
 * This is a platform-specific conversion that accesses the internal
 * nativeColorFilter property.
 */
@InternalHazeApi
public expect fun ColorFilter.toPlatformColorFilter(): PlatformColorFilter
