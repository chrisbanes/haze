// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.RetainedOutputVisualEffect
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext

/**
 * A [VisualEffect] implementation that simulates Apple's iOS Liquid Glass look:
 * refraction, depth layering, specular highlights, and soft tinted glass.
 */
@ExperimentalHazeApi
@Stable
public class GlassVisualEffect() : VisualEffect, RetainedOutputVisualEffect {

  /** Creates a new [GlassVisualEffect] copying all properties from [other]. */
  public constructor(other: GlassVisualEffect) : this() {
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
    _surfaceProfile = other._surfaceProfile
    _chromaticAberrationMode = other._chromaticAberrationMode
    _shape = other._shape
    alpha = other.alpha
    contrast = other.contrast
    whitePoint = other.whitePoint
    chromaMultiplier = other.chromaMultiplier
    refractionScale = other.refractionScale
    contentNormalBlend = other.contentNormalBlend
    specularExponent = other.specularExponent
    fresnelExponent = other.fresnelExponent
    compositionLocalStyle = other.compositionLocalStyle
    style = other.style
    if (other.progressiveOverrideSpecified) {
      progressive = other._progressive
    }
  }

  private var isAttached: Boolean = false

  private var needsDelegateSelection: Boolean = true

  internal var dirtyTracker: Bitmask by mutableStateOf(Bitmask())
    private set

  internal var delegate: Delegate = FallbackGlassDelegate(this)
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
    compositionLocalStyle = context.currentValueOf(LocalGlassStyle)

