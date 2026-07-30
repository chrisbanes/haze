// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.Poko

/**
 * A [ProvidableCompositionLocal] which provides inherited Glass appearance.
 *
 * A Glass node evaluates [GlassDefaults.style], this Style, and its explicit [GlassStyle] in that
 * order. Each node uses a fresh accumulator, so a Style may be shared safely by concurrent nodes.
 */
@ExperimentalHazeApi
public val LocalGlassStyle: ProvidableCompositionLocal<GlassStyle> =
  compositionLocalOf { GlassStyle }

/**
 * An opaque, stateless, replayable Glass appearance program.
 *
 * Create a Style with [GlassStyle] and combine Styles with [then]. Writes run in order and the last
 * write to a property wins. The companion object is the empty Style and performs no writes.
 *
 * A Style contains no renderer or mutable evaluation state. [GlassStyleScope.hovered],
 * [GlassStyleScope.focused], and [GlassStyleScope.pressed] record declarative responses only;
 * each `hazeGlass` node evaluates them into a fresh node-owned snapshot and owns its signals,
 * animations, pointer observation, and renderer resources.
 */
@ExperimentalHazeApi
@Immutable
public sealed interface GlassStyle {
  public companion object : GlassStyle {
    /**
     * Temporary compatibility name for the empty Style.
     */
    @Deprecated("Use GlassStyle directly as the empty Style.")
    public val Unspecified: GlassStyle get() = this
  }
}

/**
 * Creates a replayable [GlassStyle].
 *
 * The [block] is retained as immutable configuration and replayed into fresh node-local state.
 */
@ExperimentalHazeApi
public fun GlassStyle(block: GlassStyleScope.() -> Unit): GlassStyle = BlockGlassStyle(block)

/**
 * Returns a Style which evaluates this Style followed by [other].
 *
 * When both Styles write the same property, [other] wins.
 */
@ExperimentalHazeApi
public infix fun GlassStyle.then(other: GlassStyle): GlassStyle = when {
  this === GlassStyle -> other
  other === GlassStyle -> this
  else -> CombinedGlassStyle(this, other)
}

/**
 * Returns a Style which evaluates this Style followed by [block].
 *
 * Writes in [block] take precedence over earlier writes.
 */
@ExperimentalHazeApi
public fun GlassStyle.then(block: GlassStyleScope.() -> Unit): GlassStyle =
  this then GlassStyle(block)

@Immutable
private class BlockGlassStyle(
  val block: GlassStyleScope.() -> Unit,
) : GlassStyle

@Immutable
private class CombinedGlassStyle(
  val first: GlassStyle,
  val second: GlassStyle,
) : GlassStyle

/**
 * Receiver for Glass appearance property writes.
 *
 * Property functions canonicalize values as they are written. Calling a function again replaces
 * its previous value in the current evaluation.
 */
