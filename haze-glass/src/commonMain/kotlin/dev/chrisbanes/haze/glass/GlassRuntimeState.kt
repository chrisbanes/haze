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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
      focused = styleFocusedSlot,
      hovered = styleHoveredSlot,
      pressed = stylePressedSlot,
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

  internal val optics: GlassOptics get() = inheritedStyleValues.optics
  internal val specularIntensity: Float get() = inheritedStyleValues.specularIntensity
  internal val edgeShadow: Color get() = inheritedStyleValues.edgeShadow
  internal val ambientResponse: Float get() = inheritedStyleValues.ambientResponse
  internal val backgroundColor: Color get() = inheritedStyleValues.backgroundColor
  internal val tint: Color get() = inheritedStyleValues.tint
  internal val edgeSoftness: Dp get() = inheritedStyleValues.edgeSoftness
  internal val lightPosition: Alignment get() = inheritedStyleValues.lightPosition
  internal val chromaticAberrationStrength: Float get() = inheritedStyleValues.chromaticAberrationStrength
  internal val surfaceProfile: SurfaceProfile get() = inheritedStyleValues.surfaceProfile
  internal val chromaticAberrationMode: ChromaticAberrationMode get() = inheritedStyleValues.chromaticAberrationMode
  internal val shape: RoundedCornerShape get() = inheritedStyleValues.shape
  internal val alpha: Float get() = inheritedStyleValues.alpha
  internal val contrast: Float get() = inheritedStyleValues.contrast
  internal val whitePoint: Float get() = inheritedStyleValues.whitePoint
  internal val chromaMultiplier: Float get() = inheritedStyleValues.chromaMultiplier
  internal val contentNormalBlend: Float get() = inheritedStyleValues.contentNormalBlend
  internal val specularExponent: Float get() = inheritedStyleValues.specularExponent
  internal val fresnelExponent: Float get() = inheritedStyleValues.fresnelExponent

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

  internal var accessibilitySettings: GlassAccessibilitySettings = GlassAccessibilitySettings()
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "Glass accessibility changed. Current: $field. New: $value" }
        field = value
        markDirty(GlassDirtyFields.Accessibility)
      }
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
    if (old.edgeShadow != new.edgeShadow) {
      markDirty(GlassDirtyFields.EdgeShadow)
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
    if (old.backgroundColor != new.backgroundColor) {
      markDirty(GlassDirtyFields.BackgroundColor)
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
