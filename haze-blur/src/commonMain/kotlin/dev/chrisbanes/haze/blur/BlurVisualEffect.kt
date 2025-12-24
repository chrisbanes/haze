// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext

/**
 * A [VisualEffect] implementation that applies blur effects to content.
 *
 * This effect supports various blur-related features including:
 * - Configurable blur radius
 * - Tinting with multiple colors
 * - Progressive (gradient) blur
 * - Noise overlays
 * - Background color
 * - Alpha masking
 *
 * Example usage:
 * ```
 * Modifier.hazeEffect { scope ->
 *   scope.visualEffect = BlurVisualEffect().apply {
 *     blurRadius = 20.dp
 *     tints = listOf(HazeTint(Color.Black.copy(alpha = 0.5f)))
 *   }
 * }
 * ```
 */
public class BlurVisualEffect : VisualEffect {

  internal var dirtyTracker: Bitmask by mutableStateOf(Bitmask())
    private set

  internal var delegate: Delegate = ScrimBlurVisualEffectDelegate(this)
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "delegate changed. Current $field. New: $value" }
        // attach new delegate
        value.attach()
        // detach old delegate
        field.detach()
        field = value
      }
    }

  override fun update(context: VisualEffectContext) {
    compositionLocalStyle = context.currentValueOf(LocalHazeStyle)

    if (dirtyTracker.any(BlurDirtyFields.InvalidateFlags)) {
      context.invalidateDraw()
    }
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    updateDelegate(context, this)

    try {
      with(delegate) { draw(context) }
    } finally {
      resetDirtyTracker()
    }
  }

  override fun DrawScope.shouldDrawContentBehind(context: VisualEffectContext): Boolean {
    updateDelegate(context, this)
    return delegate is ScrimBlurVisualEffectDelegate
  }

  override fun shouldClip(): Boolean = blurredEdgeTreatment.shape != null

  private fun resetDirtyTracker() {
    dirtyTracker = Bitmask()
  }

  private var _blurEnabled: Boolean? = null

  /**
   * Whether the blur effect is enabled or not, when running on platforms which support blurring.
   *
   * When set to `false` a scrim effect will be used. When set to `true`, and running on a platform
   * which does not support blurring, a scrim effect will be used.
   *
   * Defaults to [HazeBlurDefaults.blurEnabled].
   */
  public var blurEnabled: Boolean
    get() = _blurEnabled ?: HazeBlurDefaults.blurEnabled()
    set(value) {
      if (_blurEnabled == null || value != _blurEnabled) {
        HazeLogger.d(TAG) { "blurEnabled changed. Current: $_blurEnabled. New: $value" }
        _blurEnabled = value
        dirtyTracker += BlurDirtyFields.BlurEnabled
      }
    }

  /**
   * Radius of the blur.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [HazeStyle.blurRadius] value set in [style], if specified.
   *  - [HazeStyle.blurRadius] value set in the [LocalHazeStyle] composition local.
   */
  public var blurRadius: Dp = Dp.Unspecified
    get() {
      return field
        .takeOrElse { style.blurRadius }
        .takeOrElse { compositionLocalStyle.blurRadius }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "blurRadius changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += BlurDirtyFields.BlurRadius
      }
    }

  /**
   * Amount of noise applied to the content, in the range `0f` to `1f`.
   * Anything outside of that range will be clamped.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if in the range 0f..1f.
   *  - [HazeStyle.noiseFactor] value set in [style], if in the range 0f..1f.
   *  - [HazeStyle.noiseFactor] value set in the [LocalHazeStyle] composition local.
   */
  public var noiseFactor: Float = -1f
    get() {
      return field
        .takeOrElse { style.noiseFactor }
        .takeOrElse { compositionLocalStyle.noiseFactor }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "noiseFactor changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += BlurDirtyFields.NoiseFactor
      }
    }

  /**
   * Optional alpha mask which allows effects such as fading via a
   * [Brush.verticalGradient] or similar. This is only applied when [progressive] is null.
   *
   * An alpha mask provides a similar effect as that provided as [HazeProgressive], in a more
   * performant way, but may provide a less pleasing visual result.
   */
  public var mask: Brush? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "mask changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += BlurDirtyFields.Mask
      }
    }

  /**
   * Color to draw behind the blurred content. Ideally should be opaque
   * so that the original content is not visible behind. Typically this would be
   * `MaterialTheme.colorScheme.surface` or similar.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [HazeStyle.backgroundColor] value set in [style], if specified.
   *  - [HazeStyle.backgroundColor] value set in the [LocalHazeStyle] composition local.
   */
  public var backgroundColor: Color = Color.Unspecified
    get() {
      return field
        .takeOrElse { style.backgroundColor }
        .takeOrElse { compositionLocalStyle.backgroundColor }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "backgroundColor changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += BlurDirtyFields.BackgroundColor
      }
    }

  /**
   * The [HazeTint]s to apply to the blurred content.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if not empty.
   *  - [HazeStyle.colorEffects] value set in [style], if not empty.
   *  - [HazeStyle.colorEffects] value set in the [LocalHazeStyle] composition local.
   */
  public var colorEffects: List<HazeColorEffect> = emptyList()
    get() {
      return field.takeIf { it.isNotEmpty() }
        ?: style.colorEffects.takeIf { it.isNotEmpty() }
        ?: compositionLocalStyle.colorEffects.takeIf { it.isNotEmpty() }
        ?: emptyList()
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "colorEffects changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += BlurDirtyFields.ColorEffects
      }
    }

  /**
   * The [HazeTint] to use when Haze uses the fallback scrim functionality.
   *
   * The scrim used whenever [blurEnabled] is resolved to false, either because the host
   * platform does not support blurring, or it has been manually disabled.
   *
   * When the fallback tint is used, the tints provided in [colorEffects] are ignored.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified
   *  - [HazeStyle.fallbackColorEffect] value set in [style], if specified.
   *  - [HazeStyle.fallbackColorEffect] value set in the [LocalHazeStyle] composition local.
   */
  public var fallbackTint: HazeColorEffect = HazeColorEffect.Unspecified
    get() {
      return field.takeIf { it.isSpecified }
        ?: style.fallbackColorEffect.takeIf { it.isSpecified }
        ?: compositionLocalStyle.fallbackColorEffect
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "fallbackTint changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += BlurDirtyFields.FallbackColorEffect
      }
    }

  /**
   * The opacity that the overall effect will drawn with, in the range of 0..1.
   */
  public var alpha: Float = 1f
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "alpha changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += BlurDirtyFields.Alpha
      }
    }

  /**
   * Parameters for enabling a progressive (or gradient) blur effect, or null for a uniform
   * blurring effect. Defaults to null.
   *
   * Please note: progressive blurring effects can be expensive, so you should test on a variety
   * of devices to verify that performance is acceptable for your use case. An alternative and
   * more performant way to achieve this effect is via the [mask] parameter, at the cost of
   * visual finesse.
   */
  public var progressive: HazeProgressive? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "progressive changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += BlurDirtyFields.Progressive
      }
    }

  /**
   * The [BlurredEdgeTreatment] to use when blurring content.
   *
   * Defaults to [BlurredEdgeTreatment.Rectangle] (via [HazeBlurDefaults.blurredEdgeTreatment]), which
   * is nearly always the correct value for when performing background blurring. If you're
   * performing content (foreground) blurring, it depends on the effect which you're looking for.
   *
   * Please note: some platforms do not support all of the treatments available. This value is a
   * best-effort attempt.
   */
  public var blurredEdgeTreatment: BlurredEdgeTreatment = HazeBlurDefaults.blurredEdgeTreatment
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "blurredEdgeTreatment changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += BlurDirtyFields.BlurredEdgeTreatment
      }
    }

  internal var compositionLocalStyle: HazeStyle = HazeStyle.Unspecified
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "LocalHazeStyle changed. Current: $field. New: $value" }
        onStyleChanged(field, value)
        field = value
      }
    }

  /**
   * Style set on this specific blur effect.
   *
   * There are precedence rules to how each styling property is applied. The order of precedence
   * for each property are as follows:
   *
   *  - Property value set directly on this [BlurVisualEffect], if specified.
   *  - Value set here in [style], if specified.
   *  - Value set in the [LocalHazeStyle] composition local.
   */
  public var style: HazeStyle = HazeStyle.Unspecified
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "style changed. Current: $field. New: $value" }
        onStyleChanged(field, value)
        field = value
      }
    }

  public override fun calculateInputScaleFactor(scale: HazeInputScale): Float = when (scale) {
    is HazeInputScale.None -> 1f
    is HazeInputScale.Fixed -> scale.scale
    HazeInputScale.Auto -> {
      val blurRadius = blurRadius.takeOrElse { 0.dp }
      when {
        // For small blurRadius values, input scaling is very noticeable therefore we turn it off
        blurRadius < 7.dp -> 1f
        // For progressive and masks, we need to keep enough resolution for the lowest intensity.
        // 0.5f is about right.
        progressive != null -> 0.5f
        mask != null -> 0.5f
        // Otherwise we use 1/3
        else -> 0.3334f
      }
    }
  }

  override fun requireInvalidation(): Boolean = dirtyTracker.any(BlurDirtyFields.InvalidateFlags)

  override fun preferClipToAreaBounds(): Boolean {
    return backgroundColor.isSpecified && backgroundColor.alpha < 0.9f
  }

  override fun calculateLayerBounds(rect: Rect, density: Density): Rect {
    val blurRadiusPx = with(density) {
      blurRadius.takeOrElse { 0.dp }.toPx()
    }
    return when {
      blurRadiusPx >= 1f -> rect.inflate(blurRadiusPx)
      else -> rect
    }
  }

  private fun onStyleChanged(old: HazeStyle?, new: HazeStyle?) {
    if (old?.colorEffects != new?.colorEffects) dirtyTracker += BlurDirtyFields.ColorEffects
    if (old?.fallbackColorEffect != new?.fallbackColorEffect) dirtyTracker += BlurDirtyFields.ColorEffects
    if (old?.backgroundColor != new?.backgroundColor) dirtyTracker += BlurDirtyFields.BackgroundColor
    if (old?.noiseFactor != new?.noiseFactor) dirtyTracker += BlurDirtyFields.NoiseFactor
    if (old?.blurRadius != new?.blurRadius) dirtyTracker += BlurDirtyFields.BlurRadius
  }

  internal interface Delegate {
    fun attach() = Unit
    fun DrawScope.draw(context: VisualEffectContext)
    fun detach() = Unit
  }

  internal companion object {
    const val TAG = "BlurVisualEffect"
  }
}

internal expect fun BlurVisualEffect.updateDelegate(context: VisualEffectContext, drawScope: DrawScope)
