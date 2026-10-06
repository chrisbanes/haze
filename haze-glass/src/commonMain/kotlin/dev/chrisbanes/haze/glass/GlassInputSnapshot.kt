// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer

/** Captures a recorded candidate; Android defers work beyond the active window draw. */
internal expect suspend fun GraphicsLayer.captureGlassInputSnapshot(): ImageBitmap
