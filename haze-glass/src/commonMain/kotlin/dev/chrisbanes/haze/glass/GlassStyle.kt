// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.FiniteAnimationSpec
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
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.Poko

/**
 * A [ProvidableCompositionLocal] which provides inherited Glass appearance.
 *
 * A Glass node replays [GlassDefaults.style], this Style, and its explicit [GlassStyle] in that
 * order. Each node uses a fresh accumulator, so a Style may be shared safely by concurrent nodes.
 */
@ExperimentalHazeApi
public val LocalGlassStyle: ProvidableCompositionLocal<GlassStyle> =
  compositionLocalOf { GlassStyle }

/**
 * An opaque, immutable sequence of recorded Glass appearance writes.
 *
 * The builder passed to [GlassStyle] executes once during construction. Each property is
 * canonicalized and captured as an immutable write; resolving a Style only replays those captured
 * values and never invokes caller code. Combine Styles with [then]. Writes run in order and the
 * last write to a property wins. The companion object is the empty Style and performs no writes.
 *
 * Mutating an input captured by a previously constructed Style has no effect. To update a node,
 * construct and supply a replacement Style through recomposition.
 *
 * A Style contains no renderer or mutable runtime state. [GlassStyleScope.hovered],
 * [GlassStyleScope.focused], [GlassStyleScope.pressed],
 * [GlassStyleScope.interactionLightRadiusFraction], and
 * [GlassStyleScope.interactionPositionAnimationSpec] record reusable interaction presentation;
 * each `hazeGlass` node replays it into a fresh node-owned snapshot and owns its signals,
 * geometry, animations, pointer observation, and renderer resources.
 */
@ExperimentalHazeApi
@Immutable
public sealed interface GlassStyle {
  /** Returns a Style which replays [other] after this Style. */
  public infix fun then(other: GlassStyle): GlassStyle = combineGlassStyles(this, other)

  /** Returns a Style which replays the writes recorded by [block] after this Style. */
  public fun then(block: GlassStyleScope.() -> Unit): GlassStyle = then(GlassStyle(block))

  /** The empty Glass Style, which performs no writes. */
  public companion object : GlassStyle
}

/**
 * Creates a recorded, replayable [GlassStyle].
 *
 * [block] executes exactly once during this call. Its canonicalized property values are captured
 * as immutable writes which can later be replayed into fresh node-local state without invoking
 * [block] again.
 */
@ExperimentalHazeApi
public fun GlassStyle(block: GlassStyleScope.() -> Unit): GlassStyle =
  RecordedGlassStyle(recordGlassStyleWrites(block))

@Immutable
private class RecordedGlassStyle(
  private val writes: List<GlassStyleValues.() -> Unit>,
) : GlassStyle {
  fun replay(values: GlassStyleValues) {
    for (write in writes) {
      values.write()
    }
  }

  fun then(other: RecordedGlassStyle): GlassStyle =
    RecordedGlassStyle(writes + other.writes)
}

private fun combineGlassStyles(
  first: GlassStyle,
  second: GlassStyle,
): GlassStyle = when (first) {
  GlassStyle -> second
  is RecordedGlassStyle -> when (second) {
    GlassStyle -> first
    is RecordedGlassStyle -> first.then(second)
  }
}

/** Marks the nested receiver scopes used while constructing a [GlassStyle]. */
@DslMarker
@ExperimentalHazeApi
public annotation class GlassStyleDsl

/**
 * Receiver for Glass appearance property writes.
 *
 * Property functions canonicalize values while the Style is constructed and record them for later
 * replay. Calling a function again records a later write which takes precedence.
 */
