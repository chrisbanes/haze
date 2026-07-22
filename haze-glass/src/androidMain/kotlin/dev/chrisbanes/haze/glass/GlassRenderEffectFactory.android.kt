// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import android.os.Build
import androidx.annotation.RequiresApi
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.MutableRuntimeShaderRenderEffect

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@InternalHazeApi
internal actual fun createGlassBlurRenderEffect(
  horizontal: Boolean,
  progressive: Boolean,
): MutableRuntimeShaderRenderEffect = createSharedGlassBlurRenderEffect(horizontal, progressive)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createGlassBlurPrefilterRenderEffect(): MutableRuntimeShaderRenderEffect =
  createSharedGlassBlurPrefilterRenderEffect()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createGlassOpticalRenderEffect(): MutableRuntimeShaderRenderEffect =
  createSharedGlassOpticalRenderEffect()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createRefractionDetailRenderEffect(): MutableRuntimeShaderRenderEffect =
  createSharedRefractionDetailRenderEffect()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createGlassRimRenderEffect(): MutableRuntimeShaderRenderEffect =
  createSharedGlassRimRenderEffect()
