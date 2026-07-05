// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.unit.Density
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.VisualEffectContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalHazeApi::class)
class RuntimeShaderLiquidGlassDelegateTrimMemoryTest {

  @Test
  fun onTrimMemory_backgroundKeepsRetainedOutputAvailability() {
    val delegate = RuntimeShaderLiquidGlassDelegate(LiquidGlassVisualEffect())
    delegate.setPrivateField("retainedOutputAvailable", true)
    val context = RecordingVisualEffectContext()

    delegate.onTrimMemory(context, TrimMemoryLevel.BACKGROUND)

    assertThat(delegate.getPrivateField<Boolean>("retainedOutputAvailable")).isTrue()
    assertThat(context.invalidateDrawCalls).isEqualTo(0)
  }

  @Test
  fun onTrimMemory_moderateClearsRetainedOutputAvailabilityAndInvalidatesDraw() {
    val delegate = RuntimeShaderLiquidGlassDelegate(LiquidGlassVisualEffect())
    delegate.setPrivateField("retainedOutputAvailable", true)
    delegate.setPrivateField("lastScaledLayerSize", Size(10f, 10f))
    val context = RecordingVisualEffectContext()

    delegate.onTrimMemory(context, TrimMemoryLevel.MODERATE)

    assertThat(delegate.getPrivateField<Boolean>("retainedOutputAvailable")).isFalse()
    assertThat(delegate.getPrivateField<Size?>("lastScaledLayerSize")).isEqualTo(null)
    assertThat(context.invalidateDrawCalls).isEqualTo(1)
  }
}

private fun Any.setPrivateField(name: String, value: Any?) {
  javaClass.getDeclaredField(name).apply {
    isAccessible = true
    set(this@setPrivateField, value)
  }
}

private inline fun <reified T> Any.getPrivateField(name: String): T {
  return javaClass.getDeclaredField(name).run {
    isAccessible = true
    get(this@getPrivateField) as T
  }
}

private class RecordingVisualEffectContext : VisualEffectContext {
  var invalidateDrawCalls = 0
    private set

  override val position: Offset = Offset.Zero
  override val size: Size = Size.Zero
  override val layerSize: Size = Size.Zero
  override val layerOffset: Offset = Offset.Zero
  override val rootBounds: Rect = Rect.Zero
  override val inputScale: HazeInputScale = HazeInputScale.None
  override val windowId: Any? = null
  override val areas: List<HazeArea> = emptyList()
  override val state: HazeState? = null
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun positionOf(area: HazeArea): Offset = area.coordinates.localPosition
  override fun boundsOf(area: HazeArea): Rect? {
    val position = area.coordinates.localPosition
    return if (position.isSpecified && area.size.isSpecified) Rect(position, area.size) else null
  }

  override fun requirePlatformContext(): PlatformContext = error("Unused in trim-memory tests")
  override fun requireDensity(): Density = Density(1f)
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = error("Unused in trim-memory tests")
  override fun requireGraphicsContext(): GraphicsContext = error("Unused in trim-memory tests")

  override fun invalidateDraw() {
    invalidateDrawCalls++
  }
}
