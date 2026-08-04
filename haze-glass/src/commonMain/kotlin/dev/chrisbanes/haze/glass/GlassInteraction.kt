// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import dev.chrisbanes.haze.ExperimentalHazeApi

/** Selects which visual layers receive the interaction scale transform. */
@ExperimentalHazeApi
public enum class GlassTransformTarget {
  /** Scales the generated Glass material while leaving the composable content unchanged. */
  MaterialOnly,

  /** Scales both the generated Glass material and the composable content. */
  MaterialAndContent,
}

/** Selects the pivot used by the interaction scale transform. */
@ExperimentalHazeApi
public enum class GlassTransformPivot {
  /** Uses the current pointer or interaction position as the transform pivot. */
  Pointer,

  /** Uses the center of the material as the transform pivot. */
  Center,
}

/** Controls whether interaction motion is reduced. */
@ExperimentalHazeApi
public enum class GlassReducedMotionPolicy {
  /** Follows the current platform motion-duration scale. */
  System,

  /** Disables interaction scale motion and snaps animated responses to their targets. */
  Reduced,

  /** Preserves full interaction motion regardless of the platform motion-duration scale. */
  Full,
}

/**
 * Declares the visual response for one Glass interaction state.
 *
 * This sealed receiver is implemented by Haze and is a declaration DSL, not a consumer
 * implementation point. Each node evaluates recorded declarations into node-owned runtime state.
 */
@ExperimentalHazeApi
@GlassStyleDsl
public sealed interface GlassInteractionScope {
  /** Sets the finite lighting multiplier in the inclusive range `0f..1f`. */
  public fun lightingIntensity(intensity: Float)

  /** Sets the finite refraction multiplier in the inclusive range `0f..2f`. */
  public fun refractionMultiplier(multiplier: Float)

  /** Sets the finite additive white-point adjustment in the inclusive range `-1f..1f`. */
  public fun whitePointDelta(delta: Float)

  /** Sets a finite uniform scale in `0f < scale <= 1f`. */
  public fun scale(scale: Float) {
    scale(scaleX = scale, scaleY = scale)
  }

  /** Sets finite horizontal and vertical scales in `0f < value <= 1f`. */
  public fun scale(scaleX: Float, scaleY: Float)

  /**
   * Applies [toSpec] when entering and [fromSpec] when leaving the response declared by [block].
   * Animation specs retain the contract of their owning [FiniteAnimationSpec] type.
   */
  public fun animate(
    toSpec: FiniteAnimationSpec<Float>,
    fromSpec: FiniteAnimationSpec<Float>,
    block: GlassInteractionScope.() -> Unit,
  )
}

internal data class GlassResponseValue(
  val value: Float,
  val toSpec: FiniteAnimationSpec<Float>?,
  val fromSpec: FiniteAnimationSpec<Float>?,
)

internal data class GlassInteractionResponse(
  val lightingIntensity: GlassResponseValue? = null,
  val refractionMultiplier: GlassResponseValue? = null,
  val whitePointDelta: GlassResponseValue? = null,
  val scaleX: GlassResponseValue? = null,
  val scaleY: GlassResponseValue? = null,
)

internal data class GlassInteractionSlot(
  val revision: Long,
  val response: GlassInteractionResponse,
)

internal class GlassInteractionScopeImpl : GlassInteractionScope {
  private var toSpec: FiniteAnimationSpec<Float>? = null
  private var fromSpec: FiniteAnimationSpec<Float>? = null
  private var lighting: GlassResponseValue? = null
  private var refraction: GlassResponseValue? = null
  private var whitePoint: GlassResponseValue? = null
  private var xScale: GlassResponseValue? = null
  private var yScale: GlassResponseValue? = null

  override fun lightingIntensity(intensity: Float) {
    lighting = value(
      requireFiniteInRange("lightingIntensity", intensity, 0f..1f, UNIT_INTERVAL_DOMAIN),
    )
  }

  override fun refractionMultiplier(multiplier: Float) {
    refraction = value(
      requireFiniteInRange("refractionMultiplier", multiplier, 0f..2f, DOUBLE_INTERVAL_DOMAIN),
    )
  }

  override fun whitePointDelta(delta: Float) {
    whitePoint = value(
      requireFiniteInRange("whitePointDelta", delta, -1f..1f, SIGNED_UNIT_INTERVAL_DOMAIN),
    )
  }

  override fun scale(scaleX: Float, scaleY: Float) {
    val validatedX = requirePositiveAtMostOne("scaleX", scaleX)
    val validatedY = requirePositiveAtMostOne("scaleY", scaleY)
    xScale = value(validatedX)
    yScale = value(validatedY)
  }

  override fun animate(
    toSpec: FiniteAnimationSpec<Float>,
    fromSpec: FiniteAnimationSpec<Float>,
    block: GlassInteractionScope.() -> Unit,
  ) {
    val previousTo = this.toSpec
    val previousFrom = this.fromSpec
    this.toSpec = toSpec
    this.fromSpec = fromSpec
    try {
      block()
    } finally {
      this.toSpec = previousTo
      this.fromSpec = previousFrom
    }
  }

  fun build(): GlassInteractionResponse = GlassInteractionResponse(
    lightingIntensity = lighting,
    refractionMultiplier = refraction,
    whitePointDelta = whitePoint,
    scaleX = xScale,
    scaleY = yScale,
  )

  private fun value(value: Float): GlassResponseValue {
    return GlassResponseValue(value = value, toSpec = toSpec, fromSpec = fromSpec)
  }
}

internal fun buildGlassInteractionResponse(
  block: GlassInteractionScope.() -> Unit,
): GlassInteractionResponse = GlassInteractionScopeImpl().apply(block).build()
