// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

internal actual fun HazeSourceNode.clearHazeAreaLayerOnStop() = Unit

internal actual fun shouldRefreshHazeSourceLayerOnPlacement(): Boolean = false

// Unmeasured on Skia platforms, so Auto behaves like Never until it is.
internal actual fun isAutoInputSharingSupported(): Boolean = false
