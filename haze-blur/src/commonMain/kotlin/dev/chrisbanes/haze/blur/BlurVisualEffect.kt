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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.RetainedOutputVisualEffect
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext

/**
 * Legacy mutable Blur runtime retained while callers migrate to [hazeBlur].
 *
 * New code should configure Blur with [HazeBlurStyle]. A modifier node owns each instance of this
 * runtime, including its delegate, retained layers, input-scale history, and render-effect cache.
 */
@Stable
public class BlurVisualEffect() : VisualEffect, RetainedOutputVisualEffect {

  /** Creates a new [BlurVisualEffect] copying Styles and direct property overrides from [other]. */
  public constructor(other: BlurVisualEffect) : this() {
    compositionLocalStyle = other.compositionLocalStyle
    style = other.style
    blurEnabledOverride = other.blurEnabledOverride
    blurRadiusOverride = other.blurRadiusOverride
    noiseFactorOverride = other.noiseFactorOverride
    maskOverrideSet = other.maskOverrideSet
    maskOverride = other.maskOverride
    backgroundColorOverride = other.backgroundColorOverride
    colorEffectsOverride = other.colorEffectsOverride?.toList()
    fallbackTintOverride = other.fallbackTintOverride
    alphaOverride = other.alphaOverride
    progressiveOverrideSet = other.progressiveOverrideSet
    progressiveOverride = other.progressiveOverride
    blurredEdgeTreatmentOverride = other.blurredEdgeTreatmentOverride
  }

  private var isAttached: Boolean = false
  private var needsDelegateSelection: Boolean = true
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

  override fun attach(context: VisualEffectContext) {
    if (!isAttached) {
      isAttached = true
      delegate.attach()
    }
  }

  override fun detach(context: VisualEffectContext) {
    if (isAttached) {
      isAttached = false
      delegate.detach()
      clearRenderEffectCache()
      inputScalePolicy.reset()
    }
  }

  override fun update(context: VisualEffectContext) {
    compositionLocalStyle = context.currentValueOf(LocalHazeBlurStyle)
    if (dirtyTracker.any(BlurDirtyFields.InvalidateFlags)) {
      needsDelegateSelection = true
      context.invalidateDraw()
    }
  }

  override fun DrawScope.prepareDraw(context: VisualEffectContext) {
    selectDelegateForDraw(context)
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    try {
      selectDelegateForDraw(context)
      with(delegate) { draw(context) }
    } finally {
      resetDirtyTracker()
    }
  }

  override fun shouldDrawContentBehind(context: VisualEffectContext): Boolean {
    return delegate is ScrimBlurVisualEffectDelegate
  }

