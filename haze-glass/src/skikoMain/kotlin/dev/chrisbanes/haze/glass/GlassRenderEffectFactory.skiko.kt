// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.MutableRuntimeShaderRenderEffect

@InternalHazeApi
internal actual fun createGlassBlurRenderEffect(
  horizontal: Boolean,
  progressive: Boolean,
): MutableRuntimeShaderRenderEffect = createSharedGlassBlurRenderEffect(horizontal, progressive)

internal actual fun createGlassBlurPrefilterRenderEffect(): MutableRuntimeShaderRenderEffect =
  createSharedGlassBlurPrefilterRenderEffect()

internal actual fun createGlassOpticalRenderEffect(): MutableRuntimeShaderRenderEffect =
  createSharedGlassOpticalRenderEffect()

internal actual fun createRefractionDetailRenderEffect(): MutableRuntimeShaderRenderEffect =
  createSharedRefractionDetailRenderEffect()

internal actual fun createGlassRimRenderEffect(): MutableRuntimeShaderRenderEffect =
  createSharedGlassRimRenderEffect()