@ExperimentalHazeApi
public class GlassStyleScope internal constructor(
  private val values: GlassStyleValues,
) {
  /**
   * Declares the response evaluated by each node while it is hovered.
   *
   * The last declaration for this state wins. Pointer observation is installed only when hover or
   * press has a declaration, and observes without consuming application input.
   */
  public fun hovered(block: GlassInteractionScope.() -> Unit) {
    values.hoveredInteraction = buildGlassInteractionResponse(block)
  }

  /** Declares the response evaluated by each node while it is focused. */
  public fun focused(block: GlassInteractionScope.() -> Unit) {
    values.focusedInteraction = buildGlassInteractionResponse(block)
  }

  /** Declares the response evaluated by each node while it is pressed. */
  public fun pressed(block: GlassInteractionScope.() -> Unit) {
    values.pressedInteraction = buildGlassInteractionResponse(block)
  }

  public fun shape(value: RoundedCornerShape) {
    values.shape = value
  }

  public fun optics(value: GlassOptics) {
    values.optics = value
  }

  public fun specularIntensity(value: Float) {
    values.specularIntensity = value.coerceIn(0f, 1f)
  }

  public fun ambientResponse(value: Float) {
    values.ambientResponse = value.coerceIn(0f, 1f)
  }

  public fun tint(value: Color) {
    require(value.isSpecified) { "tint must be specified" }
    values.tint = value
  }

  public fun edgeSoftness(value: Dp) {
    require(value.isSpecified) { "edgeSoftness must be specified" }
    values.edgeSoftness = value.coerceAtLeast(0.dp)
  }

  /**
   * Sets the virtual light position. [Offset.Unspecified] keeps the automatic material center.
   */
  public fun lightPosition(value: Offset) {
    require(
      value == Offset.Unspecified ||
        (value.x.isFinite() && value.y.isFinite()),
    ) {
      "lightPosition must be finite or Offset.Unspecified"
    }
    values.lightPosition = value
  }

  public fun chromaticAberrationStrength(value: Float) {
    values.chromaticAberrationStrength = value.coerceIn(0f, 1f)
  }

  public fun surfaceProfile(value: SurfaceProfile) {
    values.surfaceProfile = value
  }

  public fun chromaticAberrationMode(value: ChromaticAberrationMode) {
    values.chromaticAberrationMode = value
  }

  public fun alpha(value: Float) {
    values.alpha = value.coerceIn(0f, 1f)
  }

  public fun contrast(value: Float) {
    values.contrast = value.coerceIn(-1f, 1f)
  }

  public fun whitePoint(value: Float) {
    values.whitePoint = value.coerceIn(-1f, 1f)
  }

  public fun chromaMultiplier(value: Float) {
    values.chromaMultiplier = value.coerceIn(0f, 2f)
  }

  public fun contentNormalBlend(value: Float) {
    values.contentNormalBlend = value.coerceIn(0f, 1f)
  }

  public fun specularExponent(value: Float) {
    values.specularExponent = value.coerceAtLeast(0f)
  }

  public fun fresnelExponent(value: Float) {
    values.fresnelExponent = value.coerceAtLeast(0f)
  }
}

@Poko
internal class GlassStyleValues(
  var shape: RoundedCornerShape = GlassDefaults.shape,
  var optics: GlassOptics = GlassDefaults.optics,
  var specularIntensity: Float = GlassDefaults.specularIntensity,
  var ambientResponse: Float = GlassDefaults.ambientResponse,
  var tint: Color = GlassDefaults.tint,
  var edgeSoftness: Dp = GlassDefaults.edgeSoftness,
  var lightPosition: Offset = Offset.Unspecified,
  var chromaticAberrationStrength: Float = GlassDefaults.chromaticAberrationStrength,
  var surfaceProfile: SurfaceProfile = GlassDefaults.surfaceProfile,
  var chromaticAberrationMode: ChromaticAberrationMode = GlassDefaults.chromaticAberrationMode,
  var alpha: Float = GlassDefaults.alpha,
  var contrast: Float = GlassDefaults.contrast,
  var whitePoint: Float = GlassDefaults.whitePoint,
  var chromaMultiplier: Float = GlassDefaults.chromaMultiplier,
  var contentNormalBlend: Float = GlassDefaults.contentNormalBlend,
  var specularExponent: Float = GlassDefaults.specularExponent,
  var fresnelExponent: Float = GlassDefaults.fresnelExponent,
  var hoveredInteraction: GlassInteractionResponse? = null,
  var focusedInteraction: GlassInteractionResponse? = null,
  var pressedInteraction: GlassInteractionResponse? = null,
)

internal fun resolveGlassStyleValues(
  localStyle: GlassStyle,
  explicitStyle: GlassStyle,
): GlassStyleValues = GlassStyleValues().also { values ->
  val scope = GlassStyleScope(values)
  GlassDefaults.style.applyTo(scope)
  localStyle.applyTo(scope)
  explicitStyle.applyTo(scope)
}

/** Evaluates stateless Style declarations into a fresh, node-owned response snapshot. */
internal fun resolveGlassStyleInteractionSlots(
  localStyle: GlassStyle,
  explicitStyle: GlassStyle,
): GlassInteractionSlots {
  val values = resolveGlassStyleValues(localStyle, explicitStyle)
  return GlassInteractionSlots(
    focused = values.focusedInteraction?.let { GlassInteractionSlot(1L, it) },
    hovered = values.hoveredInteraction?.let { GlassInteractionSlot(1L, it) },
    pressed = values.pressedInteraction?.let { GlassInteractionSlot(1L, it) },
  )
}

private fun GlassStyle.applyTo(scope: GlassStyleScope) {
  when (this) {
    GlassStyle -> Unit
    is BlockGlassStyle -> scope.block()
    is CombinedGlassStyle -> {
      first.applyTo(scope)
      second.applyTo(scope)
    }
  }
}

