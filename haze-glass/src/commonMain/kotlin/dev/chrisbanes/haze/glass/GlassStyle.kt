// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.Poko

/**
 * A [ProvidableCompositionLocal] which provides inherited Glass appearance.
 *
 * A Glass node starts with [GlassDefaults], then replays this Style and its explicit [GlassStyle]
 * in that order. Each node uses a fresh accumulator, so a Style may be shared safely by concurrent
 * nodes.
 */
@ExperimentalHazeApi
public val LocalGlassStyle: ProvidableCompositionLocal<GlassStyle> =
  compositionLocalOf { GlassStyle }

/**
 * An opaque, immutable sequence of recorded Glass appearance writes.
 *
 * The builder passed to [GlassStyle] executes once during construction. Each property is
 * validated and captured as an immutable write; resolving a Style only replays those captured
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
 *
 * The same final Style is replayed unchanged into whichever private renderer Haze selects. Full
 * renderers consume every supported authored channel. Limited renderers preserve supported
 * channels, approximate supported lighting, and omit unsupported base and interaction optics.
 * Selection is automatic; callers do not need a capability check or a second fallback Style.
 */
@ExperimentalHazeApi
@Immutable
public sealed interface GlassStyle {
  /** Returns a Style which replays [other] after this Style. */
  public infix fun then(other: GlassStyle): GlassStyle = combineGlassStyles(this, other)

  /** Returns a Style which replays the writes recorded by [block] after this Style. */
  public fun then(block: GlassStyleScope.() -> Unit): GlassStyle = then(GlassStyle(block))

  /** The empty Glass Style, which performs no writes. */
  public companion object : GlassStyle {

    internal val clearOptics: GlassOptics = GlassOptics(
      refractionStrength = 0.85f,
      refractionHeightFraction = 0.22f,
      refractionDisplacement = 18.dp,
      depth = GlassOptics.SizeValue.Interpolated(
        listOf(
          GlassOptics.SizePoint(64.dp, 0.1f),
          GlassOptics.SizePoint(176.dp, 0.32f),
          GlassOptics.SizePoint(220.dp, 0.52f),
        ),
      ),
      blurRadius = GlassOptics.SizeValue.Interpolated(
        listOf(
          GlassOptics.SizePoint(64.dp, 2.dp),
          GlassOptics.SizePoint(176.dp, 6.dp),
          GlassOptics.SizePoint(220.dp, 8.dp),
        ),
      ),
      refractionDetailIntensity = 0.76f,
    )

    /**
     * The default built-in Glass style.
     *
     * Its blur and depth adapt to each material's shortest dimension. It writes the complete material
     * response while preserving separately composed shape, background colour, tint, alpha, light
     * position, and interaction presentation.
     */
    public val regular: GlassStyle = GlassStyle {
      optics(GlassDefaults.optics)
      specularIntensity(GlassDefaults.specularIntensity)
      ambientResponse(GlassDefaults.ambientResponse)
      edgeSoftness(GlassDefaults.edgeSoftness)
      chromaticAberrationStrength(GlassDefaults.chromaticAberrationStrength)
      surfaceProfile(GlassDefaults.surfaceProfile)
      chromaticAberrationMode(GlassDefaults.chromaticAberrationMode)
      contrast(GlassDefaults.contrast)
      whitePoint(GlassDefaults.whitePoint)
      chromaMultiplier(GlassDefaults.chromaMultiplier)
      contentNormalBlend(GlassDefaults.contentNormalBlend)
      specularExponent(GlassDefaults.specularExponent)
      fresnelExponent(GlassDefaults.fresnelExponent)
    }

    /**
     * A built-in Glass style that prioritizes visibility of content behind the material.
     *
     * Its blur and depth adapt to the material's shortest side while its authored refraction and
     * distinct edge and lighting response remain recognizable when a renderer simplifies advanced
     * optical effects. It writes the complete material response while preserving separately composed
     * shape, background colour, tint, alpha, light position, and interaction presentation.
     */
    public val clear: GlassStyle = GlassStyle {
      optics(clearOptics)
      specularIntensity(0.55f)
      ambientResponse(0.42f)
      edgeSoftness(1.dp)
      chromaticAberrationStrength(0.04f)
      surfaceProfile(SurfaceProfile.Circle)
      chromaticAberrationMode(ChromaticAberrationMode.Simple)
      contrast(0.08f)
      whitePoint(0.02f)
      chromaMultiplier(1.05f)
      contentNormalBlend(0.1f)
      specularExponent(16f)
      fresnelExponent(2.5f)
    }
  }
}

