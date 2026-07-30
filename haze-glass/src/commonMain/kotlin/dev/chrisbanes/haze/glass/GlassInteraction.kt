// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import dev.chrisbanes.haze.ExperimentalHazeApi

@ExperimentalHazeApi
public enum class GlassTransformTarget {
  MaterialOnly,
  MaterialAndContent,
}

@ExperimentalHazeApi
public enum class GlassTransformPivot {
  Pointer,
  Center,
}

@ExperimentalHazeApi
public enum class GlassReducedMotionPolicy {
  System,
  Reduced,
  Full,
}

@ExperimentalHazeApi
public interface GlassInteractionScope {
  public fun lightingIntensity(intensity: Float)

  public fun refractionMultiplier(multiplier: Float)

  public fun whitePointDelta(delta: Float)

  public fun scale(scale: Float) {
    scale(scaleX = scale, scaleY = scale)
  }

  public fun scale(scaleX: Float, scaleY: Float)

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
    requireFiniteInRange("lightingIntensity", intensity, 0f..1f)
    lighting = value(intensity)
  }

  override fun refractionMultiplier(multiplier: Float) {
    requireFiniteInRange("refractionMultiplier", multiplier, 0f..2f)
    refraction = value(multiplier)
  }

  override fun whitePointDelta(delta: Float) {
    requireFiniteInRange("whitePointDelta", delta, -1f..1f)
    whitePoint = value(delta)
  }

  override fun scale(scaleX: Float, scaleY: Float) {
    requireFiniteScale("scaleX", scaleX)
    requireFiniteScale("scaleY", scaleY)
    xScale = value(scaleX)
    yScale = value(scaleY)
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

private fun requireFiniteInRange(
  name: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
) {
  require(value.isFinite() && value in range) { "$name must be finite and in range" }
}

private fun requireFiniteScale(name: String, value: Float) {
  require(value.isFinite() && value > 0f && value <= 1f) {
    "$name must be finite, greater than zero, and at most one"
  }
}
