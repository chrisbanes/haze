// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.content.Context
import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.unit.Density
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.PlatformContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope

/** Drives a Blur renderer or delegate in a host test without a Compose node. */
internal class HostLifecycleScope(private val context: Context) : HazeEffectLifecycleScope {
  var invalidateDrawCalls = 0
    private set

  override val modifierSize: Size = Size.Zero
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun requirePlatformContext(): PlatformContext = context
  override fun requireDensity(): Density = Density(1f)
  override fun <T> currentValueOf(local: CompositionLocal<T>): T {
    @Suppress("UNCHECKED_CAST")
    return HazeBlurStyle as T
  }
  override fun requireGraphicsContext(): GraphicsContext = error("Unused in host lifecycle tests")

  override fun invalidateDraw() {
    invalidateDrawCalls++
  }

  override fun invalidateLayerBounds() = Unit
}
