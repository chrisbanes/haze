// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.collection.LruCache
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectLayoutScope
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRendererDrawHooks
import dev.chrisbanes.haze.HazeEffectRendererLifecycle
import dev.chrisbanes.haze.HazeEffectRendererRetainedOutput
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.TrimMemoryLevel

/** Node-owned Blur renderer configured exclusively by [HazeBlurStyle]. */
@Stable
@OptIn(InternalHazeApi::class)
internal class BlurVisualEffect :
  HazeEffectRenderer<HazeBlurStyle>,
  HazeEffectRendererLifecycle<HazeBlurStyle>,
  HazeEffectRendererDrawHooks<HazeBlurStyle>,
  HazeEffectRendererRetainedOutput {

  private var isAttached: Boolean = false
  private var lifecycleScope: HazeEffectLifecycleScope? = null
  private var needsDelegateSelection: Boolean = true
  private var needsLayerBoundsInvalidation: Boolean = false
  private val inputScalePolicy = BlurInputScalePolicy()
  internal val renderEffectCache = LruCache<RenderEffectCacheKey, RenderEffect>(maxSize = 50)

  internal var dirtyTracker: Bitmask by mutableStateOf(Bitmask())
    private set

  private var resolvedStyle: ResolvedHazeBlurStyle =
    resolveHazeBlurStyle(HazeBlurStyle, HazeBlurStyle)

  internal var delegate: Delegate = ScrimBlurVisualEffectDelegate(this)
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "delegate changed. Current $field. New: $value" }
        if (isAttached) {
          value.attach()
          field.detach()
        }
        field = value
        inputScalePolicy.reset()
      }
    }

  override fun attach(scope: HazeEffectLifecycleScope) {
    if (!isAttached) {
      isAttached = true
      lifecycleScope = scope
      delegate.attach()
    }
  }

  override fun detach() {
    if (isAttached) {
      isAttached = false
      delegate.detach()
      clearRenderEffectCache()
      inputScalePolicy.reset()
      lifecycleScope = null
    }
  }

  override fun update(
    scope: HazeEffectLifecycleScope,
    style: HazeBlurStyle,
    sampling: HazeSampling,
  ) {
    this.style = style
    compositionLocalStyle = scope.currentValueOf(LocalHazeBlurStyle)
    if (dirtyTracker.any(BlurDirtyFields.InvalidateFlags)) {
      needsDelegateSelection = true
      if (needsLayerBoundsInvalidation) {
        needsLayerBoundsInvalidation = false
        scope.invalidateLayerBounds()
      } else {
        scope.invalidateDraw()
      }
    }
  }

  override fun HazeEffectRuntimeDrawScope.prepareDraw(style: HazeBlurStyle) {
    with(this as DrawScope) {
      selectDelegateForDraw(this@prepareDraw)
    }
  }

  override fun shouldPrepareDraw(style: HazeBlurStyle): Boolean {
    if (alpha != 0f) return true
    resetDirtyTracker()
    return false
  }

  override fun HazeEffectDrawScope.draw(style: HazeBlurStyle) {
    val runtimeScope = this as HazeEffectRuntimeDrawScope
    try {
      with(runtimeScope as DrawScope) {
        selectDelegateForDraw(runtimeScope)
        with(delegate) { draw(runtimeScope) }
      }
    } finally {
      resetDirtyTracker()
    }
  }

  override fun shouldDrawContentBehind(): Boolean {
    return delegate is ScrimBlurVisualEffectDelegate
  }

  override fun onTrimMemory(level: TrimMemoryLevel) {
    lifecycleScope?.let { delegate.onTrimMemory(it, level) }
    clearRenderEffectCache()
  }

  override fun canDrawRetainedOutput(): Boolean {
    return (delegate as? RetainedOutputDelegate)?.canDrawRetainedOutput() == true
  }

  override fun shouldDrawRetainedOutput(): Boolean {
    return (delegate as? RetainedOutputDelegate)?.shouldDrawRetainedOutput() == true
  }

  override fun clearRetainedOutput() {
    (delegate as? RetainedOutputDelegate)?.clearRetainedOutput()
  }

  override fun shouldClipToNodeBounds(): Boolean = blurredEdgeTreatment.isBounded()

  override fun dispose() {
    detach()
  }

  private fun resetDirtyTracker() {
    dirtyTracker = Bitmask()
  }

  private fun DrawScope.selectDelegateForDraw(context: HazeEffectRuntimeDrawScope) {
    if (needsDelegateSelection) {
      delegate = updateDelegate(context, this)
      needsDelegateSelection = false
    }
  }

  internal val blurEnabled: Boolean get() = resolvedStyle.blurEnabled
  internal val blurRadius: Dp get() = resolvedStyle.blurRadius
  internal val noiseFactor: Float get() = resolvedStyle.noiseFactor
  internal val mask: Brush? get() = resolvedStyle.mask
  internal val backgroundColor: Color get() = resolvedStyle.backgroundColor
  internal val colorEffects: List<HazeColorEffect>? get() = resolvedStyle.colorEffects
  internal val fallbackTint: HazeColorEffect get() = resolvedStyle.fallbackColorEffect
  internal val alpha: Float get() = resolvedStyle.alpha
  internal val progressive: HazeProgressive? get() = resolvedStyle.progressive
  internal val blurredEdgeTreatment: BlurredEdgeTreatment
    get() = resolvedStyle.blurredEdgeTreatment

  internal fun resolveInputScaleFactor(context: HazeEffectRuntimeDrawScope): Float {
    val blurRadiusPx = with(context) { blurRadius.toPx() }
    return inputScalePolicy.resolve(
      requestedScale = context.sampling,
      blurRadiusPx = blurRadiusPx,
      layerSize = context.layerSize,
      progressive = progressive != null,
    )
  }

  private var compositionLocalStyle: HazeBlurStyle = HazeBlurStyle
    set(value) {
      if (field !== value) {
        HazeLogger.d(TAG) { "LocalHazeBlurStyle changed. Current: $field. New: $value" }
        field = value
        resolveStyle()
      }
    }

  private var style: HazeBlurStyle = HazeBlurStyle
    set(value) {
      if (field !== value) {
        HazeLogger.d(TAG) { "style changed. Current: $field. New: $value" }
        field = value
        resolveStyle()
      }
    }

  private fun resolveStyle() {
    val previous = resolvedStyle
    val next = resolveHazeBlurStyle(compositionLocalStyle, style)
    if (previous != next) {
      resolvedStyle = next
      onResolvedStyleChanged(previous, next)
    }
  }

  override fun shouldPreferClipToInputBounds(): Boolean {
    return backgroundColor.prefersClipToAreaBounds()
  }

  override fun HazeEffectLayoutScope.calculateLayerBounds(style: HazeBlurStyle): Rect {
    val blurRadiusPx = blurRadius.toPx()
    return if (blurRadiusPx >= 1f) modifierBounds.inflate(blurRadiusPx) else modifierBounds
  }

  private fun onResolvedStyleChanged(
    old: ResolvedHazeBlurStyle,
    new: ResolvedHazeBlurStyle,
  ) {
    if (old.blurEnabled != new.blurEnabled) dirtyTracker += BlurDirtyFields.BlurEnabled
    if (old.blurRadius != new.blurRadius) {
      dirtyTracker += BlurDirtyFields.BlurRadius
      needsLayerBoundsInvalidation = true
    }
    if (old.noiseFactor != new.noiseFactor) dirtyTracker += BlurDirtyFields.NoiseFactor
    if (old.mask != new.mask) dirtyTracker += BlurDirtyFields.Mask
    if (old.backgroundColor != new.backgroundColor) {
      dirtyTracker += BlurDirtyFields.BackgroundColor
      if (
        old.backgroundColor.prefersClipToAreaBounds() !=
        new.backgroundColor.prefersClipToAreaBounds()
      ) {
        needsLayerBoundsInvalidation = true
      }
    }
    if (old.colorEffects != new.colorEffects) dirtyTracker += BlurDirtyFields.ColorEffects
    if (old.fallbackColorEffect != new.fallbackColorEffect) {
      dirtyTracker += BlurDirtyFields.FallbackColorEffect
    }
    if (old.alpha != new.alpha) dirtyTracker += BlurDirtyFields.Alpha
    if (old.progressive != new.progressive) dirtyTracker += BlurDirtyFields.Progressive
    if (old.blurredEdgeTreatment != new.blurredEdgeTreatment) {
      dirtyTracker += BlurDirtyFields.BlurredEdgeTreatment
      if (old.blurredEdgeTreatment.isBounded() != new.blurredEdgeTreatment.isBounded()) {
        needsLayerBoundsInvalidation = true
      }
    }
  }

  internal interface Delegate {
    fun attach() = Unit
    fun DrawScope.draw(context: HazeEffectRuntimeDrawScope)
    fun detach() = Unit
    fun onTrimMemory(context: HazeEffectLifecycleScope, level: TrimMemoryLevel) = Unit
  }

  internal companion object {
    const val TAG = "BlurVisualEffect"
  }
}

