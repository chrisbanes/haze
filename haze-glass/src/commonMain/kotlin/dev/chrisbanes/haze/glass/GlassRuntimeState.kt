// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.InternalHazeApi

/** Mutable style and interaction state owned by one [GlassRuntimeEffect]. */
@ExperimentalHazeApi
@Stable
@OptIn(InternalHazeApi::class)
@Suppress("ktlint:standard:property-naming")
internal abstract class GlassRuntimeState {
  protected constructor()

  internal var onConfigurationChanged: ((Int) -> Unit)? = null

  internal var runtimeEffectFactory: GlassRuntimeEffectFactory = PlatformGlassRuntimeEffectFactory
    set(value) {
      if (field !== value) {
        field = value
        markDirty(GlassDirtyFields.RuntimeEffectFactory)
      }
    }

  internal var nextInteractionRevision: Long = 0L

  internal var hoveredSlot: GlassInteractionSlot? by mutableStateOf(null)
    private set

  internal var focusedSlot: GlassInteractionSlot? by mutableStateOf(null)
    private set

  internal var pressedSlot: GlassInteractionSlot? by mutableStateOf(null)
    private set
  private var hoveredOverride = false
  private var focusedOverride = false
  private var pressedOverride = false

  // A Style contains only declarations. Each attached effect owns these evaluated slots.
  private var styleHoveredSlot: GlassInteractionSlot? by mutableStateOf(null)
  private var styleFocusedSlot: GlassInteractionSlot? by mutableStateOf(null)
  private var stylePressedSlot: GlassInteractionSlot? by mutableStateOf(null)

  private var interactionSlotsSnapshot: GlassInteractionSlots = GlassInteractionSlots()
  private var currentInteractionTopology = interactionSlotsSnapshot.resolveInteractionTopology()

  internal val resolvedInteractionSlots: GlassInteractionSlots
    get() = interactionSlotsSnapshot

  internal val resolvedInteractionTopology: GlassInteractionTopology
    get() = currentInteractionTopology

  internal open val observesPointerEvents: Boolean
    get() = interactionSlotsSnapshot.hovered != null || interactionSlotsSnapshot.pressed != null

  internal var _interactionSource: InteractionSource? by mutableStateOf(
    null,
    referentialEqualityPolicy(),
  )

  public var interactionSource: InteractionSource?
    get() = _interactionSource
    set(value) {
      if (_interactionSource !== value) {
        HazeLogger.d(TAG) { "interactionSource changed. Current: $_interactionSource. New: $value" }
        _interactionSource = value
        onInteractionConfigurationChanged()
      }
    }

  internal val interactionLightRadiusFraction: Float
    get() = inheritedStyleValues.interactionLightRadiusFraction

  internal var _interactionTransformTarget: GlassTransformTarget by mutableStateOf(
    GlassTransformTarget.MaterialOnly,
  )

  public var interactionTransformTarget: GlassTransformTarget
    get() = _interactionTransformTarget
    set(value) {
      if (_interactionTransformTarget != value) {
        HazeLogger.d(TAG) { "interactionTransformTarget changed. Current: $_interactionTransformTarget. New: $value" }
        _interactionTransformTarget = value
        onInteractionConfigurationChanged()
      }
    }

  internal var _interactionTransformPivot: GlassTransformPivot by mutableStateOf(
    GlassTransformPivot.Pointer,
  )

  public var interactionTransformPivot: GlassTransformPivot
    get() = _interactionTransformPivot
    set(value) {
      if (_interactionTransformPivot != value) {
        HazeLogger.d(TAG) { "interactionTransformPivot changed. Current: $_interactionTransformPivot. New: $value" }
        _interactionTransformPivot = value
        onInteractionConfigurationChanged()
      }
    }

  internal val interactionPositionAnimationSpec
    get() = inheritedStyleValues.interactionPositionAnimationSpec

  internal var _interactionReducedMotionPolicy: GlassReducedMotionPolicy by mutableStateOf(
    GlassReducedMotionPolicy.System,
  )