@ExperimentalHazeApi
@GlassStyleDsl
public class GlassStyleScope internal constructor(
  private val writes: MutableList<GlassStyleValues.() -> Unit>,
) {
  /**
   * Declares the response evaluated by each node while it is hovered.
   *
   * The last declaration for this state wins. Pointer observation is installed only when hover or
   * press has a declaration, and observes without consuming application input.
   */
  public fun hovered(response: GlassInteractionScope.() -> Unit) {
    val recorded = buildGlassInteractionResponse(response)
    writes += { hoveredInteraction = recorded }
  }

  /** Declares the response evaluated by each node while it is focused. */
  public fun focused(response: GlassInteractionScope.() -> Unit) {
    val recorded = buildGlassInteractionResponse(response)
    writes += { focusedInteraction = recorded }
  }

  /** Declares the response evaluated by each node while it is pressed. */
  public fun pressed(response: GlassInteractionScope.() -> Unit) {
    val recorded = buildGlassInteractionResponse(response)
    writes += { pressedInteraction = recorded }
  }

  /**
   * Sets the interaction-light radius as a fraction of the material's shortest side.
   *
   * The value must be finite and in the range `0f..2f`.
   */
  public fun interactionLightRadiusFraction(radiusFraction: Float) {
    require(radiusFraction.isFinite() && radiusFraction in 0f..2f) {
      "interactionLightRadiusFraction must be finite and in range"
    }
    writes += { interactionLightRadiusFraction = radiusFraction }
  }

  /**
   * Sets the animation used when the interaction light moves to a new position.
   *
   * This presentation is replayed into fresh animation state owned by each consuming node.
   */
  public fun interactionPositionAnimationSpec(animationSpec: FiniteAnimationSpec<Offset>) {
    writes += { interactionPositionAnimationSpec = animationSpec }
  }

  /** Sets the rounded boundary used for refraction and masking. */
  public fun shape(shape: RoundedCornerShape) {
    writes += { this.shape = shape }
  }

  /** Sets a complete fixed optical model used to refract and blur captured content. */
  public fun optics(
    refractionStrength: Float = 0.7f,
    refractionHeightFraction: Float = 0.25f,
    refractionDisplacement: Dp = 15.dp,
    depth: Float = 1f,
    blurRadius: Dp = 14.dp,
    progressive: HazeProgressive? = null,
  ) {
    optics(
      GlassOptics.Fixed(
        refractionStrength = refractionStrength,
        refractionHeightFraction = refractionHeightFraction,
        refractionDisplacement = refractionDisplacement,
        depth = depth,
        blurRadius = blurRadius,
        progressive = progressive,
      ),
    )
  }

  /** Sets the optical model used to refract and blur captured content. */
  public fun optics(optics: GlassOptics) {
    writes += { this.optics = optics }
  }

  /** Sets specular-highlight intensity, coerced to the range `0f..1f`. */
  public fun specularIntensity(intensity: Float) {
    val canonical = intensity.coerceIn(0f, 1f)
    writes += { specularIntensity = canonical }
  }

  /** Sets ambient-light response, coerced to the range `0f..1f`. */
  public fun ambientResponse(response: Float) {
    val canonical = response.coerceIn(0f, 1f)
    writes += { ambientResponse = canonical }
  }

  /** Sets the tint applied to refracted content. */
  public fun tint(color: Color) {
    require(color.isSpecified) { "tint must be specified" }
    writes += { tint = color }
  }

  /** Sets the non-negative softening distance around the material boundary. */
  public fun edgeSoftness(softness: Dp) {
    require(softness.isSpecified) { "edgeSoftness must be specified" }
    val canonical = softness.coerceAtLeast(0.dp)
    writes += { edgeSoftness = canonical }
  }

  /**
   * Sets the virtual light position. [Offset.Unspecified] keeps the automatic material center.
   */
  public fun lightPosition(position: Offset) {
    require(
      position == Offset.Unspecified ||
        (position.x.isFinite() && position.y.isFinite()),
    ) {
      "lightPosition must be finite or Offset.Unspecified"
    }
    writes += { lightPosition = position }
  }

  /** Sets chromatic dispersion strength, coerced to the range `0f..1f`. */
  public fun chromaticAberrationStrength(strength: Float) {
    val canonical = strength.coerceIn(0f, 1f)
    writes += { chromaticAberrationStrength = canonical }
  }

  /** Sets the cross-section profile used by the refraction bezel. */
  public fun surfaceProfile(profile: SurfaceProfile) {
    writes += { surfaceProfile = profile }
  }

  /** Sets the quality mode used to render chromatic aberration. */
  public fun chromaticAberrationMode(mode: ChromaticAberrationMode) {
    writes += { chromaticAberrationMode = mode }
  }

  /** Sets overall material opacity, coerced to the range `0f..1f`. */
  public fun alpha(alpha: Float) {
    val canonical = alpha.coerceIn(0f, 1f)
    writes += { this.alpha = canonical }
  }

  /** Sets contrast adjustment, coerced to the range `-1f..1f`. */
  public fun contrast(contrast: Float) {
    val canonical = contrast.coerceIn(-1f, 1f)
    writes += { this.contrast = canonical }
  }

  /** Sets white-point adjustment, coerced to the range `-1f..1f`. */
  public fun whitePoint(whitePoint: Float) {
    val canonical = whitePoint.coerceIn(-1f, 1f)
    writes += { this.whitePoint = canonical }
  }

  /** Sets the chroma multiplier, coerced to the range `0f..2f`. */
  public fun chromaMultiplier(multiplier: Float) {
    val canonical = multiplier.coerceIn(0f, 2f)
    writes += { chromaMultiplier = canonical }
  }

  /** Sets the blend between generated and captured normals, coerced to `0f..1f`. */
  public fun contentNormalBlend(blend: Float) {
    val canonical = blend.coerceIn(0f, 1f)
    writes += { contentNormalBlend = canonical }
  }

  /** Sets the non-negative exponent controlling specular highlight concentration. */
  public fun specularExponent(exponent: Float) {
    val canonical = exponent.coerceAtLeast(0f)
    writes += { specularExponent = canonical }
  }

  /** Sets the non-negative exponent controlling Fresnel response falloff. */
  public fun fresnelExponent(exponent: Float) {
    val canonical = exponent.coerceAtLeast(0f)
    writes += { fresnelExponent = canonical }
  }
}

private fun recordGlassStyleWrites(
  block: GlassStyleScope.() -> Unit,
): List<GlassStyleValues.() -> Unit> = buildList {
  GlassStyleScope(this).block()
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
  var interactionLightRadiusFraction: Float = GlassDefaults.interactionLightRadiusFraction,
  var interactionPositionAnimationSpec: FiniteAnimationSpec<Offset> =
    GlassDefaults.positionAnimationSpec,
)

internal fun resolveGlassStyleValues(
  localStyle: GlassStyle,
  explicitStyle: GlassStyle,
): GlassStyleValues = GlassStyleValues().also { values ->
  GlassDefaults.style.replay(values)
  localStyle.replay(values)
  explicitStyle.replay(values)
}

private fun GlassStyle.replay(values: GlassStyleValues) {
  when (this) {
    GlassStyle -> Unit
    is RecordedGlassStyle -> replay(values)
  }
}
