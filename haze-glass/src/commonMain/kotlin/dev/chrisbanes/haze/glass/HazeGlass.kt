// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectVisualEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.hazeEffect

/**
 * Draws a Glass material using an explicit Haze [input].
 *
 * [style] is a stateless, replayable appearance program. Haze evaluates
 * [GlassDefaults.style] → [LocalGlassStyle] → [style] into a fresh snapshot owned by this modifier
 * node. The same Style may therefore be shared by concurrent nodes. Its hover, focus, and press
 * response blocks are likewise evaluated and animated by each node independently.
 *
 * [input], [sampling], [expandLayerBounds], and [interactionSource] are structural modifier
 * configuration rather than Style properties. Recomposition replaces each value completely,
 * including a `null` interaction source.
 *
 * @param input Source-backed content or this modifier's own content.
 * @param style Explicit appearance applied after defaults and [LocalGlassStyle].
 * @param sampling Input sampling policy. [HazeSampling.Default] preserves Glass's unscaled default.
 * @param expandLayerBounds Whether Glass may expand its capture layer for optical sampling.
 * @param interactionSource Optional external interaction source owned by this modifier node.
 * @param interactionReducedMotionPolicy Motion policy for this node's Style responses.
 */
@Stable
@ExperimentalHazeApi
public fun Modifier.hazeGlass(
  input: HazeInput,
  style: GlassStyle = GlassStyle,
  sampling: HazeSampling = HazeSampling.Default,
  expandLayerBounds: Boolean = true,
  interactionSource: InteractionSource? = null,
  interactionLightRadiusFraction: Float = GlassDefaults.interactionLightRadiusFraction,
  interactionTransformTarget: GlassTransformTarget = GlassTransformTarget.MaterialOnly,
  interactionTransformPivot: GlassTransformPivot = GlassTransformPivot.Pointer,
  interactionPositionAnimationSpec: FiniteAnimationSpec<Offset> = GlassDefaults.positionAnimationSpec,
  interactionReducedMotionPolicy: GlassReducedMotionPolicy = GlassReducedMotionPolicy.System,
): Modifier = hazeGlass(
  factory = GlassHazeEffectFactory,
  input = input,
  style = style,
  sampling = sampling,
  expandLayerBounds = expandLayerBounds,
  interactionSource = interactionSource,
  interactionLightRadiusFraction = interactionLightRadiusFraction,
  interactionTransformTarget = interactionTransformTarget,
  interactionTransformPivot = interactionTransformPivot,
  interactionPositionAnimationSpec = interactionPositionAnimationSpec,
  interactionReducedMotionPolicy = interactionReducedMotionPolicy,
)

internal fun Modifier.hazeGlass(
  factory: HazeEffectFactory<GlassNodeConfiguration>,
  input: HazeInput,
  style: GlassStyle,
  sampling: HazeSampling,
  expandLayerBounds: Boolean,
  interactionSource: InteractionSource?,
  interactionLightRadiusFraction: Float = GlassDefaults.interactionLightRadiusFraction,
  interactionTransformTarget: GlassTransformTarget = GlassTransformTarget.MaterialOnly,
  interactionTransformPivot: GlassTransformPivot = GlassTransformPivot.Pointer,
  interactionPositionAnimationSpec: FiniteAnimationSpec<Offset> = GlassDefaults.positionAnimationSpec,
  interactionReducedMotionPolicy: GlassReducedMotionPolicy = GlassReducedMotionPolicy.System,
): Modifier = hazeEffect(
  factory = factory,
  input = input,
  style = GlassNodeConfiguration(
    style = style,
    interactionSource = interactionSource,
    interactionLightRadiusFraction = interactionLightRadiusFraction,
    interactionTransformTarget = interactionTransformTarget,
    interactionTransformPivot = interactionTransformPivot,
    interactionPositionAnimationSpec = interactionPositionAnimationSpec,
    interactionReducedMotionPolicy = interactionReducedMotionPolicy,
  ),
  sampling = sampling,
  expandLayerBounds = expandLayerBounds,
)

@Poko
internal class GlassNodeConfiguration(
  val style: GlassStyle,
  val interactionSource: InteractionSource?,
  val interactionLightRadiusFraction: Float = GlassDefaults.interactionLightRadiusFraction,
  val interactionTransformTarget: GlassTransformTarget = GlassTransformTarget.MaterialOnly,
  val interactionTransformPivot: GlassTransformPivot = GlassTransformPivot.Pointer,
  val interactionPositionAnimationSpec: FiniteAnimationSpec<Offset> = GlassDefaults.positionAnimationSpec,
  val interactionReducedMotionPolicy: GlassReducedMotionPolicy = GlassReducedMotionPolicy.System,
)

internal object GlassHazeEffectFactory :
  HazeEffectFactory<GlassNodeConfiguration>,
  HazeEffectVisualEffectFactory<GlassNodeConfiguration> {

  override fun createRenderer(): HazeEffectRenderer<GlassNodeConfiguration> {
    error("Glass uses the built-in full VisualEffect adapter")
  }

  override fun createVisualEffect(
    style: GlassNodeConfiguration,
    sampling: HazeSampling,
  ): GlassRuntimeEffect = GlassRuntimeEffect(style)
}
