// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.TrimMemoryLevel
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

class BlurRendererLifecycleTest {

  @Test
  fun styleReplacement_reusesRendererAndReconfiguresResolvedStyle() {
    val renderer = HazeBlurFactory.createRenderer() as BlurVisualEffect
    renderer.update(
      BlurTestLifecycleScope,
      HazeBlurStyle { blurRadius(12.dp) },
      HazeSampling.Default,
    )

    val original = renderer
    renderer.update(
      BlurTestLifecycleScope,
      HazeBlurStyle { blurRadius(24.dp) },
      HazeSampling.FullResolution,
    )

    assertThat(renderer).isSameInstanceAs(original)
    assertThat(renderer.blurRadius).isEqualTo(24.dp)
  }

  @Test
  fun attachmentTrimAndDisposal_reachOnlyTheOwnedDelegate() {
    val renderer = HazeBlurFactory.createRenderer() as BlurVisualEffect
    val delegate = RecordingBlurDelegate()
    renderer.delegate = delegate

    renderer.attach(BlurTestLifecycleScope)
    renderer.onTrimMemory(TrimMemoryLevel.MODERATE)
    renderer.detach()
    renderer.dispose()

    assertThat(delegate.attachCalls).isEqualTo(1)
    assertThat(delegate.trimCalls).isEqualTo(1)
    assertThat(delegate.detachCalls).isEqualTo(1)
  }

  @Test
  fun retainedOutput_capabilityClearsDelegateState() {
    val renderer = HazeBlurFactory.createRenderer() as BlurVisualEffect
    val delegate = RecordingBlurDelegate(retained = true)
    renderer.delegate = delegate

    assertThat(renderer.canDrawRetainedOutput()).isTrue()
    assertThat(renderer.shouldDrawRetainedOutput()).isTrue()

    renderer.clearRetainedOutput()

    assertThat(renderer.canDrawRetainedOutput()).isFalse()
    assertThat(renderer.shouldDrawRetainedOutput()).isFalse()
  }
}

private class RecordingBlurDelegate(
  retained: Boolean = false,
) : BlurVisualEffect.Delegate, RetainedOutputDelegate {
  var attachCalls = 0
  var trimCalls = 0
  var detachCalls = 0
  private var retained = retained

  override fun attach() {
    attachCalls++
  }

  override fun DrawScope.draw(context: HazeEffectRuntimeDrawScope) = Unit

  override fun onTrimMemory(context: HazeEffectLifecycleScope, level: TrimMemoryLevel) {
    trimCalls++
  }

  override fun detach() {
    detachCalls++
  }

  override fun canDrawRetainedOutput(): Boolean = retained

  override fun clearRetainedOutput() {
    retained = false
  }
}

private data object BlurTestLifecycleScope : HazeEffectLifecycleScope {
  override val modifierSize: Size = Size.Zero
  override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)
  override fun requirePlatformContext(): PlatformContext = error("Unused")
  override fun requireGraphicsContext(): GraphicsContext = error("Unused")
  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T =
    if (local === LocalHazeBlurStyle) HazeBlurStyle as T else error("Unused local")

  override fun invalidateDraw() = Unit
  override fun invalidateLayerBounds() = Unit
}
