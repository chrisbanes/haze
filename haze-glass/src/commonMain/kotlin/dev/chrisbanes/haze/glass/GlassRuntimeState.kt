// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.InternalHazeApi

/**
 * Mutable style and interaction state owned by one [GlassRuntimeEffect].
 *
 * Fields are plain, not snapshot state: changes go through markDirty, so dirtyTrackerSignal is the
 * only observed signal.
 */
@Stable
@OptIn(InternalHazeApi::class)
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
  // Deliberately not snapshot state: they are written inside the observed update() that reads them.
  private var styleHoveredSlot: GlassInteractionSlot? = null
  private var styleFocusedSlot: GlassInteractionSlot? = null
  private var stylePressedSlot: GlassInteractionSlot? = null

  private var interactionSlotsSnapshot: GlassInteractionSlots = GlassInteractionSlots()
  private var currentInteractionTopology = interactionSlotsSnapshot.resolveInteractionTopology()

  internal val resolvedInteractionSlots: GlassInteractionSlots
    get() = interactionSlotsSnapshot

  internal val resolvedInteractionTopology: GlassInteractionTopology
    get() = currentInteractionTopology

  internal open val observesPointerEvents: Boolean
    get() = interactionSlotsSnapshot.hovered != null || interactionSlotsSnapshot.pressed != null

  public var interactionSource: InteractionSource? = null
    set(value) {
      if (field !== value) {
        HazeLogger.d(TAG) { "interactionSource changed. Current: $field. New: $value" }
        field = value
        onInteractionConfigurationChanged()
      }
    }

  internal val interactionLightRadiusFraction: Float
    get() = inheritedStyleValues.interactionLightRadiusFraction

  public var interactionTransformTarget: GlassTransformTarget = GlassTransformTarget.MaterialOnly
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "interactionTransformTarget changed. Current: $field. New: $value" }
        field = value
        onInteractionConfigurationChanged()
      }
    }

  public var interactionTransformPivot: GlassTransformPivot = GlassTransformPivot.Pointer
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "interactionTransformPivot changed. Current: $field. New: $value" }
        field = value
        onInteractionConfigurationChanged()
      }
    }

  internal val interactionPositionAnimationSpec
    get() = inheritedStyleValues.interactionPositionAnimationSpec

  public var interactionReducedMotionPolicy: GlassReducedMotionPolicy = GlassReducedMotionPolicy.System
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "interactionReducedMotionPolicy changed. Current: $field. New: $value" }
        field = value
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
  internal open val alpha: Float get() = inheritedStyleValues.alpha * transitionAlpha

  /** The authored alpha, without [transitionAlpha]. */
  internal val styleAlpha: Float get() = inheritedStyleValues.alpha
  internal val contrast: Float get() = inheritedStyleValues.contrast
  internal val whitePoint: Float get() = inheritedStyleValues.whitePoint
  internal val chromaMultiplier: Float get() = inheritedStyleValues.chromaMultiplier
  internal val contentNormalBlend: Float get() = inheritedStyleValues.contentNormalBlend
  internal val specularExponent: Float get() = inheritedStyleValues.specularExponent
  internal val fresnelExponent: Float get() = inheritedStyleValues.fresnelExponent

  internal var style: GlassStyle = GlassStyle
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "style changed. Current: $field. New: $value" }
        field = value
        updateInheritedStyleValues()
        markDirty(GlassDirtyFields.Style)
      }
    }

  /** Members drawn as one surface by a Glass container (ADR-0013), or null for one surface. */
  internal var mergedField: GlassMergedField? = null
    set(value) {
      if (field != value) {
        field = value
        markDirty(GlassDirtyFields.MergedField)
      }
    }

  /** Optics alone use the common container size; singletons retain their ordinary shader. */
  internal var opticalSizeOverride: Size? = null
    set(value) {
      if (field != value) {
        field = value
        markDirty(GlassDirtyFields.OpticalSize)
      }
    }

  /** A container's Materialize fade multiplier; 1 outside a transition. */
  internal var transitionAlpha: Float = 1f
    set(value) {
      if (field != value) {
        field = value
        markDirty(GlassDirtyFields.Alpha)
      }
    }

  internal var systemAppearance: GlassSystemAppearance = GlassSystemAppearance.Light
    set(value) {
      if (field != value) {
        field = value
        markDirty(GlassDirtyFields.Accessibility)
        updateInheritedStyleValues()
      }
    }

  internal var compositionLocalStyle: GlassStyle = GlassStyle
    set(value) {
      if (field !== value) {
        HazeLogger.d(TAG) { "LocalGlassStyle changed. Current: $field. New: $value" }
        field = value
      }
      // Always re-resolve: this re-runs Style blocks inside the node's observed update, which is
      // how state read by an unchanged Style reaches the node.
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
    resolveGlassStyleValues(compositionLocalStyle, style, systemAppearance)

  private var styleBatchDepth = 0
  private var styleResolutionPending = false

  /** Resolves Styles once when [block] ends, however many of their inputs it changes. */
  internal inline fun batchStyleResolution(block: () -> Unit) {
    beginStyleBatch()
    try {
      block()
    } finally {
      endStyleBatch()
    }
  }

  @PublishedApi
  internal fun beginStyleBatch() {
    styleBatchDepth++
  }

  @PublishedApi
  internal fun endStyleBatch() {
    if (--styleBatchDepth == 0 && styleResolutionPending) {
      styleResolutionPending = false
      updateInheritedStyleValues()
    }
  }

  private fun updateInheritedStyleValues() {
    if (styleBatchDepth > 0) {
      styleResolutionPending = true
      return
    }
    val resolved = resolveGlassStyleValues(compositionLocalStyle, style, systemAppearance)
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
