// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import android.os.Build
import androidx.annotation.RequiresApi
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformRenderEffect

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@InternalHazeApi
internal actual fun createGlassBlurRenderEffects(
  key: GlassBlurEffectKey,
): GlassBlurRenderEffects? = createSharedGlassBlurRenderEffects(key)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createGlassOpticalRenderEffect(
  key: GlassOpticalEffectKey,
): PlatformRenderEffect = createSharedGlassOpticalRenderEffect(key)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createRefractionDetailRenderEffect(
  key: GlassRefractionDetailEffectKey,
): PlatformRenderEffect = createSharedRefractionDetailRenderEffect(key)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createGlassRimRenderEffect(
  key: GlassRimEffectKey,
): PlatformRenderEffect? = createSharedGlassRimRenderEffect(key)
