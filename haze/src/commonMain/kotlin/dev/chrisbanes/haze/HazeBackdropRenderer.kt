// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas

/**
 * Internal platform boundary for a renderer which samples the already-drawn window backdrop.
 *
 * This is intentionally not part of the public Haze input or renderer API. The Android
 * implementation is experimental until the compositor behavior is proven on API 37.2.
 */
@InternalHazeApi
internal interface HazeBackdropRenderer {
  fun isSupported(canvas: Canvas): Boolean

  fun configure(
    bounds: Rect,
    clip: Rect?,
    effect: PlatformRenderEffect,
    alpha: Float = 1f,
  ): Boolean

  fun draw(canvas: Canvas): Boolean

  fun release()
}

@InternalHazeApi
internal expect fun createHazeBackdropRenderer(): HazeBackdropRenderer?

// Platform-neutral equivalent of Build.VERSION_CODES_FULL.CINNAMON_BUN_2 for common tests.
@InternalHazeApi
internal const val HAZE_BACKDROP_MIN_FULL_SDK = 3_700_002

// The available 37.2 beta 3 image is based on 37.1 and identifies its exact preview revision
// separately. Android requires prerelease API checks to match PREVIEW_SDK_INT exactly.
@InternalHazeApi
internal const val HAZE_BACKDROP_PREVIEW_BASE_FULL_SDK = 3_700_001

@InternalHazeApi
internal const val HAZE_BACKDROP_37_2_BETA_3_PREVIEW_SDK = 3_723

@InternalHazeApi
internal fun isHazeBackdropSdkSupported(
  fullSdkInt: Int,
  previewSdkInt: Int,
): Boolean = fullSdkInt >= HAZE_BACKDROP_MIN_FULL_SDK ||
  (
    fullSdkInt == HAZE_BACKDROP_PREVIEW_BASE_FULL_SDK &&
      previewSdkInt == HAZE_BACKDROP_37_2_BETA_3_PREVIEW_SDK
    )