  public var interactionReducedMotionPolicy: GlassReducedMotionPolicy
    get() = _interactionReducedMotionPolicy
    set(value) {
      if (_interactionReducedMotionPolicy != value) {
        HazeLogger.d(TAG) { "interactionReducedMotionPolicy changed. Current: $_interactionReducedMotionPolicy. New: $value" }
        _interactionReducedMotionPolicy = value
        onInteractionConfigurationChanged()
      }
    }

  public fun hovered(block: GlassInteractionScope.() -> Unit) {
    setHovered(buildGlassInteractionResponse(block))
  }

  public fun focused(block: GlassInteractionScope.() -> Unit) {
    setFocused(buildGlassInteractionResponse(block))
  }

  public fun pressed(block: GlassInteractionScope.() -> Unit) {
    setPressed(buildGlassInteractionResponse(block))
  }

  private fun setHovered(response: GlassInteractionResponse) {
    if (hoveredOverride && hoveredSlot?.response == response) return
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    hoveredOverride = true
    hoveredSlot = GlassInteractionSlot(++nextInteractionRevision, response)
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  private fun setFocused(response: GlassInteractionResponse) {
    if (focusedOverride && focusedSlot?.response == response) return
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    focusedOverride = true
    focusedSlot = GlassInteractionSlot(++nextInteractionRevision, response)
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  private fun setPressed(response: GlassInteractionResponse) {
    if (pressedOverride && pressedSlot?.response == response) return
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    pressedOverride = true
    pressedSlot = GlassInteractionSlot(++nextInteractionRevision, response)
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  private fun onInteractionConfigurationChanged(previousRefractionMultiplier: Float? = null) {
    refreshInteractionSnapshots()
    markDirty(GlassDirtyFields.Interaction)
    if (
      previousRefractionMultiplier != null &&
      previousRefractionMultiplier != maximumInteractionRefractionMultiplier()
    ) {
      markDirty(GlassDirtyFields.InteractionLayerBounds)
    }
  }

  private fun refreshInteractionSnapshots() {
    val slots = GlassInteractionSlots(
      focused = if (focusedOverride) focusedSlot else styleFocusedSlot,
      hovered = if (hoveredOverride) hoveredSlot else styleHoveredSlot,
      pressed = if (pressedOverride) pressedSlot else stylePressedSlot,
    )
    if (slots != interactionSlotsSnapshot) {
      interactionSlotsSnapshot = slots
      currentInteractionTopology = slots.resolveInteractionTopology()
    }
  }

  internal fun updateStyleInteractionSlots() {
    if (
      styleHoveredSlot?.response == inheritedStyleValues.hoveredInteraction &&
      styleFocusedSlot?.response == inheritedStyleValues.focusedInteraction &&
      stylePressedSlot?.response == inheritedStyleValues.pressedInteraction
    ) {
      return
    }
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    styleHoveredSlot = updateStyleSlot(styleHoveredSlot, inheritedStyleValues.hoveredInteraction)
    styleFocusedSlot = updateStyleSlot(styleFocusedSlot, inheritedStyleValues.focusedInteraction)
    stylePressedSlot = updateStyleSlot(stylePressedSlot, inheritedStyleValues.pressedInteraction)
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  private fun updateStyleSlot(
    previous: GlassInteractionSlot?,
    response: GlassInteractionResponse?,
  ): GlassInteractionSlot? = when {
    previous?.response == response -> previous
    response == null -> null
    else -> GlassInteractionSlot(++nextInteractionRevision, response)
  }

  private fun maximumInteractionRefractionMultiplier(): Float =
    currentInteractionTopology.maxRefractionMultiplier

  internal var _optics: GlassOptics? = null

  /**
   * Complete optical configuration for this effect.
   *
   * A direct value takes precedence over [style], [LocalGlassStyle], and [GlassDefaults].
   */
  internal var optics: GlassOptics
    get() = _optics ?: inheritedStyleValues.optics
    set(value) {
      if (value != _optics) {
        HazeLogger.d(TAG) { "optics changed. Current: $_optics. New: $value" }
        _optics = value
        markDirty(GlassDirtyFields.Optics)
      }
    }

  /**
   * Intensity of specular highlights, in the range `0f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.specularIntensity] value set in [style], if specified.
   *  - [GlassStyleScope.specularIntensity] value set in [LocalGlassStyle], if specified.
   */
  internal var _specularIntensity: Float = Float.NaN
  internal var specularIntensity: Float
    get() = _specularIntensity
      .takeOrElse { inheritedStyleValues.specularIntensity }
    set(value) {
      val normalized = value.coerceIn(0f, 1f)
      if (!_specularIntensity.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "specularIntensity changed. Current: $_specularIntensity. New: $value" }
        _specularIntensity = normalized
        markDirty(GlassDirtyFields.SpecularIntensity)
      }
    }

  /**
   * Strength of ambient lighting response and Fresnel accent.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.ambientResponse] value set in [style], if specified.
   *  - [GlassStyleScope.ambientResponse] value set in [LocalGlassStyle], if specified.
   */
  internal var _ambientResponse: Float = Float.NaN
  internal var ambientResponse: Float
    get() = _ambientResponse
      .takeOrElse { inheritedStyleValues.ambientResponse }
    set(value) {
      val normalized = value.coerceIn(0f, 1f)
      if (!_ambientResponse.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "ambientResponse changed. Current: $_ambientResponse. New: $value" }
        _ambientResponse = normalized
        markDirty(GlassDirtyFields.AmbientResponse)
      }
    }

  /**
   * Glass tint applied to the refracted content.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.tint] value set in [style], if specified.
   *  - [GlassStyleScope.tint] value set in [LocalGlassStyle], if specified.
   */
  internal var _tint: Color = Color.Unspecified
  internal var tint: Color
    get() = _tint
      .takeOrElse { inheritedStyleValues.tint }
    set(value) {
      if (_tint != value) {
        HazeLogger.d(TAG) { "tint changed. Current: $_tint. New: $value" }
        _tint = value
        markDirty(GlassDirtyFields.Tint)
      }
    }

  /**
   * Softening distance for glass edges.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.edgeSoftness] value set in [style], if specified.
   *  - [GlassStyleScope.edgeSoftness] value set in [LocalGlassStyle], if specified.
   */
  internal var _edgeSoftness: Dp = Dp.Unspecified
  internal var edgeSoftness: Dp
    get() = _edgeSoftness
      .takeOrElse { inheritedStyleValues.edgeSoftness }
    set(value) {
      if (_edgeSoftness != value) {
        HazeLogger.d(TAG) { "edgeSoftness changed. Current: $_edgeSoftness. New: $value" }
        _edgeSoftness = value
        markDirty(GlassDirtyFields.EdgeSoftness)
      }
    }

  /**
   * Position of the virtual light source. When unspecified, the center of the layer is used.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.lightPosition] value set in [style], if specified.
   *  - [GlassStyleScope.lightPosition] value set in [LocalGlassStyle], if specified.
   *
   * If no value is specified through any of the above, the delegate falls back to the
   * center of the layer at draw time.
   */
  internal var _lightPosition: Offset = Offset.Unspecified
  internal var lightPosition: Offset
    get() = _lightPosition
      .takeOrElse { inheritedStyleValues.lightPosition }
    set(value) {
      if (_lightPosition != value) {
        HazeLogger.d(TAG) { "lightPosition changed. Current: $_lightPosition. New: $value" }
        _lightPosition = value
        markDirty(GlassDirtyFields.LightPosition)
      }
    }

  /**
   * Strength of chromatic aberration, in the range `0f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.chromaticAberrationStrength] value set in [style], if specified.
   *  - [GlassStyleScope.chromaticAberrationStrength] value set in [LocalGlassStyle], if specified.
   */
  internal var _chromaticAberrationStrength: Float = Float.NaN
  internal var chromaticAberrationStrength: Float
    get() = _chromaticAberrationStrength
      .takeOrElse { inheritedStyleValues.chromaticAberrationStrength }
    set(value) {
      val normalized = value.coerceIn(0f, 1f)
      if (!_chromaticAberrationStrength.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) {
          "chromaticAberrationStrength changed. Current: $_chromaticAberrationStrength. New: $value"
        }
        _chromaticAberrationStrength = normalized
        markDirty(GlassDirtyFields.ChromaticAberration)
      }
    }

  /**
   * Surface cross-section profile used for the refraction bezel.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.surfaceProfile] value set in [style], if specified.
   *  - [GlassStyleScope.surfaceProfile] value set in [LocalGlassStyle], if specified.
   */
  internal var _surfaceProfile: SurfaceProfile? = null

  internal var surfaceProfile: SurfaceProfile
    get() = _surfaceProfile ?: inheritedStyleValues.surfaceProfile
    set(value) {
      if (value != _surfaceProfile) {
        HazeLogger.d(TAG) { "surfaceProfile changed. Current: $_surfaceProfile. New: $value" }
        _surfaceProfile = value
        markDirty(GlassDirtyFields.SurfaceProfile)
      }
    }

  /**
   * Quality mode for chromatic aberration (color dispersion).
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.chromaticAberrationMode] value set in [style], if specified.
   *  - [GlassStyleScope.chromaticAberrationMode] value set in [LocalGlassStyle], if specified.
   */
  internal var _chromaticAberrationMode: ChromaticAberrationMode? = null

  internal var chromaticAberrationMode: ChromaticAberrationMode
    get() = _chromaticAberrationMode ?: inheritedStyleValues.chromaticAberrationMode
    set(value) {
      if (value != _chromaticAberrationMode) {
        HazeLogger.d(TAG) { "chromaticAberrationMode changed. Current: $_chromaticAberrationMode. New: $value" }
        _chromaticAberrationMode = value
        markDirty(GlassDirtyFields.ChromaticAberrationMode)
      }
    }

  /**
   * Shape applied to the glass. Defaults to [RoundedCornerShape] with 16.dp corners.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.shape] value set in [style], if specified.
   *  - [GlassStyleScope.shape] value set in [LocalGlassStyle], if specified.
   */
  internal var _shape: RoundedCornerShape? = null

  internal var shape: RoundedCornerShape
    get() = _shape ?: inheritedStyleValues.shape
    set(value) {
      if (value != _shape) {
        HazeLogger.d(TAG) { "shape changed. Current: $_shape. New: $value" }
        _shape = value
        markDirty(GlassDirtyFields.Shape)
      }
    }

  /**
   * Opacity for the effect, in the range `0f..1f`.
   *
   * The base material is composited as one group. Rim and interaction lighting use the same
   * opacity in a separate foreground pass so that they remain above this node's content.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.alpha] value set in [style], if specified.
   *  - [GlassStyleScope.alpha] value set in [LocalGlassStyle], if specified.
   */
  internal var _alpha: Float = Float.NaN
  internal var alpha: Float
    get() = _alpha
      .takeOrElse { inheritedStyleValues.alpha }
    set(value) {
      val normalized = value.coerceIn(0f, 1f)
      if (!_alpha.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "alpha changed. Current: $_alpha. New: $value" }
        _alpha = normalized
        markDirty(GlassDirtyFields.Alpha)
      }
    }

