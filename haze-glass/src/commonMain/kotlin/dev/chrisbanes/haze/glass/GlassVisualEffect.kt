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
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext

/**
 * A [VisualEffect] implementation that renders a translucent refractive glass material.
 * refraction, depth layering, specular highlights, and soft tinted glass.
 */
@ExperimentalHazeApi
@Stable
@OptIn(InternalHazeApi::class)
public class GlassVisualEffect() : VisualEffect, RetainedOutputVisualEffect, InteractiveVisualEffect {

  /** Creates a new [GlassVisualEffect] copying all properties from [other]. */
  public constructor(other: GlassVisualEffect) : this() {
    _optics = other._optics
    specularIntensity = other.specularIntensity
    ambientResponse = other.ambientResponse
    tint = other.tint
    edgeSoftness = other.edgeSoftness
    lightPosition = other.lightPosition
    chromaticAberrationStrength = other.chromaticAberrationStrength
    _surfaceProfile = other._surfaceProfile
    _chromaticAberrationMode = other._chromaticAberrationMode
    _shape = other._shape
    alpha = other.alpha
    contrast = other.contrast
    whitePoint = other.whitePoint
    chromaMultiplier = other.chromaMultiplier
    contentNormalBlend = other.contentNormalBlend
    specularExponent = other.specularExponent
    fresnelExponent = other.fresnelExponent
    compositionLocalStyle = other.compositionLocalStyle
    style = other.style
    nextInteractionRevision = other.nextInteractionRevision
    hoveredSlot = other.hoveredSlot
    focusedSlot = other.focusedSlot
    pressedSlot = other.pressedSlot
    interactionSource = other.interactionSource
    interactionLightRadiusFraction = other.interactionLightRadiusFraction
    interactionTransformTarget = other.interactionTransformTarget
    interactionTransformPivot = other.interactionTransformPivot
    interactionPositionAnimationSpec = other.interactionPositionAnimationSpec
    interactionReducedMotionPolicy = other.interactionReducedMotionPolicy
  }

  private var isAttached: Boolean = false

  private var attachedContext: VisualEffectContext? = null

  private var interactionController: GlassInteractionController? = null

  internal val interactionControllerForTest: GlassInteractionController?
    get() = interactionController

  internal val attachedContextForTest: VisualEffectContext?
    get() = attachedContext

  internal val currentInteractionState: GlassInteractionRenderState
    get() = interactionController?.renderState ?: GlassInteractionRenderState(Offset.Zero)

  internal val currentInteractionSignals: GlassInteractionSignals
    get() = interactionController?.currentSignals ?: GlassInteractionSignals()

  private var needsDelegateSelection: Boolean = true

  internal var dirtyTracker: Bitmask by mutableStateOf(Bitmask())
    private set

  private var nextInteractionRevision: Long = 0L

  internal var hoveredSlot: GlassInteractionSlot? by mutableStateOf(null)
    private set

  internal var focusedSlot: GlassInteractionSlot? by mutableStateOf(null)
    private set

  internal var pressedSlot: GlassInteractionSlot? by mutableStateOf(null)
    private set

  internal val interactionSlots: GlassInteractionSlots
    get() = GlassInteractionSlots(
      focused = focusedSlot,
      hovered = hoveredSlot,
      pressed = pressedSlot,
    )

  override val observesPointerEvents: Boolean
    get() = hoveredSlot != null || pressedSlot != null

  private var _interactionSource: InteractionSource? by mutableStateOf(null)

  public var interactionSource: InteractionSource?
    get() = _interactionSource
    set(value) {
      if (_interactionSource != value) {
        HazeLogger.d(TAG) { "interactionSource changed. Current: $_interactionSource. New: $value" }
        _interactionSource = value
        onInteractionConfigurationChanged()
      }
    }

