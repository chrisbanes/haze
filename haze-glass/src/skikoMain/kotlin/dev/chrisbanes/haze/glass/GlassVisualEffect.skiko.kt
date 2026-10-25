// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.drawscope.DrawScope

internal actual fun isRuntimeShaderGlassSupported(): Boolean = true

internal actual fun DrawScope.isGlassHardwareCanvas(): Boolean = true