    if (dirtyTracker.any(GlassDirtyFields.LayerBoundsFlags)) {
      context.invalidateLayerBounds()
    }
    if (dirtyTracker.any(GlassDirtyFields.InvalidateFlags)) {
      needsDelegateSelection = true
      context.invalidateDraw()
    }
  }

  override fun DrawScope.prepareDraw(context: VisualEffectContext) {
    selectDelegateForDraw(context)
    with(delegate) { prepareDraw(context) }
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    try {
      selectDelegateForDraw(context)
      with(delegate) { draw(context) }
    } finally {
      resetDirtyTracker()
    }
  }

  override fun DrawScope.drawForeground(context: VisualEffectContext) {
    with(delegate) { drawForeground(context) }
  }

  override fun shouldDrawContentBehind(context: VisualEffectContext): Boolean {
    return delegate is FallbackGlassDelegate
  }

  override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
    delegate.onTrimMemory(context, level)
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

  override fun shouldClipToNodeBounds(): Boolean = edgeSoftness > 0.dp || !shape.hasZeroCornerRadii()

  internal fun resolveInputScaleFactor(scale: HazeInputScale): Float = when (scale) {
    is HazeInputScale.None -> 1f
    is HazeInputScale.Fixed -> scale.scale
    HazeInputScale.Auto -> 0.75f
  }

  override fun calculateLayerBounds(rect: Rect, density: Density): Rect {
    val effectiveBlurRadiusPx = effectiveSemanticBlurRadiusPx(with(density) { blurRadius.toPx() })
    val resolvedBlurScale = if (
      rect.width.isFinite() && rect.height.isFinite() && rect.width > 0f && rect.height > 0f
    ) {
      // Layout direction only permutes the corner radii, while calibration uses their minimum.
      val cornerRadii = shape.toCornerRadiiPx(rect.size, density, LayoutDirection.Ltr)
      calculateRegularGeometryProfile(
        materialSize = rect.size,
        cornerRadii = cornerRadii,
        blurRadiusPx = effectiveBlurRadiusPx,
        refractionHeight = refractionHeight,
      ).resolve(refractionStrength).blurScale
    } else {
      1f
    }
    val paddingPx = calculateGlassSamplePaddingPx(
      blurRadiusPx = effectiveBlurRadiusPx * resolvedBlurScale,
      refractionScale = refractionScale,
      refractionStrength = refractionStrength,
      chromaticAberrationStrength = chromaticAberrationStrength,
      edgeSoftnessPx = with(density) { edgeSoftness.toPx() },
      foregroundOutsetPx = 0f,
    )
    return rect.inflate(paddingPx)
  }

  override fun shouldPreferClipToAreaBounds(): Boolean = edgeSoftness <= 0.dp && shape.hasZeroCornerRadii()

  /**
   * Strength of refractive distortion, in the range `0f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.refractionStrength] value set in [style], if specified.
   *  - [GlassStyle.refractionStrength] value set in the [LocalGlassStyle] composition local.
   */
  public var refractionStrength: Float = Float.NaN
    get() {
      return field
        .takeOrElse { styleOptics.refractionStrength }
        .takeOrElse { localOptics.refractionStrength }
        .takeOrElse { GlassDefaults.refractionStrength }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "refractionStrength changed. Current: $field. New: $value" }
        field = value.coerceIn(0f, 1f)
        dirtyTracker += GlassDirtyFields.RefractionStrength
      }
    }

  /**
   * Intensity of specular highlights, in the range `0f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.specularIntensity] value set in [style], if specified.
   *  - [GlassStyle.specularIntensity] value set in the [LocalGlassStyle] composition local.
   */
  public var specularIntensity: Float = Float.NaN
    get() {
      return field
        .takeOrElse { styleLighting.specularIntensity }
        .takeOrElse { localLighting.specularIntensity }
        .takeOrElse { GlassDefaults.specularIntensity }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "specularIntensity changed. Current: $field. New: $value" }
        field = value.coerceIn(0f, 1f)
        dirtyTracker += GlassDirtyFields.SpecularIntensity
      }
    }

  /**
   * Depth perception factor (0 = flat, 1 = deep layered glass).
   *
   * Values greater than `0f` require drawing an additional blurred sample for the glass content,
   * which has a rendering cost. Use `0f` when depth layering is not needed.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.depth] value set in [style], if specified.
   *  - [GlassStyle.depth] value set in the [LocalGlassStyle] composition local.
   */
  public var depth: Float = Float.NaN
    get() {
      return field
        .takeOrElse { styleOptics.depth }
        .takeOrElse { localOptics.depth }
        .takeOrElse { GlassDefaults.depth }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "depth changed. Current: $field. New: $value" }
        field = value.coerceIn(0f, 1f)
        dirtyTracker += GlassDirtyFields.Depth
      }
    }

  /**
   * Strength of ambient lighting response and Fresnel accent.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.ambientResponse] value set in [style], if specified.
   *  - [GlassStyle.ambientResponse] value set in the [LocalGlassStyle] composition local.
   */
  public var ambientResponse: Float = Float.NaN
    get() {
      return field
        .takeOrElse { styleLighting.ambientResponse }
        .takeOrElse { localLighting.ambientResponse }
        .takeOrElse { GlassDefaults.ambientResponse }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "ambientResponse changed. Current: $field. New: $value" }
        field = value.coerceIn(0f, 1f)
        dirtyTracker += GlassDirtyFields.AmbientResponse
      }
    }

  /**
   * Glass tint applied to the refracted content.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.tint] value set in [style], if specified.
   *  - [GlassStyle.tint] value set in the [LocalGlassStyle] composition local.
   */
  public var tint: Color = Color.Unspecified
    get() {
      return field
        .takeOrElse { style.tint }
        .takeOrElse { compositionLocalStyle.tint }
        .takeOrElse { GlassDefaults.tint }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "tint changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += GlassDirtyFields.Tint
      }
    }

  /**
   * Softening distance for glass edges.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.edgeSoftness] value set in [style], if specified.
   *  - [GlassStyle.edgeSoftness] value set in the [LocalGlassStyle] composition local.
   */
  public var edgeSoftness: Dp = Dp.Unspecified
    get() {
      return field
        .takeOrElse { styleRendering.edgeSoftness }
        .takeOrElse { localRendering.edgeSoftness }
        .takeOrElse { GlassDefaults.edgeSoftness }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "edgeSoftness changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += GlassDirtyFields.EdgeSoftness
      }
    }

  /**
   * Position of the virtual light source. When unspecified, the center of the layer is used.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.lightPosition] value set in [style], if specified.
   *  - [GlassStyle.lightPosition] value set in the [LocalGlassStyle] composition local.
   *
   * If no value is specified through any of the above, the delegate falls back to the
   * center of the layer at draw time.
   */
  public var lightPosition: Offset = Offset.Unspecified
    get() {
      return field
        .takeOrElse { styleLighting.lightPosition }
        .takeOrElse { localLighting.lightPosition }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "lightPosition changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += GlassDirtyFields.LightPosition
      }
    }

  /**
   * Radius of the blur applied to create depth effect.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.blurRadius] value set in [style], if specified.
   *  - [GlassStyle.blurRadius] value set in the [LocalGlassStyle] composition local.
   *
   * **Note:** On Android API 33+ and Skiko targets, the runtime-shader delegate uses
   * a platform blur render effect as the blurred content input to the runtime shader.
   */
  public var blurRadius: Dp = Dp.Unspecified
    get() {
      return field
        .takeOrElse { styleOptics.blurRadius }
        .takeOrElse { localOptics.blurRadius }
        .takeOrElse { GlassDefaults.blurRadius }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "blurRadius changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += GlassDirtyFields.BlurRadius
      }
    }

  private var progressiveOverrideSpecified: Boolean = false
  private var _progressive: HazeProgressive? = null

  /**
   * Parameters for enabling progressive blur, or null for a uniform blur effect.
   *
   * Setting this property directly, including to null, overrides values inherited from [style]
   * and [LocalGlassStyle]. Call [clearProgressiveOverride] to restore inherited values.
   */
  public var progressive: HazeProgressive?
    get() {
      return if (progressiveOverrideSpecified) {
        _progressive
      } else {
        styleOptics.progressive ?: localOptics.progressive
      }
    }
    set(value) {
      if (!progressiveOverrideSpecified || value != _progressive) {
        HazeLogger.d(TAG) { "progressive changed. Current: $_progressive. New: $value" }
        progressiveOverrideSpecified = true
        _progressive = value
        dirtyTracker += GlassDirtyFields.Progressive
      }
    }

  public fun clearProgressiveOverride() {
    if (progressiveOverrideSpecified) {
      HazeLogger.d(TAG) { "progressive override cleared. Current: $_progressive" }
      progressiveOverrideSpecified = false
      _progressive = null
      dirtyTracker += GlassDirtyFields.Progressive
    }
  }

  /**
   * Height of the refraction zone expressed as a fraction of the smallest dimension (0f..1f).
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.refractionHeight] value set in [style], if specified.
   *  - [GlassStyle.refractionHeight] value set in the [LocalGlassStyle] composition local.
   */
  public var refractionHeight: Float = Float.NaN
    get() {
      return field
        .takeOrElse { styleOptics.refractionHeight }
        .takeOrElse { localOptics.refractionHeight }
        .takeOrElse { GlassDefaults.refractionHeight }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "refractionHeight changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += GlassDirtyFields.RefractionHeight
      }
    }

  /**
   * Strength of chromatic aberration, in the range `0f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.chromaticAberrationStrength] value set in [style], if specified.
   *  - [GlassStyle.chromaticAberrationStrength] value set in the [LocalGlassStyle] composition local.
   */
  public var chromaticAberrationStrength: Float = Float.NaN
    get() {
      return field
        .takeOrElse { styleRendering.chromaticAberrationStrength }
        .takeOrElse { localRendering.chromaticAberrationStrength }
        .takeOrElse { GlassDefaults.chromaticAberrationStrength }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "chromaticAberrationStrength changed. Current: $field. New: $value" }
        field = value.coerceIn(0f, 1f)
        dirtyTracker += GlassDirtyFields.ChromaticAberration
      }
    }

  /**
   * Surface cross-section profile used for the refraction bezel.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.surfaceProfile] value set in [style], if specified.
   *  - [GlassStyle.surfaceProfile] value set in the [LocalGlassStyle] composition local.
   */
  private var _surfaceProfile: SurfaceProfile? = null

  public var surfaceProfile: SurfaceProfile
    get() = _surfaceProfile ?: styleRendering.surfaceProfile ?: localRendering.surfaceProfile ?: GlassDefaults.surfaceProfile
    set(value) {
      if (value != _surfaceProfile) {
        HazeLogger.d(TAG) { "surfaceProfile changed. Current: $_surfaceProfile. New: $value" }
        _surfaceProfile = value
        dirtyTracker += GlassDirtyFields.SurfaceProfile
      }
    }

  /**
   * Clears the direct [surfaceProfile] override and restores inherited values from [style] and
   * [LocalGlassStyle].
   */
  public fun clearSurfaceProfileOverride() {
    if (_surfaceProfile != null) {
      HazeLogger.d(TAG) { "surfaceProfile override cleared. Current: $_surfaceProfile" }
      _surfaceProfile = null
      dirtyTracker += GlassDirtyFields.SurfaceProfile
    }
  }

  /**
   * Quality mode for chromatic aberration (color dispersion).
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.chromaticAberrationMode] value set in [style], if specified.
   *  - [GlassStyle.chromaticAberrationMode] value set in the [LocalGlassStyle] composition local.
   */
  private var _chromaticAberrationMode: ChromaticAberrationMode? = null

  public var chromaticAberrationMode: ChromaticAberrationMode
    get() = _chromaticAberrationMode ?: styleRendering.chromaticAberrationMode ?: localRendering.chromaticAberrationMode ?: GlassDefaults.chromaticAberrationMode
    set(value) {
      if (value != _chromaticAberrationMode) {
        HazeLogger.d(TAG) { "chromaticAberrationMode changed. Current: $_chromaticAberrationMode. New: $value" }
        _chromaticAberrationMode = value
        dirtyTracker += GlassDirtyFields.ChromaticAberrationMode
      }
    }

  /**
   * Clears the direct [chromaticAberrationMode] override and restores inherited values from [style]
   * and [LocalGlassStyle].
   */
  public fun clearChromaticAberrationModeOverride() {
    if (_chromaticAberrationMode != null) {
      HazeLogger.d(TAG) { "chromaticAberrationMode override cleared. Current: $_chromaticAberrationMode" }
      _chromaticAberrationMode = null
      dirtyTracker += GlassDirtyFields.ChromaticAberrationMode
    }
  }

  /**
   * Shape applied to the glass. Defaults to [RoundedCornerShape] with 16.dp corners.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.shape] value set in [style], if specified.
   *  - [GlassStyle.shape] value set in the [LocalGlassStyle] composition local.
   */
  private var _shape: RoundedCornerShape? = null

  public var shape: RoundedCornerShape
    get() = _shape ?: style.shape ?: compositionLocalStyle.shape ?: GlassDefaults.shape
    set(value) {
      if (value != _shape) {
        HazeLogger.d(TAG) { "shape changed. Current: $_shape. New: $value" }
        _shape = value
        dirtyTracker += GlassDirtyFields.Shape
      }
    }

  /**
   * Clears the direct [shape] override and restores inherited values from [style] and
   * [LocalGlassStyle].
   */
  public fun clearShapeOverride() {
    if (_shape != null) {
      HazeLogger.d(TAG) { "shape override cleared. Current: $_shape" }
      _shape = null
      dirtyTracker += GlassDirtyFields.Shape
    }
  }

  /**
   * Overall opacity for the effect, in the range `0f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.alpha] value set in [style], if specified.
   *  - [GlassStyle.alpha] value set in the [LocalGlassStyle] composition local.
   */
  public var alpha: Float = Float.NaN
    get() {
      return field
        .takeOrElse { styleColor.alpha }
        .takeOrElse { localColor.alpha }
        .takeOrElse { GlassDefaults.alpha }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "alpha changed. Current: $field. New: $value" }
        field = value.coerceIn(0f, 1f)
        dirtyTracker += GlassDirtyFields.Alpha
      }
    }

  /**
   * Overall contrast adjustment, in the range `-1f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.contrast] value set in [style], if specified.
   *  - [GlassStyle.contrast] value set in the [LocalGlassStyle] composition local.
   */
  public var contrast: Float = Float.NaN
    get() {
      return field
        .takeOrElse { styleColor.contrast }
        .takeOrElse { localColor.contrast }
        .takeOrElse { GlassDefaults.contrast }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "contrast changed. Current: $field. New: $value" }
        field = value.coerceIn(-1f, 1f)
        dirtyTracker += GlassDirtyFields.Contrast
      }
    }

  /**
   * White point adjustment, in the range `-1f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.whitePoint] value set in [style], if specified.
   *  - [GlassStyle.whitePoint] value set in the [LocalGlassStyle] composition local.
   */
  public var whitePoint: Float = Float.NaN
    get() {
      return field
        .takeOrElse { styleColor.whitePoint }
        .takeOrElse { localColor.whitePoint }
        .takeOrElse { GlassDefaults.whitePoint }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "whitePoint changed. Current: $field. New: $value" }
        field = value.coerceIn(-1f, 1f)
        dirtyTracker += GlassDirtyFields.WhitePoint
      }
    }

  /**
   * Chroma multiplier for saturation control, in the range `0f..2f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.chromaMultiplier] value set in [style], if specified.
   *  - [GlassStyle.chromaMultiplier] value set in the [LocalGlassStyle] composition local.
   */
  public var chromaMultiplier: Float = Float.NaN
    get() {
      return field
        .takeOrElse { styleColor.chromaMultiplier }
        .takeOrElse { localColor.chromaMultiplier }
        .takeOrElse { GlassDefaults.chromaMultiplier }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "chromaMultiplier changed. Current: $field. New: $value" }
        field = value.coerceIn(0f, 2f)
        dirtyTracker += GlassDirtyFields.ChromaMultiplier
      }
    }

  /**
   * Scale factor for refraction distortion.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.refractionScale] value set in [style], if specified.
   *  - [GlassStyle.refractionScale] value set in the [LocalGlassStyle] composition local.
   */
  public var refractionScale: Float = Float.NaN
    get() {
      return field
        .takeOrElse { styleOptics.refractionScale }
        .takeOrElse { localOptics.refractionScale }
        .takeOrElse { GlassDefaults.refractionScale }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "refractionScale changed. Current: $field. New: $value" }
        field = value.coerceAtLeast(0f)
        dirtyTracker += GlassDirtyFields.RefractionScale
      }
    }

  /**
   * Blend factor for content normals, in the range `0f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.contentNormalBlend] value set in [style], if specified.
   *  - [GlassStyle.contentNormalBlend] value set in the [LocalGlassStyle] composition local.
   */
  public var contentNormalBlend: Float = Float.NaN
    get() {
      return field
        .takeOrElse { styleRendering.contentNormalBlend }
        .takeOrElse { localRendering.contentNormalBlend }
        .takeOrElse { GlassDefaults.contentNormalBlend }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "contentNormalBlend changed. Current: $field. New: $value" }
        field = value.coerceIn(0f, 1f)
        dirtyTracker += GlassDirtyFields.ContentNormalBlend
      }
    }

  /**
   * Exponent controlling specular highlight shape.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.specularExponent] value set in [style], if specified.
   *  - [GlassStyle.specularExponent] value set in the [LocalGlassStyle] composition local.
   */
  public var specularExponent: Float = Float.NaN
    get() {
      return field
        .takeOrElse { styleLighting.specularExponent }
        .takeOrElse { localLighting.specularExponent }
        .takeOrElse { GlassDefaults.specularExponent }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "specularExponent changed. Current: $field. New: $value" }
        field = value.coerceAtLeast(0f)
        dirtyTracker += GlassDirtyFields.SpecularExponent
      }
    }

  /**
   * Exponent controlling Fresnel edge effect intensity.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.fresnelExponent] value set in [style], if specified.
   *  - [GlassStyle.fresnelExponent] value set in the [LocalGlassStyle] composition local.
   */
  public var fresnelExponent: Float = Float.NaN
    get() {
      return field
        .takeOrElse { styleLighting.fresnelExponent }
        .takeOrElse { localLighting.fresnelExponent }
        .takeOrElse { GlassDefaults.fresnelExponent }
    }
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "fresnelExponent changed. Current: $field. New: $value" }
        field = value.coerceAtLeast(0f)
        dirtyTracker += GlassDirtyFields.FresnelExponent
      }
    }

  /**
   * Optional style container that can set multiple parameters at once.
   *
   * There are precedence rules to how each styling property is applied. The order of precedence
   * for each property are as follows:
   *
   *  - Property value set directly on this [GlassVisualEffect], if specified.
   *  - Value set here in [style], if specified.
   *  - Value set in the [LocalGlassStyle] composition local.
   */
  public var style: GlassStyle = GlassStyle.Unspecified
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "style changed. Current: $field. New: $value" }
        onStyleChanged(old = field, new = value)
        field = value
        dirtyTracker += GlassDirtyFields.Style
      }
    }

  private val styleOptics: GlassOptics get() = style.optics
  private val localOptics: GlassOptics get() = compositionLocalStyle.optics
  private val styleLighting: GlassLighting get() = style.lighting
  private val localLighting: GlassLighting get() = compositionLocalStyle.lighting
  private val styleColor: GlassColor get() = style.color
  private val localColor: GlassColor get() = compositionLocalStyle.color
  private val styleRendering: GlassRendering get() = style.rendering
  private val localRendering: GlassRendering get() = compositionLocalStyle.rendering

  internal var compositionLocalStyle: GlassStyle = GlassDefaults.style
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "LocalGlassStyle changed. Current: $field. New: $value" }
        onStyleChanged(field, value)
        field = value
      }
    }

  internal interface Delegate {
    fun attach() = Unit
    fun DrawScope.prepareDraw(context: VisualEffectContext) = Unit
    fun DrawScope.draw(context: VisualEffectContext)
    fun DrawScope.drawForeground(context: VisualEffectContext) = Unit
    fun detach() = Unit
    fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) = Unit
  }

  private fun resetDirtyTracker() {
    dirtyTracker = Bitmask()
  }

  private fun DrawScope.selectDelegateForDraw(context: VisualEffectContext) {
    if (needsDelegateSelection) {
      delegate = updateDelegate(context, this)
      needsDelegateSelection = false
    }
  }

  private fun onStyleChanged(old: GlassStyle, new: GlassStyle) {
    if (old.optics.refractionStrength != new.optics.refractionStrength) {
      dirtyTracker += GlassDirtyFields.RefractionStrength
    }
    if (old.optics.depth != new.optics.depth) {
      dirtyTracker += GlassDirtyFields.Depth
    }
    if (old.optics.blurRadius != new.optics.blurRadius) {
      dirtyTracker += GlassDirtyFields.BlurRadius
    }
    if (old.optics.progressive != new.optics.progressive) {
      dirtyTracker += GlassDirtyFields.Progressive
    }
    if (old.optics.refractionHeight != new.optics.refractionHeight) {
      dirtyTracker += GlassDirtyFields.RefractionHeight
    }
    if (old.optics.refractionScale != new.optics.refractionScale) {
      dirtyTracker += GlassDirtyFields.RefractionScale
    }
    if (old.lighting.specularIntensity != new.lighting.specularIntensity) {
      dirtyTracker += GlassDirtyFields.SpecularIntensity
    }
    if (old.lighting.ambientResponse != new.lighting.ambientResponse) {
      dirtyTracker += GlassDirtyFields.AmbientResponse
    }
    if (old.lighting.lightPosition != new.lighting.lightPosition) {
      dirtyTracker += GlassDirtyFields.LightPosition
    }
    if (old.lighting.specularExponent != new.lighting.specularExponent) {
      dirtyTracker += GlassDirtyFields.SpecularExponent
    }
    if (old.lighting.fresnelExponent != new.lighting.fresnelExponent) {
      dirtyTracker += GlassDirtyFields.FresnelExponent
    }
    if (old.tint != new.tint) {
      dirtyTracker += GlassDirtyFields.Tint
    }
    if (old.shape != new.shape) {
      dirtyTracker += GlassDirtyFields.Shape
    }
    if (old.color.alpha != new.color.alpha) {
      dirtyTracker += GlassDirtyFields.Alpha
    }
    if (old.color.contrast != new.color.contrast) {
      dirtyTracker += GlassDirtyFields.Contrast
    }
    if (old.color.whitePoint != new.color.whitePoint) {
      dirtyTracker += GlassDirtyFields.WhitePoint
    }
    if (old.color.chromaMultiplier != new.color.chromaMultiplier) {
      dirtyTracker += GlassDirtyFields.ChromaMultiplier
    }
    if (old.rendering.edgeSoftness != new.rendering.edgeSoftness) {
      dirtyTracker += GlassDirtyFields.EdgeSoftness
    }
    if (old.rendering.contentNormalBlend != new.rendering.contentNormalBlend) {
      dirtyTracker += GlassDirtyFields.ContentNormalBlend
    }
    if (old.rendering.surfaceProfile != new.rendering.surfaceProfile) {
      dirtyTracker += GlassDirtyFields.SurfaceProfile
    }
    if (old.rendering.chromaticAberrationStrength != new.rendering.chromaticAberrationStrength) {
      dirtyTracker += GlassDirtyFields.ChromaticAberration
    }
    if (old.rendering.chromaticAberrationMode != new.rendering.chromaticAberrationMode) {
      dirtyTracker += GlassDirtyFields.ChromaticAberrationMode
    }
  }

  internal companion object {
    const val TAG = "GlassVisualEffect"
  }
}

internal interface RetainedOutputDelegate {
  fun canDrawRetainedOutput(): Boolean

  fun shouldDrawRetainedOutput(): Boolean = canDrawRetainedOutput()

  fun clearRetainedOutput()
}

internal expect fun GlassVisualEffect.updateDelegate(
  context: VisualEffectContext,
  drawScope: DrawScope,
): GlassVisualEffect.Delegate

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