  private var _interactionLightRadiusFraction: Float by mutableStateOf(
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

  private var _interactionTransformTarget: GlassTransformTarget by mutableStateOf(
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

  private var _interactionTransformPivot: GlassTransformPivot by mutableStateOf(
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

  private var _interactionPositionAnimationSpec: FiniteAnimationSpec<Offset> by mutableStateOf(
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

  private var _interactionReducedMotionPolicy: GlassReducedMotionPolicy by mutableStateOf(
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

  private var interactionConfigurationVersion: Int by mutableIntStateOf(0)

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
    hoveredSlot = null
    onInteractionConfigurationChanged()
  }

  public fun clearFocused() {
    focusedSlot = null
    onInteractionConfigurationChanged()
  }

  public fun clearPressed() {
    pressedSlot = null
    onInteractionConfigurationChanged()
  }

  public fun clearInteractions() {
    hoveredSlot = null
    focusedSlot = null
    pressedSlot = null
    onInteractionConfigurationChanged()
  }

  private fun setHovered(response: GlassInteractionResponse) {
    hoveredSlot = GlassInteractionSlot(++nextInteractionRevision, response)
    onInteractionConfigurationChanged()
  }

  private fun setFocused(response: GlassInteractionResponse) {
    focusedSlot = GlassInteractionSlot(++nextInteractionRevision, response)
    onInteractionConfigurationChanged()
  }

  private fun setPressed(response: GlassInteractionResponse) {
    pressedSlot = GlassInteractionSlot(++nextInteractionRevision, response)
    onInteractionConfigurationChanged()
  }

  private fun onInteractionConfigurationChanged() {
    interactionConfigurationVersion++
    if (hoveredSlot == null && focusedSlot == null && pressedSlot == null) {
      attachedContext?.let(::syncInteractionController)
    }
  }

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
      attachedContext = context
      syncInteractionController(context)
      delegate.attach()
    }
  }

  override fun detach(context: VisualEffectContext) {
    if (isAttached) {
      interactionController?.dispose()
      interactionController = null
      attachedContext = null
      isAttached = false
      delegate.detach()
    }
  }

  override fun update(context: VisualEffectContext) {
    interactionConfigurationVersion
    compositionLocalStyle = context.currentValueOf(LocalGlassStyle)
    syncInteractionController(context)

    if (dirtyTracker.any(GlassDirtyFields.LayerBoundsFlags)) {
      context.invalidateLayerBounds()
    }
    if (dirtyTracker.any(GlassDirtyFields.InvalidateFlags)) {
      needsDelegateSelection = true
      context.invalidateDraw()
    }
  }

  override fun onPointerEvent(event: PointerEvent, context: VisualEffectContext) {
    interactionController?.onPointerEvent(event, context.size)
  }

  override fun onCancelPointerInput(context: VisualEffectContext) {
    interactionController?.cancelPointerInput(context.size)
  }

  internal fun setPressedForTest(position: Offset, pressed: Boolean = true) {
    val context = attachedContext ?: return
    interactionController?.setRawPressedForTest(pressed, position, context.size)
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

  internal fun controllerConfiguration(
    systemMotionScale: Float,
  ): GlassInteractionControllerConfiguration {
    val (reduced, forceFull) = reducedMotion(
      policy = interactionReducedMotionPolicy,
      systemScale = systemMotionScale,
    )
    return GlassInteractionControllerConfiguration(
      slots = interactionSlots,
      positionAnimationSpec = interactionPositionAnimationSpec,
      reducedMotion = reduced,
      forceFullMotion = forceFull,
    )
  }

  internal fun interactionRenderState(context: VisualEffectContext): GlassInteractionRenderState {
    return interactionController?.renderState ?: GlassInteractionRenderState(
      position = context.size.center,
    )
  }

  private fun syncInteractionController(context: VisualEffectContext) {
    if (hoveredSlot == null && focusedSlot == null && pressedSlot == null) {
      val controller = interactionController ?: return
      controller.dispose()
      interactionController = null
      context.invalidateDraw()
      return
    }
    val controller = interactionController ?: GlassInteractionController(context).also {
      interactionController = it
    }
    controller.updateConfiguration(controllerConfiguration(systemMotionScale(context)))
    controller.updateInteractionSource(interactionSource, context.size)
  }

  private fun systemMotionScale(context: VisualEffectContext): Float {
    return context.coroutineScope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
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
    val validGeometry =
      rect.width.isFinite() && rect.height.isFinite() && rect.width > 0f && rect.height > 0f
    val cornerRadii = if (validGeometry) {
      shape.toCornerRadiiPx(rect.size, density, LayoutDirection.Ltr)
    } else {
      CornerRadii.zero
    }
    val resolved = resolveGlassOptics(
      optics = optics,
      materialSizePx = rect.size,
      density = density,
      cornerRadiiPx = cornerRadii,
    )
    val paddingPx = calculateGlassSamplePaddingPx(
      blurRadiusPx = resolved.blurRadiusPx,
      refractionScale = resolved.refractionScalePx,
      refractionStrength = resolved.refractionStrength,
      chromaticAberrationStrength = chromaticAberrationStrength,
      edgeSoftnessPx = with(density) { edgeSoftness.toPx() },
      foregroundOutsetPx = 0f,
    )
    return rect.inflate(paddingPx)
  }

  override fun shouldPreferClipToAreaBounds(): Boolean = edgeSoftness <= 0.dp && shape.hasZeroCornerRadii()

  private var _optics: GlassOptics? = null

  /**
   * Complete optical configuration for this effect.
   *
   * A direct value takes precedence over [style], [LocalGlassStyle], and [GlassDefaults]. Call
   * [clearOpticsOverride] to restore the next complete inherited value.
   */
  public var optics: GlassOptics
    get() = _optics ?: style.optics ?: compositionLocalStyle.optics ?: GlassDefaults.optics
    set(value) {
      if (value != _optics) {
        HazeLogger.d(TAG) { "optics changed. Current: $_optics. New: $value" }
        _optics = value
        dirtyTracker += GlassDirtyFields.Optics
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
      dirtyTracker += GlassDirtyFields.Optics
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

  internal fun resetDirtyTracker() {
    dirtyTracker = Bitmask()
  }

  private fun DrawScope.selectDelegateForDraw(context: VisualEffectContext) {
    if (needsDelegateSelection) {
      delegate = updateDelegate(context, this)
      needsDelegateSelection = false
    }
  }

  private fun onStyleChanged(old: GlassStyle, new: GlassStyle) {
    if (old.optics != new.optics) {
      dirtyTracker += GlassDirtyFields.Optics
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

private fun reducedMotion(
  policy: GlassReducedMotionPolicy,
  systemScale: Float,
): Pair<Boolean, Boolean> = when (policy) {
  GlassReducedMotionPolicy.System -> (systemScale == 0f) to false
  GlassReducedMotionPolicy.Reduced -> true to false
  GlassReducedMotionPolicy.Full -> false to true
}
