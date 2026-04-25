// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

class BlurVisualEffectLifecycleTest {

  @Test
  fun attachAndDetach_areIdempotent() {
    val effect = BlurVisualEffect()
    val delegate = TrackingDelegate()

    effect.delegate = delegate

    assertThat(delegate.attachCount).isEqualTo(0)
    assertThat(delegate.detachCount).isEqualTo(0)

    effect.attach(FakeVisualEffectContext)
    effect.attach(FakeVisualEffectContext)

    assertThat(delegate.attachCount).isEqualTo(1)
    assertThat(delegate.detachCount).isEqualTo(0)

    effect.detach()
    effect.detach()

    assertThat(delegate.attachCount).isEqualTo(1)
    assertThat(delegate.detachCount).isEqualTo(1)
  }

  @Test
  fun changingDelegateWhileAttached_detachesOldAndAttachesNew() {
    val effect = BlurVisualEffect()
    val oldDelegate = TrackingDelegate()
    val newDelegate = TrackingDelegate()

    effect.delegate = oldDelegate
    effect.attach(FakeVisualEffectContext)

    effect.delegate = newDelegate

    assertThat(oldDelegate.attachCount).isEqualTo(1)
    assertThat(oldDelegate.detachCount).isEqualTo(1)
    assertThat(newDelegate.attachCount).isEqualTo(1)
    assertThat(newDelegate.detachCount).isEqualTo(0)

    effect.detach()

    assertThat(newDelegate.detachCount).isEqualTo(1)
  }

  @Test
  fun blurRadius_prefersDirectThenStyleThenCompositionLocal() {
    val effect = BlurVisualEffect()

    effect.compositionLocalStyle = HazeBlurStyle(
      colorEffects = emptyList(),
      blurRadius = HazeBlurDefaults.blurRadius,
    )
    assertThat(effect.blurRadius).isEqualTo(HazeBlurDefaults.blurRadius)

    effect.style = HazeBlurStyle(
      colorEffects = emptyList(),
      blurRadius = HazeBlurDefaults.blurRadius * 2,
    )
    assertThat(effect.blurRadius).isEqualTo(HazeBlurDefaults.blurRadius * 2)

    effect.blurRadius = HazeBlurDefaults.blurRadius * 3
    assertThat(effect.blurRadius).isEqualTo(HazeBlurDefaults.blurRadius * 3)
  }
}

private data object FakeVisualEffectContext : VisualEffectContext {
  override val position: Offset = Offset.Zero
  override val size: Size = Size.Zero
  override val layerSize: Size = Size.Zero
  override val layerOffset: Offset = Offset.Zero
  override val rootBounds: Rect = Rect.Zero
  override val inputScale: HazeInputScale = HazeInputScale.None
  override val windowId: Any? = null
  override val areas: List<HazeArea> = emptyList()
  override val state: HazeState? = null
  override val visualEffect: VisualEffect = VisualEffect.Empty
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun requirePlatformContext(): PlatformContext = error("Unused in lifecycle tests")
  override fun requireDensity(): Density = Density(1f)
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = error("Unused in lifecycle tests")
  override fun requireGraphicsContext(): GraphicsContext = error("Unused in lifecycle tests")
  override fun invalidateDraw() = Unit
}

private class TrackingDelegate : BlurVisualEffect.Delegate {
  var attachCount: Int = 0
    private set
  var detachCount: Int = 0
    private set

  override fun attach() {
    attachCount++
  }

  override fun DrawScope.draw(context: VisualEffectContext) = Unit

  override fun detach() {
    detachCount++
  }
}
