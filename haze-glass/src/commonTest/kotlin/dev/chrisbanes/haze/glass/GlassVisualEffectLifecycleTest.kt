// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.VisualEffectContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
class GlassVisualEffectLifecycleTest {

  @Test
  fun update_directShapeChangeInvalidatesLayerBounds() {
    val effect = GlassVisualEffect()
    val context = TrackingVisualEffectContext()

    effect.update(context)
    context.invalidateLayerBoundsCalls = 0

    effect.shape = RoundedCornerShape(24.dp)
    effect.update(context)

    assertThat(context.invalidateLayerBoundsCalls).isEqualTo(1)
  }

  @Test
  fun update_adaptiveToAbsoluteInvalidatesDrawAndLayerBounds() {
    val effect = GlassVisualEffect()
    val context = TrackingVisualEffectContext()

    effect.optics = GlassOptics.Absolute(refractionStrength = 0.4f)
    effect.update(context)

    assertThat(context.invalidateDrawCalls).isEqualTo(1)
    assertThat(context.invalidateLayerBoundsCalls).isEqualTo(1)
  }

  @Test
  fun update_replacingAbsoluteInvalidatesDrawAndLayerBounds() {
    val effect = GlassVisualEffect().apply {
      optics = GlassOptics.Absolute(refractionStrength = 0.4f)
      resetDirtyTracker()
    }
    val context = TrackingVisualEffectContext()

    effect.optics = (effect.optics as GlassOptics.Absolute).copy(refractionStrength = 0.8f)
    effect.update(context)

    assertThat(context.invalidateDrawCalls).isEqualTo(1)
    assertThat(context.invalidateLayerBoundsCalls).isEqualTo(1)
  }

  @Test
  fun update_clearingAbsoluteOverrideInvalidatesDrawAndLayerBounds() {
    val effect = GlassVisualEffect().apply {
      optics = GlassOptics.Absolute(refractionStrength = 0.4f)
      resetDirtyTracker()
    }
    val context = TrackingVisualEffectContext()

    effect.clearOpticsOverride()
    effect.update(context)

    assertThat(context.invalidateDrawCalls).isEqualTo(1)
    assertThat(context.invalidateLayerBoundsCalls).isEqualTo(1)
  }
}

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
private class TrackingVisualEffectContext : VisualEffectContext {
  override val position: Offset = Offset.Zero
  override val size: Size = Size(100f, 100f)
  override val layerSize: Size = size
  override val layerOffset: Offset = Offset.Zero
  override val rootBounds: Rect = Rect(Offset.Zero, size)
  override val inputScale: HazeInputScale = HazeInputScale.None
  override val windowId: Any? = null
  override val areas: List<HazeArea> = emptyList()
  override val state: HazeState? = null
  override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

  var invalidateLayerBoundsCalls: Int = 0
  var invalidateDrawCalls: Int = 0

  override fun requirePlatformContext(): PlatformContext = error("Unused in lifecycle test")

  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = GlassDefaults.style as T

  override fun requireGraphicsContext(): GraphicsContext = error("Unused in lifecycle test")

  override fun invalidateDraw() {
    invalidateDrawCalls++
  }

  override fun invalidateLayerBounds() {
    invalidateLayerBoundsCalls++
  }
}
