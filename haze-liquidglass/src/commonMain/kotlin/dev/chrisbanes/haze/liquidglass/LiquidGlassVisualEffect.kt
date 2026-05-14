// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext

/**
 * A [VisualEffect] implementation that simulates the iOS-style liquid glass look:
 * refraction, depth layering, specular highlights, and soft tinted glass.
 */
@OptIn(ExperimentalHazeApi::class)
@Stable
public class LiquidGlassVisualEffect() : VisualEffect {

  /** Creates a new [LiquidGlassVisualEffect] copying all properties from [other]. */
  public constructor(other: LiquidGlassVisualEffect) : this() {
    refractionStrength = other.refractionStrength
    specularIntensity = other.specularIntensity
    depth = other.depth
    ambientResponse = other.ambientResponse
    tint = other.tint
    edgeSoftness = other.edgeSoftness
    lightPosition = other.lightPosition
    blurRadius = other.blurRadius
    refractionHeight = other.refractionHeight
    chromaticAberrationStrength = other.chromaticAberrationStrength
    surfaceProfile = other.surfaceProfile
    chromaticAberrationMode = other.chromaticAberrationMode
    shape = other.shape
    alpha = other.alpha
    style = other.style
  }

  private var isAttached: Boolean = false

  private var needsDelegateSelection: Boolean = true

  internal var dirtyTracker: Bitmask by mutableStateOf(Bitmask())
    private set