private fun Color.prefersClipToAreaBounds(): Boolean {
  return isSpecified && alpha > 0f && alpha < 0.9f
}

private fun BlurredEdgeTreatment.isBounded(): Boolean = shape != null

internal interface RetainedOutputDelegate {
  fun canDrawRetainedOutput(): Boolean

  fun shouldDrawRetainedOutput(): Boolean = canDrawRetainedOutput()

  fun clearRetainedOutput()
}

/** Tracks whether an asynchronously generated Blur output is still valid for presentation. */
internal class BlurRetainedOutputState {
  var generation: Int = 0
    private set
  var isAvailable: Boolean = false
    private set
  var isPending: Boolean = false
    private set

  fun beginOutputUpdate(): Int {
    isPending = true
    return generation
  }

  fun completeOutputUpdate(generationAtStart: Int): Boolean {
    if (generation != generationAtStart) return false
    isPending = false
    isAvailable = true
    return true
  }

  fun finishOutputUpdate(generationAtStart: Int) {
    if (generation == generationAtStart) {
      isPending = false
    }
  }

  fun clear() {
    generation++
    isAvailable = false
    isPending = false
  }
}

internal expect fun BlurVisualEffect.updateDelegate(
  context: HazeEffectRuntimeDrawScope,
  drawScope: DrawScope,
): BlurVisualEffect.Delegate
