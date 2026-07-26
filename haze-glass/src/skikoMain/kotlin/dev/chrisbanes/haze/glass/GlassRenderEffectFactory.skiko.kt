// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.MutableRuntimeShaderRenderEffect
import dev.chrisbanes.haze.PlatformRenderEffect

@InternalHazeApi
internal actual fun createGlassBlurRenderEffect(
  horizontal: Boolean,
  progressive: Boolean,
): MutableRuntimeShaderRenderEffect = createRetainedGlassBlurRenderEffect(horizontal, progressive)

internal actual fun createGlassBlurPrefilterRenderEffect(): MutableRuntimeShaderRenderEffect =
  createRetainedGlassBlurPrefilterRenderEffect()

internal actual fun createGlassOpticalRenderEffect(): MutableRuntimeShaderRenderEffect =
  createRetainedGlassOpticalRenderEffect()

internal actual fun createRefractionDetailRenderEffect(): MutableRuntimeShaderRenderEffect =
  createRetainedRefractionDetailRenderEffect()

internal actual fun createGlassRimRenderEffect(): MutableRuntimeShaderRenderEffect =
  createRetainedGlassRimRenderEffect()

internal actual fun createGlassDepthInputRenderEffect(
  blur: PlatformRenderEffect?,
  depth: Float,
): PlatformRenderEffect? = null

internal actual val supportsFusedGlassRenderEffect: Boolean = false
