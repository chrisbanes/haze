// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.InteractiveVisualEffect
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.RetainedOutputVisualEffect
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.VisualEffectRendererFactory

/**
 * Shareable Glass compatibility configuration.
 *
 * Each attached `hazeEffect` node materializes and owns an internal renderer. This object stores
 * only caller configuration and may be reused by multiple nodes.
 */
@ExperimentalHazeApi
@Stable
@OptIn(InternalHazeApi::class)
@Suppress("ktlint:standard:property-naming")
public class GlassVisualEffect() :
  VisualEffect,
  InteractiveVisualEffect,
  RetainedOutputVisualEffect,
  VisualEffectRendererFactory,
  GlassStyleConfiguration {
  /** Creates a new [GlassVisualEffect] copying all properties from [other]. */
  public constructor(other: GlassVisualEffect) : this() {
    copyConfigurationFrom(other)
  }

  internal fun copyConfigurationFrom(other: GlassVisualEffect) {
    _optics = other._optics
    _specularIntensity = other._specularIntensity
    _ambientResponse = other._ambientResponse
    _tint = other._tint
    _edgeSoftness = other._edgeSoftness
    _lightPosition = other._lightPosition
    _chromaticAberrationStrength = other._chromaticAberrationStrength
    _surfaceProfile = other._surfaceProfile
    _chromaticAberrationMode = other._chromaticAberrationMode
    _shape = other._shape
    _alpha = other._alpha
    _contrast = other._contrast
    _whitePoint = other._whitePoint
    _chromaMultiplier = other._chromaMultiplier
    _contentNormalBlend = other._contentNormalBlend
    _specularExponent = other._specularExponent
    _fresnelExponent = other._fresnelExponent
    compositionLocalStyle = other.compositionLocalStyle
    style = other.style
    nextInteractionRevision = other.nextInteractionRevision
    hoveredSlot = other.hoveredSlot
    focusedSlot = other.focusedSlot
    pressedSlot = other.pressedSlot
    _interactionSource = other._interactionSource
    _interactionLightRadiusFraction = other._interactionLightRadiusFraction
    _interactionTransformTarget = other._interactionTransformTarget
    _interactionTransformPivot = other._interactionTransformPivot
    _interactionPositionAnimationSpec = other._interactionPositionAnimationSpec
    _interactionReducedMotionPolicy = other._interactionReducedMotionPolicy
    runtimeEffectFactory = other.runtimeEffectFactory
    refreshInteractionSnapshots()
  }

  internal var configurationRevision: Int by mutableIntStateOf(0)
    private set

  private val configurationFieldVersions = IntArray(Int.SIZE_BITS)
  internal var onConfigurationChanged: ((Int) -> Unit)? = null
  internal var trackConfigurationVersions: Boolean = true

  internal fun configurationFieldVersions(): IntArray = configurationFieldVersions.copyOf()

  internal fun synchronizeConfigurationFrom(
    other: GlassVisualEffect,
    changedFields: Int,
  ) {
    val callback = onConfigurationChanged
    onConfigurationChanged = null
    try {
      if (changedFields and GlassDirtyFields.Optics != 0) _optics = other._optics
      if (changedFields and GlassDirtyFields.SpecularIntensity != 0) {
        _specularIntensity = other._specularIntensity
      }
      if (changedFields and GlassDirtyFields.AmbientResponse != 0) {
        _ambientResponse = other._ambientResponse
      }
      if (changedFields and GlassDirtyFields.Tint != 0) _tint = other._tint
      if (changedFields and GlassDirtyFields.EdgeSoftness != 0) {
        _edgeSoftness = other._edgeSoftness
      }
      if (changedFields and GlassDirtyFields.LightPosition != 0) {
        _lightPosition = other._lightPosition
      }
      if (changedFields and GlassDirtyFields.ChromaticAberration != 0) {
        _chromaticAberrationStrength = other._chromaticAberrationStrength
      }
      if (changedFields and GlassDirtyFields.SurfaceProfile != 0) {
        _surfaceProfile = other._surfaceProfile
      }
      if (changedFields and GlassDirtyFields.ChromaticAberrationMode != 0) {
        _chromaticAberrationMode = other._chromaticAberrationMode
      }
      if (changedFields and GlassDirtyFields.Shape != 0) _shape = other._shape
      if (changedFields and GlassDirtyFields.Alpha != 0) _alpha = other._alpha
      if (changedFields and GlassDirtyFields.Contrast != 0) _contrast = other._contrast
      if (changedFields and GlassDirtyFields.WhitePoint != 0) _whitePoint = other._whitePoint
      if (changedFields and GlassDirtyFields.ChromaMultiplier != 0) {
        _chromaMultiplier = other._chromaMultiplier
      }
      if (changedFields and GlassDirtyFields.ContentNormalBlend != 0) {
        _contentNormalBlend = other._contentNormalBlend
      }
      if (changedFields and GlassDirtyFields.SpecularExponent != 0) {
        _specularExponent = other._specularExponent
      }
      if (changedFields and GlassDirtyFields.FresnelExponent != 0) {
        _fresnelExponent = other._fresnelExponent
      }
      if (changedFields and GlassDirtyFields.Style != 0) style = other.style
      if (changedFields and GlassDirtyFields.Interaction != 0) {
        nextInteractionRevision = other.nextInteractionRevision
        hoveredSlot = other.hoveredSlot
        focusedSlot = other.focusedSlot
        pressedSlot = other.pressedSlot
        _interactionSource = other._interactionSource
        _interactionLightRadiusFraction = other._interactionLightRadiusFraction
        _interactionTransformTarget = other._interactionTransformTarget
        _interactionTransformPivot = other._interactionTransformPivot
        _interactionPositionAnimationSpec = other._interactionPositionAnimationSpec
        _interactionReducedMotionPolicy = other._interactionReducedMotionPolicy
        refreshInteractionSnapshots()
      }
      if (changedFields and GlassDirtyFields.RuntimeEffectFactory != 0) {
        runtimeEffectFactory = other.runtimeEffectFactory
      }
    } finally {
      onConfigurationChanged = callback
    }
  }

  internal var dirtyTracker: Bitmask = Bitmask()
    private set

  internal fun resetDirtyTracker() {
    dirtyTracker = Bitmask()
  }

  internal var runtimeEffectFactory: GlassRuntimeEffectFactory = PlatformGlassRuntimeEffectFactory
    set(value) {
      if (field !== value) {
        field = value
        markDirty(GlassDirtyFields.RuntimeEffectFactory)
      }
    }

  internal val rendererCacheKey: Any by lazy(LazyThreadSafetyMode.NONE) { Any() }

  override fun createRenderer(): VisualEffect = GlassRendererCache.acquire(this)

  override fun DrawScope.draw(context: VisualEffectContext): Unit = Unit

  override fun onPointerEvent(event: PointerEvent, context: VisualEffectContext): Unit = Unit

  override fun onCancelPointerInput(context: VisualEffectContext): Unit = Unit

  override fun canDrawRetainedOutput(context: VisualEffectContext): Boolean = false

  override fun clearRetainedOutput(): Unit = Unit

  override fun shouldClipToNodeBounds(): Boolean =
    edgeSoftness > 0.dp || !shape.hasZeroCornerRadii()

  override fun shouldPreferClipToAreaBounds(): Boolean = !shouldClipToNodeBounds()

  override fun calculateLayerBounds(rect: Rect, density: Density): Rect {
    val resolvedStyle = resolveGlassStyle(
      effect = this,
      materialSizePx = rect.size,
      density = density,
      layoutDirection = LayoutDirection.Ltr,
    )
    val resolved = resolvedStyle.resolvedOptics
    val paddingPx = calculateGlassSamplePaddingPx(
      blurRadiusPx = if (resolved.depth <= 0f) 0f else resolved.blurRadiusPx,
      refractionScale = resolved.refractionScalePx,
      refractionStrength = (
        resolved.refractionStrength * maximumInteractionRefractionMultiplier()
        ).coerceIn(0f, 1f),
      chromaticAberrationStrength = resolvedStyle.chromaticAberrationStrength,
      edgeSoftnessPx = resolvedStyle.edgeSoftnessPx,
      foregroundOutsetPx = 0f,
    )
    return rect.inflate(paddingPx)
  }

  internal var nextInteractionRevision: Long = 0L

  internal var hoveredSlot: GlassInteractionSlot? by mutableStateOf(null)
    private set

  internal var focusedSlot: GlassInteractionSlot? by mutableStateOf(null)
    private set

  internal var pressedSlot: GlassInteractionSlot? by mutableStateOf(null)
    private set

  private var interactionSlotsSnapshot: GlassInteractionSlots = GlassInteractionSlots()
  private var currentInteractionTopology = interactionSlotsSnapshot.resolveInteractionTopology()

  internal val resolvedInteractionSlots: GlassInteractionSlots
    get() = interactionSlotsSnapshot

  internal val resolvedInteractionTopology: GlassInteractionTopology
    get() = currentInteractionTopology

  internal fun resolveInputScaleFactor(scale: HazeInputScale): Float = when {
    scale === HazeInputScale.Auto -> 0.75f
    scale is HazeInputScale.Fixed -> scale.scale
    else -> 1f
  }

  private class InteractionSlotTransaction(effect: GlassVisualEffect) {
    var hovered: GlassInteractionResponse? = effect.hoveredSlot?.response
    var focused: GlassInteractionResponse? = effect.focusedSlot?.response
    var pressed: GlassInteractionResponse? = effect.pressedSlot?.response
  }

  private var interactionSlotTransaction: InteractionSlotTransaction? = null

  override val observesPointerEvents: Boolean
    get() = hoveredSlot != null || pressedSlot != null

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

  internal var _interactionLightRadiusFraction: Float by mutableStateOf(
    GlassDefaults.interactionLightRadiusFraction,
  )

  public var interactionLightRadiusFraction: Float
    get() = _interactionLightRadiusFraction
    set(value) {
      require(value.isFinite() && value in 0f..2f) {
        "interactionLightRadiusFraction must be finite and in range"
      }
      if (_interactionLightRadiusFraction != value) {
        HazeLogger.d(TAG) { "interactionLightRadiusFraction changed. Current: $_interactionLightRadiusFraction. New: $value" }
        _interactionLightRadiusFraction = value
        onInteractionConfigurationChanged()
      }
    }

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

  internal var _interactionPositionAnimationSpec: FiniteAnimationSpec<Offset> by mutableStateOf(
    GlassDefaults.positionAnimationSpec,
  )

  public var interactionPositionAnimationSpec: FiniteAnimationSpec<Offset>
    get() = _interactionPositionAnimationSpec
    set(value) {
      if (_interactionPositionAnimationSpec != value) {
        HazeLogger.d(TAG) { "interactionPositionAnimationSpec changed. Current: $_interactionPositionAnimationSpec. New: $value" }
        _interactionPositionAnimationSpec = value
        onInteractionConfigurationChanged()
      }
    }

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

  public fun hovered() {
    setHovered(defaultHoverResponse())
  }

  public fun hovered(block: GlassInteractionScope.() -> Unit) {
    setHovered(buildGlassInteractionResponse(block))
  }

  public fun focused() {
    setFocused(defaultFocusResponse())
  }

  public fun focused(block: GlassInteractionScope.() -> Unit) {
    setFocused(buildGlassInteractionResponse(block))
  }

  public fun pressed() {
    setPressed(defaultPressResponse())
  }

  public fun pressed(block: GlassInteractionScope.() -> Unit) {
    setPressed(buildGlassInteractionResponse(block))
  }

  public fun interactable() {
    hovered()
    focused()
    pressed()
  }

  public fun clearHovered() {
    interactionSlotTransaction?.let {
      it.hovered = null
      return
    }
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    hoveredSlot = null
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  public fun clearFocused() {
    interactionSlotTransaction?.let {
      it.focused = null
      return
    }
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    focusedSlot = null
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  public fun clearPressed() {
    interactionSlotTransaction?.let {
      it.pressed = null
      return
    }
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    pressedSlot = null
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  public fun clearInteractions() {
    interactionSlotTransaction?.let {
      it.hovered = null
      it.focused = null
      it.pressed = null
      return
    }
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    hoveredSlot = null
    focusedSlot = null
    pressedSlot = null
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  private fun setHovered(response: GlassInteractionResponse) {
    interactionSlotTransaction?.let {
      it.hovered = response
      return
    }
    if (hoveredSlot?.response == response) return
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    hoveredSlot = GlassInteractionSlot(++nextInteractionRevision, response)
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  private fun setFocused(response: GlassInteractionResponse) {
    interactionSlotTransaction?.let {
      it.focused = response
      return
    }
    if (focusedSlot?.response == response) return
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    focusedSlot = GlassInteractionSlot(++nextInteractionRevision, response)
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  private fun setPressed(response: GlassInteractionResponse) {
    interactionSlotTransaction?.let {
      it.pressed = response
      return
    }
    if (pressedSlot?.response == response) return
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
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
      focused = focusedSlot,
      hovered = hoveredSlot,
      pressed = pressedSlot,
    )
    if (slots != interactionSlotsSnapshot) {
      interactionSlotsSnapshot = slots
      currentInteractionTopology = slots.resolveInteractionTopology()
    }
  }

  @PublishedApi
  internal fun beginInteractionSlotTransaction(): Boolean {
    if (interactionSlotTransaction != null) return false
    interactionSlotTransaction = InteractionSlotTransaction(this)
    return true
  }

  @PublishedApi
  internal fun commitInteractionSlotTransaction(ownsTransaction: Boolean) {
    if (!ownsTransaction) return
    val transaction = checkNotNull(interactionSlotTransaction)
    interactionSlotTransaction = null
    commitInteractionSlots(transaction)
  }

  @PublishedApi
  internal fun rollbackInteractionSlotTransaction(ownsTransaction: Boolean) {
    if (!ownsTransaction) return
    interactionSlotTransaction = null
  }

  private fun commitInteractionSlots(transaction: InteractionSlotTransaction) {
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    var changed = false
    if (hoveredSlot?.response != transaction.hovered) {
      hoveredSlot = transaction.hovered?.let { GlassInteractionSlot(++nextInteractionRevision, it) }
      changed = true
    }
    if (focusedSlot?.response != transaction.focused) {
      focusedSlot = transaction.focused?.let { GlassInteractionSlot(++nextInteractionRevision, it) }
      changed = true
    }
    if (pressedSlot?.response != transaction.pressed) {
      pressedSlot = transaction.pressed?.let { GlassInteractionSlot(++nextInteractionRevision, it) }
      changed = true
    }
    if (changed) {
      onInteractionConfigurationChanged(previousRefractionMultiplier)
    }
  }

  private fun maximumInteractionRefractionMultiplier(): Float =
    currentInteractionTopology.maxRefractionMultiplier

  internal var _optics: GlassOptics? = null

  /**
   * Complete optical configuration for this effect.
   *
   * A direct value takes precedence over [style], [LocalGlassStyle], and [GlassDefaults]. Call
   * [clearOpticsOverride] to restore the next complete inherited value.
   */
  override var optics: GlassOptics
    get() = _optics ?: inheritedStyleValues.optics
    set(value) {
      if (value != _optics) {
        HazeLogger.d(TAG) { "optics changed. Current: $_optics. New: $value" }
        _optics = value
        markDirty(GlassDirtyFields.Optics)
      }
    }

  /**
   * Clears the direct [optics] override and restores inherited values from [style] and
   * [LocalGlassStyle].
   */
  public fun clearOpticsOverride() {
    if (_optics != null) {
      HazeLogger.d(TAG) { "optics override cleared. Current: $_optics" }
      _optics = null
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
  override var specularIntensity: Float
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
  override var ambientResponse: Float
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
  override var tint: Color
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
  override var edgeSoftness: Dp
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
  override var lightPosition: Offset
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
  override var chromaticAberrationStrength: Float
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

  override var surfaceProfile: SurfaceProfile
    get() = _surfaceProfile ?: inheritedStyleValues.surfaceProfile
    set(value) {
      if (value != _surfaceProfile) {
        HazeLogger.d(TAG) { "surfaceProfile changed. Current: $_surfaceProfile. New: $value" }
        _surfaceProfile = value
        markDirty(GlassDirtyFields.SurfaceProfile)
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

  override var chromaticAberrationMode: ChromaticAberrationMode
    get() = _chromaticAberrationMode ?: inheritedStyleValues.chromaticAberrationMode
    set(value) {
      if (value != _chromaticAberrationMode) {
        HazeLogger.d(TAG) { "chromaticAberrationMode changed. Current: $_chromaticAberrationMode. New: $value" }
        _chromaticAberrationMode = value
        markDirty(GlassDirtyFields.ChromaticAberrationMode)
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

  override var shape: RoundedCornerShape
    get() = _shape ?: inheritedStyleValues.shape
    set(value) {
      if (value != _shape) {
        HazeLogger.d(TAG) { "shape changed. Current: $_shape. New: $value" }
        _shape = value
        markDirty(GlassDirtyFields.Shape)
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
  override var alpha: Float
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
  override var contrast: Float
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
  override var whitePoint: Float
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
  override var chromaMultiplier: Float
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
  override var contentNormalBlend: Float
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
  override var specularExponent: Float
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
  override var fresnelExponent: Float
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
   *  - Property value set directly on this [GlassVisualEffect], if specified.
   *  - Value set here in [style], if specified.
   *  - Value set in the [LocalGlassStyle] composition local.
   */
  override var style: GlassStyle = GlassStyle
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
    if (trackConfigurationVersions) {
      configurationRevision++
      dirtyTracker += fields
      configurationFieldVersions.indices.forEach { index ->
        val field = 1 shl index
        if (fields and field != 0) {
          configurationFieldVersions[index]++
        }
      }
    }
    onConfigurationChanged?.invoke(fields)
  }

  private fun onStyleChanged(old: GlassStyleValues, new: GlassStyleValues) {
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
    const val TAG = "GlassVisualEffect"
  }
}