  override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
    delegate.onTrimMemory(context, level)
    clearRenderEffectCache()
  }

  override fun canDrawRetainedOutput(context: VisualEffectContext): Boolean {
    return (delegate as? RetainedOutputDelegate)?.canDrawRetainedOutput() == true
  }

  override fun shouldDrawRetainedOutput(context: VisualEffectContext): Boolean {
    return (delegate as? RetainedOutputDelegate)?.shouldDrawRetainedOutput() == true
  }

  override fun clearRetainedOutput() {
    (delegate as? RetainedOutputDelegate)?.clearRetainedOutput()
  }

  override fun shouldClipToNodeBounds(): Boolean = blurredEdgeTreatment.shape != null

  private fun resetDirtyTracker() {
    dirtyTracker = Bitmask()
  }

  private fun DrawScope.selectDelegateForDraw(context: VisualEffectContext) {
    if (needsDelegateSelection) {
      delegate = updateDelegate(context, this)
      needsDelegateSelection = false
    }
  }

  private var blurEnabledOverride: Boolean? = null

  /** Whether Blur is enabled on supported platforms. */
  public var blurEnabled: Boolean
    get() = blurEnabledOverride ?: resolvedStyle.blurEnabled
    set(value) {
      if (value != blurEnabled) {
        HazeLogger.d(TAG) { "blurEnabled changed. Current: $blurEnabled. New: $value" }
        blurEnabledOverride = value
        dirtyTracker += BlurDirtyFields.BlurEnabled
      } else if (blurEnabledOverride == null) {
        blurEnabledOverride = value
      }
    }

  private var blurRadiusOverride: Dp? = null

  /** Radius of the blur. */
  public var blurRadius: Dp
    get() = blurRadiusOverride ?: resolvedStyle.blurRadius
    set(value) {
      val normalized = value.takeIf(Dp::isSpecified)
      if (normalized != blurRadiusOverride) {
        HazeLogger.d(TAG) { "blurRadius changed. Current: $blurRadius. New: $value" }
        val old = blurRadius
        blurRadiusOverride = normalized
        if (old != blurRadius) dirtyTracker += BlurDirtyFields.BlurRadius
      }
    }

  private var noiseFactorOverride: Float? = null

  /** Amount of noise applied to the content, clamped to `0f..1f`. */
  public var noiseFactor: Float
    get() = noiseFactorOverride ?: resolvedStyle.noiseFactor
    set(value) {
      val normalized = value.takeIf { it >= 0f }?.coerceAtMost(1f)
      if (normalized != noiseFactorOverride) {
        HazeLogger.d(TAG) { "noiseFactor changed. Current: $noiseFactor. New: $value" }
        val old = noiseFactor
        noiseFactorOverride = normalized
        if (old != noiseFactor) dirtyTracker += BlurDirtyFields.NoiseFactor
      }
    }

  private var maskOverrideSet: Boolean = false
  private var maskOverride: Brush? = null

  /** Optional alpha mask. */
  public var mask: Brush?
    get() = if (maskOverrideSet) maskOverride else resolvedStyle.mask
    set(value) {
      if (!maskOverrideSet || value != maskOverride) {
        HazeLogger.d(TAG) { "mask changed. Current: $mask. New: $value" }
        val old = mask
        maskOverrideSet = true
        maskOverride = value
        if (old != mask) dirtyTracker += BlurDirtyFields.Mask
      }
    }

  private var backgroundColorOverride: Color? = null

  /** Color drawn behind the blurred content. */
  public var backgroundColor: Color
    get() = backgroundColorOverride ?: resolvedStyle.backgroundColor
    set(value) {
      val normalized = value.takeIf { it.isSpecified }
      if (normalized != backgroundColorOverride) {
        HazeLogger.d(TAG) { "backgroundColor changed. Current: $backgroundColor. New: $value" }
        val old = backgroundColor
        backgroundColorOverride = normalized
        if (old != backgroundColor) dirtyTracker += BlurDirtyFields.BackgroundColor
      }
    }

  private var colorEffectsOverride: List<HazeColorEffect>? = null

  /** Color effects applied to the blurred content. */
  public var colorEffects: List<HazeColorEffect>?
    get() = colorEffectsOverride ?: resolvedStyle.colorEffects
    set(value) {
      val snapshot = value?.toList()
      if (snapshot != colorEffectsOverride) {
        HazeLogger.d(TAG) { "colorEffects changed. Current: $colorEffects. New: $snapshot" }
        val old = colorEffects
        colorEffectsOverride = snapshot
        if (old != colorEffects) dirtyTracker += BlurDirtyFields.ColorEffects
      }
    }

  private var fallbackTintOverride: HazeColorEffect? = null

  /** Color effect used by the fallback scrim. */
  public var fallbackTint: HazeColorEffect
    get() = fallbackTintOverride ?: resolvedStyle.fallbackColorEffect
    set(value) {
      val normalized = value.takeIf { it.isSpecified }
      if (normalized != fallbackTintOverride) {
        HazeLogger.d(TAG) { "fallbackTint changed. Current: $fallbackTint. New: $value" }
        val old = fallbackTint
        fallbackTintOverride = normalized
        if (old != fallbackTint) dirtyTracker += BlurDirtyFields.FallbackColorEffect
      }
    }

  private var alphaOverride: Float? = null

  /** Opacity of the overall effect. */
  public var alpha: Float
    get() = alphaOverride ?: resolvedStyle.alpha
    set(value) {
      val normalized = value.coerceIn(0f, 1f)
      if (normalized != alphaOverride) {
        HazeLogger.d(TAG) { "alpha changed. Current: $alpha. New: $value" }
        val old = alpha
        alphaOverride = normalized
        if (old != alpha) dirtyTracker += BlurDirtyFields.Alpha
      }
    }

  private var progressiveOverrideSet: Boolean = false
  private var progressiveOverride: HazeProgressive? = null

  /** Progressive Blur parameters, or null for uniform Blur. */
  public var progressive: HazeProgressive?
    get() = if (progressiveOverrideSet) progressiveOverride else resolvedStyle.progressive
    set(value) {
      if (!progressiveOverrideSet || value != progressiveOverride) {
        HazeLogger.d(TAG) { "progressive changed. Current: $progressive. New: $value" }
        val old = progressive
        progressiveOverrideSet = true
        progressiveOverride = value
        if (old != progressive) dirtyTracker += BlurDirtyFields.Progressive
      }
    }

  private var blurredEdgeTreatmentOverride: BlurredEdgeTreatment? = null

  /** Edge treatment used while blurring. */
  public var blurredEdgeTreatment: BlurredEdgeTreatment
    get() = blurredEdgeTreatmentOverride ?: resolvedStyle.blurredEdgeTreatment
    set(value) {
      if (value != blurredEdgeTreatmentOverride) {
        HazeLogger.d(TAG) {
          "blurredEdgeTreatment changed. Current: $blurredEdgeTreatment. New: $value"
        }
        val old = blurredEdgeTreatment
        blurredEdgeTreatmentOverride = value
        if (old != blurredEdgeTreatment) dirtyTracker += BlurDirtyFields.BlurredEdgeTreatment
      }
    }

  internal fun resolveInputScaleFactor(context: VisualEffectContext): Float {
    val blurRadiusPx = with(context.requireDensity()) { blurRadius.toPx() }
    return inputScalePolicy.resolve(
      requestedScale = context.inputScale,
      blurRadiusPx = blurRadiusPx,
      layerSize = context.layerSize,
      progressive = progressive != null,
    )
  }

  internal var compositionLocalStyle: HazeBlurStyle = HazeBlurStyle
    set(value) {
      if (field !== value) {
        HazeLogger.d(TAG) { "LocalHazeBlurStyle changed. Current: $field. New: $value" }
        field = value
        resolveStyle()
      }
    }

  /** Explicit Style replayed after [LocalHazeBlurStyle]. */
  public var style: HazeBlurStyle = HazeBlurStyle
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

  override fun shouldPreferClipToAreaBounds(): Boolean {
    return backgroundColor.isSpecified && backgroundColor.alpha > 0f && backgroundColor.alpha < 0.9f
  }

  override fun calculateLayerBounds(rect: Rect, density: Density): Rect {
    val blurRadiusPx = with(density) { blurRadius.toPx() }
    return if (blurRadiusPx >= 1f) rect.inflate(blurRadiusPx) else rect
  }

  private fun onResolvedStyleChanged(
    old: ResolvedHazeBlurStyle,
    new: ResolvedHazeBlurStyle,
  ) {
    if (old.blurEnabled != new.blurEnabled) dirtyTracker += BlurDirtyFields.BlurEnabled
    if (old.blurRadius != new.blurRadius) dirtyTracker += BlurDirtyFields.BlurRadius
    if (old.noiseFactor != new.noiseFactor) dirtyTracker += BlurDirtyFields.NoiseFactor
    if (old.mask != new.mask) dirtyTracker += BlurDirtyFields.Mask
    if (old.backgroundColor != new.backgroundColor) dirtyTracker += BlurDirtyFields.BackgroundColor
    if (old.colorEffects != new.colorEffects) dirtyTracker += BlurDirtyFields.ColorEffects
    if (old.fallbackColorEffect != new.fallbackColorEffect) {
      dirtyTracker += BlurDirtyFields.FallbackColorEffect
    }
    if (old.alpha != new.alpha) dirtyTracker += BlurDirtyFields.Alpha
    if (old.progressive != new.progressive) dirtyTracker += BlurDirtyFields.Progressive
    if (old.blurredEdgeTreatment != new.blurredEdgeTreatment) {
      dirtyTracker += BlurDirtyFields.BlurredEdgeTreatment
    }
  }

  internal interface Delegate {
    fun attach() = Unit
    fun DrawScope.draw(context: VisualEffectContext)
    fun detach() = Unit
    fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) = Unit
  }

  internal companion object {
    const val TAG = "BlurVisualEffect"
  }
}

internal interface RetainedOutputDelegate {
  fun canDrawRetainedOutput(): Boolean

  fun shouldDrawRetainedOutput(): Boolean = canDrawRetainedOutput()

  fun clearRetainedOutput()
}

internal expect fun BlurVisualEffect.updateDelegate(
  context: VisualEffectContext,
  drawScope: DrawScope,
): BlurVisualEffect.Delegate