/**
 * Creates a recorded, replayable [GlassStyle].
 *
 * [block] executes exactly once during this call. Its validated property values are captured
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
 * Property functions validate values while the Style is constructed and record them for later
 * replay. Invalid direct Glass numbers throw [IllegalArgumentException]. Calling a function again
 * records a later write which takes precedence.
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
   * The value must be finite and in the inclusive range `0f..2f`.
   */
  public fun interactionLightRadiusFraction(radiusFraction: Float) {
    val validated = requireFiniteInRange(
      "interactionLightRadiusFraction",
      radiusFraction,
      0f..2f,
      DOUBLE_INTERVAL_DOMAIN,
    )
    writes += { interactionLightRadiusFraction = validated }
  }

  /**
   * Sets the animation used for focus and [InteractionSource]-derived positions.
   *
   * Direct mouse, stylus, and touch input tracks immediately.
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
    refractionFoldStrength: Float = 0f,
    refractionDetailIntensity: Float = 0.76f,
  ) {
    optics(
      GlassOptics(
        refractionStrength = refractionStrength,
        refractionHeightFraction = refractionHeightFraction,
        refractionDisplacement = refractionDisplacement,
        depth = GlassOptics.SizeValue.Fixed(depth),
        blurRadius = GlassOptics.SizeValue.Fixed(blurRadius),
        progressive = progressive,
        refractionFoldStrength = refractionFoldStrength,
        refractionDetailIntensity = refractionDetailIntensity,
      ),
    )
  }

  /**
   * Sets the optical model used to refract and blur captured content.
   *
   * Complete optics values retain the contracts enforced by their concrete [GlassOptics] type.
   */
  public fun optics(optics: GlassOptics) {
    writes += { this.optics = optics }
  }

  /** Sets finite specular-highlight intensity in the inclusive range `0f..1f`. */
  public fun specularIntensity(intensity: Float) {
    val validated = requireFiniteInRange("specularIntensity", intensity, 0f..1f, UNIT_INTERVAL_DOMAIN)
    writes += { specularIntensity = validated }
  }

  /** Sets finite ambient-light response in the inclusive range `0f..1f`. */
  public fun ambientResponse(response: Float) {
    val validated = requireFiniteInRange("ambientResponse", response, 0f..1f, UNIT_INTERVAL_DOMAIN)
    writes += { ambientResponse = validated }
  }

  /** Sets any specified color, including transparent, composited behind the captured input. */
  public fun backgroundColor(color: Color) {
    require(color.isSpecified) { "backgroundColor must be specified" }
    writes += { backgroundColor = color }
  }

  /** Sets any specified tint, including transparent, applied to refracted content. */
  public fun tint(color: Color) {
    require(color.isSpecified) { "tint must be specified" }
    writes += { tint = color }
  }

  /**
   * Sets a specified, finite, non-negative softening distance around the material boundary.
   * There is no authored upper limit.
   */
  public fun edgeSoftness(softness: Dp) {
    val validated = requireSpecifiedFiniteNonNegative("edgeSoftness", softness)
    writes += { edgeSoftness = validated }
  }

  /**
   * Aligns the virtual light within each material's measured bounds.
   *
   * The default is [Alignment.Center]. Logical start and end alignments use the node's current
   * layout direction, and a shared Style is resolved independently for each consuming node's size.
   * [BiasAlignment] and [BiasAbsoluteAlignment] require finite horizontal and vertical biases;
   * finite values outside `-1f..1f` intentionally place the light beyond the material. Other
   * [Alignment] implementations retain their own contract and are not evaluated during Style
   * construction.
   */
  public fun lightPosition(alignment: Alignment) {
    when (alignment) {
      is BiasAlignment -> validateLightPositionBiases(
        horizontalBias = alignment.horizontalBias,
        verticalBias = alignment.verticalBias,
      )
      is BiasAbsoluteAlignment -> validateLightPositionBiases(
        horizontalBias = alignment.horizontalBias,
        verticalBias = alignment.verticalBias,
      )
      else -> Unit
    }
    writes += { lightPosition = alignment }
  }

  /** Sets finite chromatic dispersion strength in the inclusive range `0f..1f`. */
  public fun chromaticAberrationStrength(strength: Float) {
    val validated = requireFiniteInRange(
      "chromaticAberrationStrength",
      strength,
      0f..1f,
      UNIT_INTERVAL_DOMAIN,
    )
    writes += { chromaticAberrationStrength = validated }
  }

  /** Sets the cross-section profile used by the refraction bezel. */
  public fun surfaceProfile(profile: SurfaceProfile) {
    writes += { surfaceProfile = profile }
  }

  /** Sets the quality mode used to render chromatic aberration. */
  public fun chromaticAberrationMode(mode: ChromaticAberrationMode) {
    writes += { chromaticAberrationMode = mode }
  }

  /** Sets finite overall material opacity in the inclusive range `0f..1f`. */
  public fun alpha(alpha: Float) {
    val validated = requireFiniteInRange("alpha", alpha, 0f..1f, UNIT_INTERVAL_DOMAIN)
    writes += { this.alpha = validated }
  }

  /** Sets finite contrast adjustment in the inclusive range `-1f..1f`. */
  public fun contrast(contrast: Float) {
    val validated = requireFiniteInRange("contrast", contrast, -1f..1f, SIGNED_UNIT_INTERVAL_DOMAIN)
    writes += { this.contrast = validated }
  }

  /** Sets finite white-point adjustment in the inclusive range `-1f..1f`. */
  public fun whitePoint(whitePoint: Float) {
    val validated = requireFiniteInRange(
      "whitePoint",
      whitePoint,
      -1f..1f,
      SIGNED_UNIT_INTERVAL_DOMAIN,
    )
    writes += { this.whitePoint = validated }
  }

  /** Sets the finite chroma multiplier in the inclusive range `0f..2f`. */
  public fun chromaMultiplier(multiplier: Float) {
    val validated = requireFiniteInRange("chromaMultiplier", multiplier, 0f..2f, DOUBLE_INTERVAL_DOMAIN)
    writes += { chromaMultiplier = validated }
  }

  /** Sets the finite blend between generated and captured normals in inclusive `0f..1f`. */
  public fun contentNormalBlend(blend: Float) {
    val validated = requireFiniteInRange("contentNormalBlend", blend, 0f..1f, UNIT_INTERVAL_DOMAIN)
    writes += { contentNormalBlend = validated }
  }

  /**
   * Sets a finite, non-negative exponent controlling specular highlight concentration.
   * There is no authored upper limit.
   */
  public fun specularExponent(exponent: Float) {
    val validated = requireFiniteNonNegative("specularExponent", exponent)
    writes += { specularExponent = validated }
  }

  /**
   * Sets a finite, non-negative exponent controlling Fresnel response falloff.
   * There is no authored upper limit.
   */
  public fun fresnelExponent(exponent: Float) {
    val validated = requireFiniteNonNegative("fresnelExponent", exponent)
    writes += { fresnelExponent = validated }
  }
}

private fun validateLightPositionBiases(horizontalBias: Float, verticalBias: Float) {
  requireFinite("lightPosition.horizontalBias", horizontalBias)
  requireFinite("lightPosition.verticalBias", verticalBias)
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
  var backgroundColor: Color = GlassDefaults.backgroundColor,
  var tint: Color = GlassDefaults.tint,
  var edgeSoftness: Dp = GlassDefaults.edgeSoftness,
  var lightPosition: Alignment = Alignment.Center,
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
  localStyle.replay(values)
  explicitStyle.replay(values)
}

private fun GlassStyle.replay(values: GlassStyleValues) {
  when (this) {
    GlassStyle -> Unit
    is RecordedGlassStyle -> replay(values)
  }
}