  internal var delegate: Delegate = FallbackLiquidGlassDelegate(this)
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "delegate changed. Current $field. New: $value" }
        val old = field
        field = value
        if (isAttached) {
          old.detach()
          value.attach()
        }
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
    }
  }

  override fun update(context: VisualEffectContext) {
    compositionLocalStyle = context.currentValueOf(LocalLiquidGlassStyle)

    if (dirtyTracker.any(LiquidGlassDirtyFields.InvalidateFlags)) {
      needsDelegateSelection = true
      context.invalidateDraw()
    }
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    try {
      if (needsDelegateSelection) {
        delegate = updateDelegate(context, this)
        needsDelegateSelection = false
      }
      with(delegate) { draw(context) }
    } finally {
      resetDirtyTracker()
    }
  }

  override fun shouldDrawContentBehind(context: VisualEffectContext): Boolean {
    return delegate is FallbackLiquidGlassDelegate
  }

  override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
    delegate.onTrimMemory(context, level)
  }

  override fun shouldClipToNodeBounds(): Boolean = edgeSoftness > 0.dp || !shape.hasZeroCornerRadii()

  internal fun resolveInputScaleFactor(scale: HazeInputScale): Float = when (scale) {
    is HazeInputScale.None -> 1f
    is HazeInputScale.Fixed -> scale.scale
    HazeInputScale.Auto -> 0.75f
  }

  override fun calculateLayerBounds(rect: Rect, density: Density): Rect {
    val softnessPx = with(density) { edgeSoftness.toPx() }
    return if (softnessPx > 0f) rect.inflate(softnessPx) else rect
  }

  override fun shouldPreferClipToAreaBounds(): Boolean = edgeSoftness <= 0.dp && shape.hasZeroCornerRadii()

  /**
   * Strength of refractive distortion, in the range `0f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [LiquidGlassStyle.refractionStrength] value set in [style], if specified.
   *  - [LiquidGlassStyle.refractionStrength] value set in the [LocalLiquidGlassStyle] composition local.
   */
  public var refractionStrength: Float = Float.NaN
    get() {
      return field
        .takeOrElse { style.refractionStrength }
        .takeOrElse { compositionLocalStyle.refractionStrength }
        .takeOrElse { LiquidGlassDefaults.refractionStrength }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "refractionStrength changed. Current: $field. New: $value" }
        field = value.coerceIn(0f, 1f)
        dirtyTracker += LiquidGlassDirtyFields.RefractionStrength
      }
    }

  /**
   * Intensity of specular highlights, in the range `0f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [LiquidGlassStyle.specularIntensity] value set in [style], if specified.
   *  - [LiquidGlassStyle.specularIntensity] value set in the [LocalLiquidGlassStyle] composition local.
   */
  public var specularIntensity: Float = Float.NaN
    get() {
      return field
        .takeOrElse { style.specularIntensity }
        .takeOrElse { compositionLocalStyle.specularIntensity }
        .takeOrElse { LiquidGlassDefaults.specularIntensity }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "specularIntensity changed. Current: $field. New: $value" }
        field = value.coerceIn(0f, 1f)
        dirtyTracker += LiquidGlassDirtyFields.SpecularIntensity
      }
    }

  /**
   * Depth perception factor (0 = flat, 1 = deep layered glass).
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [LiquidGlassStyle.depth] value set in [style], if specified.
   *  - [LiquidGlassStyle.depth] value set in the [LocalLiquidGlassStyle] composition local.
   */
  public var depth: Float = Float.NaN
    get() {
      return field
        .takeOrElse { style.depth }
        .takeOrElse { compositionLocalStyle.depth }
        .takeOrElse { LiquidGlassDefaults.depth }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "depth changed. Current: $field. New: $value" }
        field = value.coerceIn(0f, 1f)
        dirtyTracker += LiquidGlassDirtyFields.Depth
      }
    }

  /**
   * Strength of ambient lighting response and Fresnel accent.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [LiquidGlassStyle.ambientResponse] value set in [style], if specified.
   *  - [LiquidGlassStyle.ambientResponse] value set in the [LocalLiquidGlassStyle] composition local.
   */
  public var ambientResponse: Float = Float.NaN
    get() {
      return field
        .takeOrElse { style.ambientResponse }
        .takeOrElse { compositionLocalStyle.ambientResponse }
        .takeOrElse { LiquidGlassDefaults.ambientResponse }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "ambientResponse changed. Current: $field. New: $value" }
        field = value.coerceIn(0f, 1f)
        dirtyTracker += LiquidGlassDirtyFields.AmbientResponse
      }
    }

  /**
   * Glass tint applied to the refracted content.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [LiquidGlassStyle.tint] value set in [style], if specified.
   *  - [LiquidGlassStyle.tint] value set in the [LocalLiquidGlassStyle] composition local.
   */
  public var tint: Color = Color.Unspecified
    get() {
      return field
        .takeOrElse { style.tint }
        .takeOrElse { compositionLocalStyle.tint }
        .takeOrElse { LiquidGlassDefaults.tint }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "tint changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += LiquidGlassDirtyFields.Tint
      }
    }

  /**
   * Softening distance for glass edges.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [LiquidGlassStyle.edgeSoftness] value set in [style], if specified.
   *  - [LiquidGlassStyle.edgeSoftness] value set in the [LocalLiquidGlassStyle] composition local.
   */
  public var edgeSoftness: Dp = Dp.Unspecified
    get() {
      return field
        .takeOrElse { style.edgeSoftness }
        .takeOrElse { compositionLocalStyle.edgeSoftness }
        .takeOrElse { LiquidGlassDefaults.edgeSoftness }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "edgeSoftness changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += LiquidGlassDirtyFields.EdgeSoftness
      }
    }

  /**
   * Position of the virtual light source. When unspecified, the center of the layer is used.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [LiquidGlassStyle.lightPosition] value set in [style], if specified.
   *  - [LiquidGlassStyle.lightPosition] value set in the [LocalLiquidGlassStyle] composition local.
   *
   * If no value is specified through any of the above, the delegate falls back to the
   * center of the layer at draw time.
   */
  public var lightPosition: Offset = Offset.Unspecified
    get() {
      return field
        .takeOrElse { style.lightPosition }
        .takeOrElse { compositionLocalStyle.lightPosition }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "lightPosition changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += LiquidGlassDirtyFields.LightPosition
      }
    }

  /**
   * Radius of the blur applied to create depth effect.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [LiquidGlassStyle.blurRadius] value set in [style], if specified.
   *  - [LiquidGlassStyle.blurRadius] value set in the [LocalLiquidGlassStyle] composition local.
   */
  public var blurRadius: Dp = Dp.Unspecified
    get() {
      return field
        .takeOrElse { style.blurRadius }
        .takeOrElse { compositionLocalStyle.blurRadius }
        .takeOrElse { LiquidGlassDefaults.blurRadius }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "blurRadius changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += LiquidGlassDirtyFields.BlurRadius
      }
    }

  /**
   * Height of the refraction zone expressed as a fraction of the smallest dimension (0f..1f).
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [LiquidGlassStyle.refractionHeight] value set in [style], if specified.
   *  - [LiquidGlassStyle.refractionHeight] value set in the [LocalLiquidGlassStyle] composition local.
   */
  public var refractionHeight: Float = Float.NaN
    get() {
      return field
        .takeOrElse { style.refractionHeight }
        .takeOrElse { compositionLocalStyle.refractionHeight }
        .takeOrElse { LiquidGlassDefaults.refractionHeight }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "refractionHeight changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += LiquidGlassDirtyFields.RefractionHeight
      }
    }

  /**
   * Strength of chromatic aberration, in the range `0f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [LiquidGlassStyle.chromaticAberrationStrength] value set in [style], if specified.
   *  - [LiquidGlassStyle.chromaticAberrationStrength] value set in the [LocalLiquidGlassStyle] composition local.
   */
  public var chromaticAberrationStrength: Float = Float.NaN
    get() {
      return field
        .takeOrElse { style.chromaticAberrationStrength }
        .takeOrElse { compositionLocalStyle.chromaticAberrationStrength }
        .takeOrElse { LiquidGlassDefaults.chromaticAberrationStrength }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "chromaticAberrationStrength changed. Current: $field. New: $value" }
        field = value.coerceIn(0f, 1f)
        dirtyTracker += LiquidGlassDirtyFields.ChromaticAberration
      }
    }

  /**
   * Surface cross-section profile used for the refraction bezel.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [LiquidGlassStyle.surfaceProfile] value set in [style], if specified.
   *  - [LiquidGlassStyle.surfaceProfile] value set in the [LocalLiquidGlassStyle] composition local.
   */
  private var _surfaceProfile: SurfaceProfile? = null

  public var surfaceProfile: SurfaceProfile
    get() = _surfaceProfile ?: style.surfaceProfile ?: compositionLocalStyle.surfaceProfile ?: LiquidGlassDefaults.surfaceProfile
    set(value) {
      if (value != _surfaceProfile) {
        HazeLogger.d(TAG) { "surfaceProfile changed. Current: $_surfaceProfile. New: $value" }
        _surfaceProfile = value
        dirtyTracker += LiquidGlassDirtyFields.SurfaceProfile
      }
    }

  /**
   * Quality mode for chromatic aberration (color dispersion).
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [LiquidGlassStyle.chromaticAberrationMode] value set in [style], if specified.
   *  - [LiquidGlassStyle.chromaticAberrationMode] value set in the [LocalLiquidGlassStyle] composition local.
   */
  private var _chromaticAberrationMode: ChromaticAberrationMode? = null

  public var chromaticAberrationMode: ChromaticAberrationMode
    get() = _chromaticAberrationMode ?: style.chromaticAberrationMode ?: compositionLocalStyle.chromaticAberrationMode ?: LiquidGlassDefaults.chromaticAberrationMode
    set(value) {
      if (value != _chromaticAberrationMode) {
        HazeLogger.d(TAG) { "chromaticAberrationMode changed. Current: $_chromaticAberrationMode. New: $value" }
        _chromaticAberrationMode = value
        dirtyTracker += LiquidGlassDirtyFields.ChromaticAberrationMode
      }
    }

  /**
   * Shape applied to the glass. Defaults to [RoundedCornerShape] with 16.dp corners.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [LiquidGlassStyle.shape] value set in [style], if specified.
   *  - [LiquidGlassStyle.shape] value set in the [LocalLiquidGlassStyle] composition local.
   */
  private var _shape: RoundedCornerShape? = null

  public var shape: RoundedCornerShape
    get() = _shape ?: style.shape ?: compositionLocalStyle.shape ?: LiquidGlassDefaults.shape
    set(value) {
      if (value != _shape) {
        HazeLogger.d(TAG) { "shape changed. Current: $_shape. New: $value" }
        _shape = value
        dirtyTracker += LiquidGlassDirtyFields.Shape
      }
    }

  /**
   * Overall opacity for the effect, in the range `0f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [LiquidGlassStyle.alpha] value set in [style], if specified.
   *  - [LiquidGlassStyle.alpha] value set in the [LocalLiquidGlassStyle] composition local.
   */
  public var alpha: Float = Float.NaN
    get() {
      return field
        .takeOrElse { style.alpha }
        .takeOrElse { compositionLocalStyle.alpha }
        .takeOrElse { LiquidGlassDefaults.alpha }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "alpha changed. Current: $field. New: $value" }
        field = value.coerceIn(0f, 1f)
        dirtyTracker += LiquidGlassDirtyFields.Alpha
      }
    }

  /**
   * Optional style container that can set multiple parameters at once.
   *
   * There are precedence rules to how each styling property is applied. The order of precedence
   * for each property are as follows:
   *
   *  - Property value set directly on this [LiquidGlassVisualEffect], if specified.
   *  - Value set here in [style], if specified.
   *  - Value set in the [LocalLiquidGlassStyle] composition local.
   */
  public var style: LiquidGlassStyle = LiquidGlassStyle.Unspecified
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "style changed. Current: $field. New: $value" }
        onStyleChanged(old = field, new = value)
        field = value
        dirtyTracker += LiquidGlassDirtyFields.Style
      }
    }

  internal var compositionLocalStyle: LiquidGlassStyle = LiquidGlassDefaults.style
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "LocalLiquidGlassStyle changed. Current: $field. New: $value" }
        onStyleChanged(field, value)
        field = value
      }
    }

  internal interface Delegate {
    fun attach() = Unit
    fun DrawScope.draw(context: VisualEffectContext)
    fun detach() = Unit
    fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) = Unit
  }

  private fun resetDirtyTracker() {
    dirtyTracker = Bitmask()
  }

  private fun onStyleChanged(old: LiquidGlassStyle, new: LiquidGlassStyle) {
    if (old.refractionStrength != new.refractionStrength) {
      dirtyTracker += LiquidGlassDirtyFields.RefractionStrength
    }
    if (old.specularIntensity != new.specularIntensity) {
      dirtyTracker += LiquidGlassDirtyFields.SpecularIntensity
    }
    if (old.depth != new.depth) {
      dirtyTracker += LiquidGlassDirtyFields.Depth
    }
    if (old.ambientResponse != new.ambientResponse) {
      dirtyTracker += LiquidGlassDirtyFields.AmbientResponse
    }
    if (old.tint != new.tint) {
      dirtyTracker += LiquidGlassDirtyFields.Tint
    }
    if (old.edgeSoftness != new.edgeSoftness) {
      dirtyTracker += LiquidGlassDirtyFields.EdgeSoftness
    }
    if (old.lightPosition != new.lightPosition) {
      dirtyTracker += LiquidGlassDirtyFields.LightPosition
    }
    if (old.blurRadius != new.blurRadius) {
      dirtyTracker += LiquidGlassDirtyFields.BlurRadius
    }
    if (old.refractionHeight != new.refractionHeight) {
      dirtyTracker += LiquidGlassDirtyFields.RefractionHeight
    }
    if (old.chromaticAberrationStrength != new.chromaticAberrationStrength) {
      dirtyTracker += LiquidGlassDirtyFields.ChromaticAberration
    }
    if (old.surfaceProfile != new.surfaceProfile) {
      dirtyTracker += LiquidGlassDirtyFields.SurfaceProfile
    }
    if (old.chromaticAberrationMode != new.chromaticAberrationMode) {
      dirtyTracker += LiquidGlassDirtyFields.ChromaticAberrationMode
    }
    if (old.shape != new.shape) {
      dirtyTracker += LiquidGlassDirtyFields.Shape
    }
    if (old.alpha != new.alpha) {
      dirtyTracker += LiquidGlassDirtyFields.Alpha
    }
  }

  internal companion object {
    const val TAG = "LiquidGlassVisualEffect"
  }
}

internal expect fun LiquidGlassVisualEffect.updateDelegate(
  context: VisualEffectContext,
  drawScope: DrawScope,
): LiquidGlassVisualEffect.Delegate

private fun RoundedCornerShape.hasZeroCornerRadii(): Boolean {
  // Use unit values to check if all corner sizes resolve to zero.
  val unitSize = androidx.compose.ui.geometry.Size(1f, 1f)
  val unitDensity = androidx.compose.ui.unit.Density(1f)
  return topStart.toPx(unitSize, unitDensity) == 0f &&
    topEnd.toPx(unitSize, unitDensity) == 0f &&
    bottomEnd.toPx(unitSize, unitDensity) == 0f &&
    bottomStart.toPx(unitSize, unitDensity) == 0f
}

private inline fun Float.takeOrElse(default: () -> Float): Float {
  return if (this.isNaN()) default() else this
}
