// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.Poko
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
  fun changingDelegate_resetsAutomaticHysteresisHistory() {
    val effect = BlurVisualEffect().apply {
      blurRadius = BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX.dp
      delegate = TrackingDelegate()
    }

    assertThat(
      effect.resolveInputScaleFactor(
        ScalingVisualEffectContext(
          layerSize = Size(BlurInputScalePolicy.AGGRESSIVE_AREA_PX, 1f),
        ),
      ),
    ).isEqualTo(0.5f)

    effect.delegate = TrackingDelegate()
    effect.blurRadius = (BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX - 1f).dp

    assertThat(
      effect.resolveInputScaleFactor(
        ScalingVisualEffectContext(
          layerSize = Size(BlurInputScalePolicy.AGGRESSIVE_AREA_PX - 1f, 1f),
        ),
      ),
    ).isEqualTo(0.8f)
  }

  @Test
  fun shouldDrawContentBehind_reflectsCurrentDelegateWithoutMutatingIt() {
    val effect = BlurVisualEffect()
    effect.delegate = ScrimBlurVisualEffectDelegate(effect)

    assertThat(effect.shouldDrawContentBehind(FakeVisualEffectContext)).isTrue()
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

    effect.compositionLocalStyle = HazeBlurStyle {
      colorEffects(emptyList())
      blurRadius(HazeBlurDefaults.blurRadius)
    }
    assertThat(effect.blurRadius).isEqualTo(HazeBlurDefaults.blurRadius)

    effect.style = HazeBlurStyle {
      colorEffects(emptyList())
      blurRadius(HazeBlurDefaults.blurRadius * 2)
    }
    assertThat(effect.blurRadius).isEqualTo(HazeBlurDefaults.blurRadius * 2)

    effect.blurRadius = HazeBlurDefaults.blurRadius * 3
    assertThat(effect.blurRadius).isEqualTo(HazeBlurDefaults.blurRadius * 3)
  }

  @Test
  fun copy_preservesStyleAndCompositionLocalInheritance() {
    val inheritedMask = Brush.verticalGradient(listOf(Color.White, Color.Transparent))
    val inheritedColorEffect = HazeColorEffect.tint(Color.Magenta)
    val inheritedFallback = HazeColorEffect.tint(Color.Gray)
    val inheritedProgressive = HazeProgressive.verticalGradient()
    val original = BlurVisualEffect().apply {
      compositionLocalStyle = fullStyle(
        blurEnabled = false,
        blurRadius = 12.dp,
        noiseFactor = 0.2f,
        backgroundColor = Color.Red,
        mask = inheritedMask,
        colorEffect = inheritedColorEffect,
        fallback = inheritedFallback,
        alpha = 0.7f,
        progressive = inheritedProgressive,
        blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded,
      )
      style = HazeBlurStyle {
        blurRadius(16.dp)
      }
    }
    val copy = BlurVisualEffect(original)
    val localMask = Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
    val localColorEffect = HazeColorEffect.tint(Color.Blue)
    val localFallback = HazeColorEffect.tint(Color.Green)
    val localProgressive = HazeProgressive.verticalGradient()

    assertThat(copy.blurEnabled).isFalse()
    assertThat(copy.blurRadius).isEqualTo(16.dp)
    assertThat(copy.noiseFactor).isEqualTo(0.2f)
    assertThat(copy.backgroundColor).isEqualTo(Color.Red)
    assertThat(copy.mask).isEqualTo(inheritedMask)
    assertThat(copy.colorEffects).isEqualTo(listOf(inheritedColorEffect))
    assertThat(copy.fallbackTint).isEqualTo(inheritedFallback)
    assertThat(copy.alpha).isEqualTo(0.7f)
    assertThat(copy.progressive).isEqualTo(inheritedProgressive)
    assertThat(copy.blurredEdgeTreatment).isEqualTo(BlurredEdgeTreatment.Unbounded)

    copy.compositionLocalStyle = fullStyle(
      blurEnabled = true,
      blurRadius = 20.dp,
      noiseFactor = 0.6f,
      backgroundColor = Color.Cyan,
      mask = localMask,
      colorEffect = localColorEffect,
      fallback = localFallback,
      alpha = 0.4f,
      progressive = localProgressive,
      blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded,
    )
    copy.style = HazeBlurStyle {
      blurRadius(24.dp)
    }

    assertThat(copy.blurEnabled).isTrue()
    assertThat(copy.blurRadius).isEqualTo(24.dp)
    assertThat(copy.noiseFactor).isEqualTo(0.6f)
    assertThat(copy.backgroundColor).isEqualTo(Color.Cyan)
    assertThat(copy.mask).isEqualTo(localMask)
    assertThat(copy.colorEffects).isEqualTo(listOf(localColorEffect))
    assertThat(copy.fallbackTint).isEqualTo(localFallback)
    assertThat(copy.alpha).isEqualTo(0.4f)
    assertThat(copy.progressive).isEqualTo(localProgressive)
    assertThat(copy.blurredEdgeTreatment).isEqualTo(BlurredEdgeTreatment.Unbounded)
  }

  @Test
  fun copy_preservesDirectOverrides() {
    val inheritedMask = Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
    val inheritedProgressive = HazeProgressive.verticalGradient()
    val directFallback = HazeColorEffect.tint(Color.Green)
    val original = BlurVisualEffect().apply {
      style = fullStyle(
        blurEnabled = false,
        blurRadius = 12.dp,
        noiseFactor = 0.2f,
        backgroundColor = Color.Red,
        mask = inheritedMask,
        progressive = inheritedProgressive,
        blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded,
      )
      blurEnabled = true
      blurRadius = 32.dp
      noiseFactor = 0.9f
      backgroundColor = Color.Yellow
      mask = null
      colorEffects = emptyList()
      fallbackTint = directFallback
      alpha = 0.8f
      progressive = null
      blurredEdgeTreatment = BlurredEdgeTreatment.Rectangle
    }
    val copy = BlurVisualEffect(original)

    copy.style = fullStyle(
      blurEnabled = false,
      blurRadius = 20.dp,
      noiseFactor = 0.4f,
      backgroundColor = Color.Cyan,
      mask = inheritedMask,
      progressive = inheritedProgressive,
      blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded,
    )

    assertThat(copy.blurEnabled).isTrue()
    assertThat(copy.blurRadius).isEqualTo(32.dp)
    assertThat(copy.noiseFactor).isEqualTo(0.9f)
    assertThat(copy.backgroundColor).isEqualTo(Color.Yellow)
    assertThat(copy.mask).isNull()
    assertThat(copy.colorEffects).isEqualTo(emptyList())
    assertThat(copy.fallbackTint).isEqualTo(directFallback)
    assertThat(copy.alpha).isEqualTo(0.8f)
    assertThat(copy.progressive).isNull()
    assertThat(copy.blurredEdgeTreatment).isEqualTo(BlurredEdgeTreatment.Rectangle)
  }

  @Test
  fun inheritedStructuralStyleChanges_invalidateLayerBoundsOnlyWhenRequired() {
    val effect = BlurVisualEffect()
    val context = TrackingInvalidationContext()

    context.localStyle = HazeBlurStyle {
      blurRadius(24.dp)
    }
    effect.update(context)

    assertThat(context.invalidateLayerBoundsCount).isEqualTo(1)
    assertThat(context.invalidateDrawCount).isEqualTo(0)

    context.resetInvalidations()
    context.localStyle = HazeBlurStyle {
      blurRadius(24.dp)
      noiseFactor(0.5f)
    }
    effect.update(context)

    assertThat(context.invalidateLayerBoundsCount).isEqualTo(0)
    assertThat(context.invalidateDrawCount).isEqualTo(1)

    context.resetInvalidations()
    context.localStyle = HazeBlurStyle {
      blurRadius(24.dp)
      noiseFactor(0.5f)
      backgroundColor(Color.Red.copy(alpha = 0.5f))
    }
    effect.update(context)

    assertThat(context.invalidateLayerBoundsCount).isEqualTo(1)
    assertThat(context.invalidateDrawCount).isEqualTo(0)

    context.resetInvalidations()
    context.localStyle = HazeBlurStyle {
      blurRadius(24.dp)
      noiseFactor(0.5f)
      backgroundColor(Color.Blue.copy(alpha = 0.5f))
    }
    effect.update(context)

    assertThat(context.invalidateLayerBoundsCount).isEqualTo(0)
    assertThat(context.invalidateDrawCount).isEqualTo(1)

    context.resetInvalidations()
    context.localStyle = HazeBlurStyle {
      blurRadius(24.dp)
      noiseFactor(0.5f)
      backgroundColor(Color.Blue.copy(alpha = 0.5f))
      blurredEdgeTreatment(BlurredEdgeTreatment.Unbounded)
    }
    effect.update(context)

    assertThat(context.invalidateLayerBoundsCount).isEqualTo(1)
    assertThat(context.invalidateDrawCount).isEqualTo(0)
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

private fun fullStyle(
  blurEnabled: Boolean,
  blurRadius: Dp,
  noiseFactor: Float,
  backgroundColor: Color,
  mask: Brush? = null,
  colorEffect: HazeColorEffect = HazeColorEffect.tint(Color.Magenta),
  fallback: HazeColorEffect = HazeColorEffect.tint(Color.Gray),
  alpha: Float = 0.7f,
  progressive: HazeProgressive? = null,
  blurredEdgeTreatment: BlurredEdgeTreatment = BlurredEdgeTreatment.Rectangle,
): HazeBlurStyle = HazeBlurStyle {
  blurEnabled(blurEnabled)
  blurRadius(blurRadius)
  noiseFactor(noiseFactor)
  backgroundColor(backgroundColor)
  mask(mask)
  colorEffects(listOf(colorEffect))
  fallbackColorEffect(fallback)
  alpha(alpha)
  progressive(progressive)
  blurredEdgeTreatment(blurredEdgeTreatment)
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

private class TrackingInvalidationContext : VisualEffectContext by FakeVisualEffectContext {
  var localStyle: HazeBlurStyle = HazeBlurStyle
  var invalidateDrawCount: Int = 0
    private set
  var invalidateLayerBoundsCount: Int = 0
    private set

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T {
    check(local === LocalHazeBlurStyle)
    return localStyle as T
  }

  override fun invalidateDraw() {
    invalidateDrawCount++
  }

  override fun invalidateLayerBounds() {
    invalidateLayerBoundsCount++
  }

  fun resetInvalidations() {
    invalidateDrawCount = 0
    invalidateLayerBoundsCount = 0
  }
}

@Poko
private class ScalingVisualEffectContext(
  override val layerSize: Size,
  override val inputScale: HazeInputScale = HazeInputScale.Auto,
) : VisualEffectContext by FakeVisualEffectContext

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
