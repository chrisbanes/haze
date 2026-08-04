// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.hazeEffect

/**
 * Draws a Glass material using an explicit Haze [input].
 *
 * [style] is an immutable sequence of appearance writes recorded when its builder executes. Haze
 * replays [GlassDefaults.style] → [LocalGlassStyle] → [style] into a fresh snapshot owned by this
 * modifier node without invoking any Style builder. The same Style may therefore be shared by
 * concurrent nodes. Each node independently owns and animates the recorded hover, focus, and press
 * responses.
 *
 * Values captured by a previously constructed Style do not update when they are mutated. To update
 * appearance, construct and supply a replacement Style through recomposition.
 *
 * [input], [sampling], [expandLayerBounds], [interactionSource], [interactionTransformTarget],
 * [interactionTransformPivot], and [interactionReducedMotionPolicy] are node-owned modifier
 * mechanics rather than Style presentation. Recomposition replaces each value completely,
 * including a `null` interaction source.
 *
 * @param input Source-backed content or this modifier's own content.
 * @param style Explicit appearance applied after defaults and [LocalGlassStyle].
 * @param sampling Input sampling policy. [HazeSampling.Default] currently points to
 * [HazeSampling.Adaptive], which selects approximately 50% or 25% of full-resolution input pixels
 * from retained-layer work and recent update cadence.
 * @param expandLayerBounds Whether Glass may expand its capture layer for optical sampling.
 * @param interactionSource Optional external interaction source owned by this modifier node.
 * @param interactionTransformTarget Visual layers that receive the interaction scale transform.
 * @param interactionTransformPivot Pivot used by the interaction scale transform.
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
  interactionTransformTarget: GlassTransformTarget = GlassTransformTarget.MaterialOnly,
  interactionTransformPivot: GlassTransformPivot = GlassTransformPivot.Pointer,
  interactionReducedMotionPolicy: GlassReducedMotionPolicy = GlassReducedMotionPolicy.System,
): Modifier = hazeGlass(
  factory = GlassHazeEffectFactory,
  input = input,
  style = style,
  sampling = sampling,
  expandLayerBounds = expandLayerBounds,
  interactionSource = interactionSource,
  interactionTransformTarget = interactionTransformTarget,
  interactionTransformPivot = interactionTransformPivot,
  interactionReducedMotionPolicy = interactionReducedMotionPolicy,
)

internal fun Modifier.hazeGlass(
  factory: HazeEffectFactory<GlassNodeConfiguration>,
  input: HazeInput,
  style: GlassStyle,
  sampling: HazeSampling,
  expandLayerBounds: Boolean,
  interactionSource: InteractionSource?,
  interactionTransformTarget: GlassTransformTarget = GlassTransformTarget.MaterialOnly,
  interactionTransformPivot: GlassTransformPivot = GlassTransformPivot.Pointer,
  interactionReducedMotionPolicy: GlassReducedMotionPolicy = GlassReducedMotionPolicy.System,
): Modifier = hazeEffect(
  factory = factory,
  input = input,
  style = GlassNodeConfiguration(
    style = style,
    interactionSource = interactionSource,
    interactionTransformTarget = interactionTransformTarget,
    interactionTransformPivot = interactionTransformPivot,
    interactionReducedMotionPolicy = interactionReducedMotionPolicy,
  ),
  sampling = sampling,
  expandLayerBounds = expandLayerBounds,
)

@Poko
internal class GlassNodeConfiguration(
  val style: GlassStyle,
  val interactionSource: InteractionSource?,
  val interactionTransformTarget: GlassTransformTarget = GlassTransformTarget.MaterialOnly,
  val interactionTransformPivot: GlassTransformPivot = GlassTransformPivot.Pointer,
  val interactionReducedMotionPolicy: GlassReducedMotionPolicy = GlassReducedMotionPolicy.System,
)

internal object GlassHazeEffectFactory : HazeEffectFactory<GlassNodeConfiguration> {
  override fun createRenderer(): HazeEffectRenderer<GlassNodeConfiguration> = GlassRuntimeEffect()
}