/**
 * Legacy grouped lighting patch retained temporarily for source migration.
 *
 * New code should use property functions in [GlassStyleScope].
 */
@Deprecated("Use GlassStyle { specularIntensity(...); ambientResponse(...); ... }.")
@ExperimentalHazeApi
@Immutable
public data class GlassLighting(
  val specularIntensity: Float = Float.NaN,
  val specularExponent: Float = Float.NaN,
  val fresnelExponent: Float = Float.NaN,
  val ambientResponse: Float = Float.NaN,
  val lightPosition: Offset = Offset.Unspecified,
) {
  public companion object {
    public val Unspecified: GlassLighting = GlassLighting()
  }
}

/**
 * Legacy grouped color patch retained temporarily for source migration.
 *
 * New code should use property functions in [GlassStyleScope].
 */
@Deprecated("Use GlassStyle { alpha(...); contrast(...); ... }.")
@ExperimentalHazeApi
@Immutable
public data class GlassColor(
  val alpha: Float = Float.NaN,
  val contrast: Float = Float.NaN,
  val whitePoint: Float = Float.NaN,
  val chromaMultiplier: Float = Float.NaN,
) {
  public companion object {
    public val Unspecified: GlassColor = GlassColor()
  }
}

/**
 * Legacy grouped rendering patch retained temporarily for source migration.
 *
 * New code should use property functions in [GlassStyleScope].
 */
@Deprecated("Use GlassStyle { edgeSoftness(...); contentNormalBlend(...); ... }.")
@ExperimentalHazeApi
@Immutable
public data class GlassRendering(
  val edgeSoftness: Dp = Dp.Unspecified,
  val contentNormalBlend: Float = Float.NaN,
  val surfaceProfile: SurfaceProfile? = null,
  val chromaticAberrationStrength: Float = Float.NaN,
  val chromaticAberrationMode: ChromaticAberrationMode? = null,
) {
  public companion object {
    public val Unspecified: GlassRendering = GlassRendering()
  }
}

/**
 * Temporary constructor adapter for the legacy grouped Style surface.
 *
 * Sentinel interpretation is confined to this adapter; ordinary Style evaluation only replays
 * explicit property writes.
 */
@Deprecated("Use GlassStyle { property(value) }.")
@ExperimentalHazeApi
@Suppress("DEPRECATION")
public fun GlassStyle(
  tint: Color = Color.Unspecified,
  shape: RoundedCornerShape? = null,
  optics: GlassOptics? = null,
  lighting: GlassLighting = GlassLighting.Unspecified,
  color: GlassColor = GlassColor.Unspecified,
  rendering: GlassRendering = GlassRendering.Unspecified,
): GlassStyle = GlassStyle {
  if (tint.isSpecified) tint(tint)
  shape?.let(::shape)
  optics?.let(::optics)
  if (!lighting.specularIntensity.isNaN()) specularIntensity(lighting.specularIntensity)
  if (!lighting.specularExponent.isNaN()) specularExponent(lighting.specularExponent)
  if (!lighting.fresnelExponent.isNaN()) fresnelExponent(lighting.fresnelExponent)
  if (!lighting.ambientResponse.isNaN()) ambientResponse(lighting.ambientResponse)
  if (lighting.lightPosition != Offset.Unspecified) lightPosition(lighting.lightPosition)
  if (!color.alpha.isNaN()) alpha(color.alpha)
  if (!color.contrast.isNaN()) contrast(color.contrast)
  if (!color.whitePoint.isNaN()) whitePoint(color.whitePoint)
  if (!color.chromaMultiplier.isNaN()) chromaMultiplier(color.chromaMultiplier)
  if (rendering.edgeSoftness.isSpecified) edgeSoftness(rendering.edgeSoftness)
  if (!rendering.contentNormalBlend.isNaN()) contentNormalBlend(rendering.contentNormalBlend)
  rendering.surfaceProfile?.let(::surfaceProfile)
  if (!rendering.chromaticAberrationStrength.isNaN()) {
    chromaticAberrationStrength(rendering.chromaticAberrationStrength)
  }
  rendering.chromaticAberrationMode?.let(::chromaticAberrationMode)
}
