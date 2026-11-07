// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.chrisbanes.haze.PlatformRenderEffect

internal actual fun DrawScope.supportsFallbackGlassBlur(): Boolean = false
internal actual fun createFallbackGlassInputEffect(style: ResolvedGlassStyle, blurred: Boolean): PlatformRenderEffect? = null