  /**
   * Overall contrast adjustment, in the range `-1f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.contrast] value set in [style], if specified.
   *  - [GlassStyleScope.contrast] value set in [LocalGlassStyle], if specified.
   */
  internal var _contrast: Float = Float.NaN
  internal var contrast: Float
    get() = _contrast
      .takeOrElse { inheritedStyleValues.contrast }
    set(value) {
      val normalized = value.coerceIn(-1f, 1f)
      if (!_contrast.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "contrast changed. Current: $_contrast. New: $value" }
        _contrast = normalized
        markDirty(GlassDirtyFields.Contrast)
      }
    }

  /**
   * White point adjustment, in the range `-1f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.whitePoint] value set in [style], if specified.
   *  - [GlassStyleScope.whitePoint] value set in [LocalGlassStyle], if specified.
   */
  internal var _whitePoint: Float = Float.NaN
  internal var whitePoint: Float
    get() = _whitePoint
      .takeOrElse { inheritedStyleValues.whitePoint }
    set(value) {
      val normalized = value.coerceIn(-1f, 1f)
      if (!_whitePoint.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "whitePoint changed. Current: $_whitePoint. New: $value" }
        _whitePoint = normalized
        markDirty(GlassDirtyFields.WhitePoint)
      }
    }

  /**
   * Chroma multiplier for saturation control, in the range `0f..2f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.chromaMultiplier] value set in [style], if specified.
   *  - [GlassStyleScope.chromaMultiplier] value set in [LocalGlassStyle], if specified.
   */
  internal var _chromaMultiplier: Float = Float.NaN
  internal var chromaMultiplier: Float
    get() = _chromaMultiplier
      .takeOrElse { inheritedStyleValues.chromaMultiplier }
    set(value) {
      val normalized = value.coerceIn(0f, 2f)
      if (!_chromaMultiplier.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "chromaMultiplier changed. Current: $_chromaMultiplier. New: $value" }
        _chromaMultiplier = normalized
        markDirty(GlassDirtyFields.ChromaMultiplier)
      }
    }

  /**
   * Blend factor for content normals, in the range `0f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.contentNormalBlend] value set in [style], if specified.
   *  - [GlassStyleScope.contentNormalBlend] value set in [LocalGlassStyle], if specified.
   */
  internal var _contentNormalBlend: Float = Float.NaN
  internal var contentNormalBlend: Float
    get() = _contentNormalBlend
      .takeOrElse { inheritedStyleValues.contentNormalBlend }
    set(value) {
      val normalized = value.coerceIn(0f, 1f)
      if (!_contentNormalBlend.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "contentNormalBlend changed. Current: $_contentNormalBlend. New: $value" }
        _contentNormalBlend = normalized
        markDirty(GlassDirtyFields.ContentNormalBlend)
      }
    }

  /**
   * Exponent controlling specular highlight shape. A value of `0f` produces a full response.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.specularExponent] value set in [style], if specified.
   *  - [GlassStyleScope.specularExponent] value set in [LocalGlassStyle], if specified.
   */
  internal var _specularExponent: Float = Float.NaN
  internal var specularExponent: Float
    get() = _specularExponent
      .takeOrElse { inheritedStyleValues.specularExponent }
    set(value) {
      val normalized = value.coerceAtLeast(0f)
      if (!_specularExponent.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "specularExponent changed. Current: $_specularExponent. New: $value" }
        _specularExponent = normalized
        markDirty(GlassDirtyFields.SpecularExponent)
      }
    }

  /**
   * Exponent controlling Fresnel edge effect intensity. A value of `0f` produces a full response.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyleScope.fresnelExponent] value set in [style], if specified.
   *  - [GlassStyleScope.fresnelExponent] value set in [LocalGlassStyle], if specified.
   */
  internal var _fresnelExponent: Float = Float.NaN
  internal var fresnelExponent: Float
    get() = _fresnelExponent
      .takeOrElse { inheritedStyleValues.fresnelExponent }
    set(value) {
      val normalized = value.coerceAtLeast(0f)
      if (!_fresnelExponent.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "fresnelExponent changed. Current: $_fresnelExponent. New: $value" }
        _fresnelExponent = normalized
        markDirty(GlassDirtyFields.FresnelExponent)
      }
    }

  /**
   * Optional style container that can set multiple parameters at once.
   *
   * There are precedence rules to how each styling property is applied. The order of precedence
   * for each property are as follows:
   *
   *  - Property value set directly on this runtime state, if specified.
   *  - Value set here in [style], if specified.
   *  - Value set in the [LocalGlassStyle] composition local.
   */
  internal var style: GlassStyle = GlassStyle
    set(value) {
      if (field !== value) {
        HazeLogger.d(TAG) { "style changed. Current: $field. New: $value" }
        field = value
        updateInheritedStyleValues()
        markDirty(GlassDirtyFields.Style)
      }
    }

  internal var compositionLocalStyle: GlassStyle = GlassStyle
    set(value) {
      if (field !== value) {
        HazeLogger.d(TAG) { "LocalGlassStyle changed. Current: $field. New: $value" }
        field = value
      }
      updateInheritedStyleValues()
    }

  private var inheritedStyleValues: GlassStyleValues =
    resolveGlassStyleValues(compositionLocalStyle, style)

  private fun updateInheritedStyleValues() {
    val resolved = resolveGlassStyleValues(compositionLocalStyle, style)
    val previous = inheritedStyleValues
    inheritedStyleValues = resolved
    onStyleChanged(old = previous, new = resolved)
  }

  private fun markDirty(fields: Int) {
    onConfigurationChanged?.invoke(fields)
  }

  private fun onStyleChanged(old: GlassStyleValues, new: GlassStyleValues) {
    if (old.interactionLightRadiusFraction != new.interactionLightRadiusFraction) {
      markDirty(GlassDirtyFields.Interaction)
      markDirty(GlassDirtyFields.InteractionLayerBounds)
    }
    if (old.interactionPositionAnimationSpec != new.interactionPositionAnimationSpec) {
      markDirty(GlassDirtyFields.Interaction)
    }
    if (old.optics != new.optics) {
      markDirty(GlassDirtyFields.Optics)
    }
    if (old.specularIntensity != new.specularIntensity) {
      markDirty(GlassDirtyFields.SpecularIntensity)
    }
    if (old.ambientResponse != new.ambientResponse) {
      markDirty(GlassDirtyFields.AmbientResponse)
    }
    if (old.lightPosition != new.lightPosition) {
      markDirty(GlassDirtyFields.LightPosition)
    }
    if (old.specularExponent != new.specularExponent) {
      markDirty(GlassDirtyFields.SpecularExponent)
    }
    if (old.fresnelExponent != new.fresnelExponent) {
      markDirty(GlassDirtyFields.FresnelExponent)
    }
    if (old.tint != new.tint) {
      markDirty(GlassDirtyFields.Tint)
    }
    if (old.shape != new.shape) {
      markDirty(GlassDirtyFields.Shape)
    }
    if (old.alpha != new.alpha) {
      markDirty(GlassDirtyFields.Alpha)
    }
    if (old.contrast != new.contrast) {
      markDirty(GlassDirtyFields.Contrast)
    }
    if (old.whitePoint != new.whitePoint) {
      markDirty(GlassDirtyFields.WhitePoint)
    }
    if (old.chromaMultiplier != new.chromaMultiplier) {
      markDirty(GlassDirtyFields.ChromaMultiplier)
    }
    if (old.edgeSoftness != new.edgeSoftness) {
      markDirty(GlassDirtyFields.EdgeSoftness)
    }
    if (old.contentNormalBlend != new.contentNormalBlend) {
      markDirty(GlassDirtyFields.ContentNormalBlend)
    }
    if (old.surfaceProfile != new.surfaceProfile) {
      markDirty(GlassDirtyFields.SurfaceProfile)
    }
    if (old.chromaticAberrationStrength != new.chromaticAberrationStrength) {
      markDirty(GlassDirtyFields.ChromaticAberration)
    }
    if (old.chromaticAberrationMode != new.chromaticAberrationMode) {
      markDirty(GlassDirtyFields.ChromaticAberrationMode)
    }
  }

  internal companion object {
    const val TAG = "GlassRuntimeConfiguration"
  }
}
