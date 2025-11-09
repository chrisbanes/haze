// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Build

/**
 * We need to manually invalidate if the HazeSourceNode 'draws' on certain API levels:
 *
 * - API 31: Ideally this wouldn't be necessary, but its been seen that API 31 has a few issues
 *   with RenderNodes not automatically re-painting. We workaround it by manually invalidating.
 * - Anything below API 31 does not have RenderEffect so we need to force invalidations.
 */
internal actual fun invalidateOnHazeAreaPreDraw(): Boolean = Build.VERSION.SDK_INT < 32
