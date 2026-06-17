// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.VisualEffectContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalHazeApi::class)
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

    effect.detach(FakeVisualEffectContext)
    effect.detach(FakeVisualEffectContext)

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

    effect.detach(FakeVisualEffectContext)

    assertThat(newDelegate.detachCount).isEqualTo(1)
  }

  @Test
  fun shouldDrawContentBehind_reflectsCurrentDelegateWithoutMutatingIt() {
    val effect = BlurVisualEffect()
    effect.delegate = ScrimBlurVisualEffectDelegate(effect)

    assertThat(effect.shouldDrawContentBehind(FakeVisualEffectContext)).isTrue()
  }

  @Test
  fun resolveInputScaleFactor_autoUsesBlurSpecificRules() {
    val effect = BlurVisualEffect().apply {
      blurRadius = 20.dp
    }

    assertThat(effect.resolveInputScaleFactor(HazeInputScale.Auto)).isEqualTo(0.3334f)
  }

  @Test
  fun canUseRenderEffect_requiresApi31AndHardwareAcceleration() {
    assertThat(canUseRenderEffect(sdkInt = 31, isHardwareAccelerated = true)).isTrue()
    assertThat(canUseRenderEffect(sdkInt = 31, isHardwareAccelerated = false)).isFalse()
    assertThat(canUseRenderEffect(sdkInt = 30, isHardwareAccelerated = true)).isFalse()
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

  @Test
  fun retainedOutputAvailabilityReflectsDelegate() {
    val effect = BlurVisualEffect()
    val delegate = RetainedTrackingBlurDelegate()
    effect.delegate = delegate

    assertThat(effect.canDrawRetainedOutput(FakeVisualEffectContext)).isFalse()
    assertThat(effect.shouldDrawRetainedOutput(FakeVisualEffectContext)).isFalse()

    delegate.retainedOutputAvailable = true

    assertThat(effect.canDrawRetainedOutput(FakeVisualEffectContext)).isTrue()
    assertThat(effect.shouldDrawRetainedOutput(FakeVisualEffectContext)).isTrue()

    delegate.retainedOutputAvailable = false
    delegate.pendingRetainedOutput = true

    assertThat(effect.canDrawRetainedOutput(FakeVisualEffectContext)).isFalse()
    assertThat(effect.shouldDrawRetainedOutput(FakeVisualEffectContext)).isTrue()

    effect.clearRetainedOutput()

    assertThat(delegate.clearCount).isEqualTo(1)
    assertThat(effect.canDrawRetainedOutput(FakeVisualEffectContext)).isFalse()
    assertThat(effect.shouldDrawRetainedOutput(FakeVisualEffectContext)).isFalse()
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
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun positionOf(area: HazeArea): Offset = area.coordinates.localPosition
  override fun boundsOf(area: HazeArea): Rect? {
    val position = area.coordinates.localPosition
    return if (position.isSpecified && area.size.isSpecified) Rect(position, area.size) else null
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

private class RetainedTrackingBlurDelegate : BlurVisualEffect.Delegate, RetainedOutputDelegate {
  var retainedOutputAvailable = false
  var pendingRetainedOutput = false
  var clearCount = 0

  override fun canDrawRetainedOutput(): Boolean = retainedOutputAvailable

  override fun shouldDrawRetainedOutput(): Boolean = retainedOutputAvailable || pendingRetainedOutput

  override fun clearRetainedOutput() {
    clearCount++
    retainedOutputAvailable = false
    pendingRetainedOutput = false
  }

  override fun DrawScope.draw(context: VisualEffectContext) = Unit
}
