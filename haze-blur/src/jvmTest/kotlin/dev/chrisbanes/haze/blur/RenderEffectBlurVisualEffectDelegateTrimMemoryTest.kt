// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.unit.Density
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.TrimMemoryLevel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

class RenderEffectBlurVisualEffectDelegateTrimMemoryTest {

  @Test
  fun renderEffectCache_isOwnedByEachBlurRuntime() {
    val first = HazeBlurFactory.createRenderer() as BlurVisualEffect
    val second = HazeBlurFactory.createRenderer() as BlurVisualEffect

    assertThat(first.renderEffectCache).isNotSameInstanceAs(second.renderEffectCache)
  }

  @Test
  fun onTrimMemory_backgroundKeepsRetainedOutputAvailability() {
    val delegate = RenderEffectBlurVisualEffectDelegate(
      HazeBlurFactory.createRenderer() as BlurVisualEffect,
    )
    delegate.setPrivateField("retainedOutputAvailable", true)
    val context = RecordingLifecycleScope()

    delegate.onTrimMemory(context, TrimMemoryLevel.BACKGROUND)

    assertThat(delegate.getPrivateField<Boolean>("retainedOutputAvailable")).isTrue()
    assertThat(context.invalidateDrawCalls).isEqualTo(0)
  }

  @Test
  fun onTrimMemory_moderateClearsRetainedOutputAvailabilityAndInvalidatesDraw() {
    val delegate = RenderEffectBlurVisualEffectDelegate(
      HazeBlurFactory.createRenderer() as BlurVisualEffect,
    )
    delegate.setPrivateField("retainedOutputAvailable", true)
    delegate.setPrivateField("lastScaledLayerSize", Size(10f, 10f))
    val context = RecordingLifecycleScope()

    delegate.onTrimMemory(context, TrimMemoryLevel.MODERATE)

    assertThat(delegate.getPrivateField<Boolean>("retainedOutputAvailable")).isFalse()
    assertThat(delegate.getPrivateField<Size?>("lastScaledLayerSize")).isEqualTo(null)
    assertThat(context.invalidateDrawCalls).isEqualTo(1)
  }

  @Test
  fun clearRetainedOutput_releasesLayerMetadataAndAvailability() {
    val delegate = RenderEffectBlurVisualEffectDelegate(
      HazeBlurFactory.createRenderer() as BlurVisualEffect,
    )
    delegate.setPrivateField("retainedOutputAvailable", true)
    delegate.setPrivateField("lastScaledLayerSize", Size(10f, 10f))

    delegate.clearRetainedOutput()

    assertThat(delegate.getPrivateField<Boolean>("retainedOutputAvailable")).isFalse()
    assertThat(delegate.getPrivateField<Size?>("lastScaledLayerSize")).isEqualTo(null)
    assertThat(delegate.getPrivateField<Any?>("scaledContentLayer")).isEqualTo(null)
    assertThat(delegate.getPrivateField<Any?>("graphicsContext")).isEqualTo(null)
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

private class RecordingLifecycleScope : HazeEffectLifecycleScope {
  var invalidateDrawCalls = 0
    private set

  override val modifierSize: Size = Size.Zero
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun requirePlatformContext(): PlatformContext = error("Unused in trim-memory tests")
  override fun requireDensity(): Density = Density(1f)
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = error("Unused in trim-memory tests")
  override fun requireGraphicsContext(): GraphicsContext = error("Unused in trim-memory tests")

  override fun invalidateDraw() {
    invalidateDrawCalls++
  }

  override fun invalidateLayerBounds() = Unit
}
