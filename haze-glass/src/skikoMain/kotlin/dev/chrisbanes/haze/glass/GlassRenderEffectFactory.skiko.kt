// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformRenderEffect

@InternalHazeApi
internal actual fun createGlassBlurRenderEffects(
  key: GlassBlurEffectKey,
): GlassBlurRenderEffects? = createSharedGlassBlurRenderEffects(key)

internal actual fun createGlassOpticalRenderEffect(
  key: GlassOpticalEffectKey,
): PlatformRenderEffect = createSharedGlassOpticalRenderEffect(key)

internal actual fun createRefractionDetailRenderEffect(
  key: GlassRefractionDetailEffectKey,
): PlatformRenderEffect = createSharedRefractionDetailRenderEffect(key)

internal actual fun createGlassRimRenderEffect(
  key: GlassRimEffectKey,
): PlatformRenderEffect? = createSharedGlassRimRenderEffect(key)
