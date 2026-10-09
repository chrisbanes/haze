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
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.hazeEffect

/**
 * Draws a Glass material using an explicit Haze [input].
 *
 * [style] is a sequence of appearance writes. Each time this node resolves its style, Haze
 * initializes a fresh Regular response for the current system appearance, then runs
 * [LocalGlassStyle] → [style] into it, so the same Style may be shared by concurrent nodes. Each
 * node independently owns and animates the declared hover, focus, and press responses.
 *
 * Snapshot state read by a Style block is observed by this node: a change resolves the Style again
 * and redraws on the next frame without recomposition. Styles compare by identity, so `remember` or
 * hoist [style]. Invalid values throw [IllegalArgumentException] when the block runs. Built-in
 * Regular and Clear material responses update when the Compose host's system appearance changes.
 *
 * [input], [performanceMode], [expandLayerBounds], [interactionSource], [interactionTransformTarget],
 * [interactionTransformPivot], and [interactionReducedMotionPolicy] are node-owned modifier
 * mechanics rather than Style presentation. Recomposition replaces each value completely,
 * including a `null` interaction source.
 *
 * @param input Source-backed content, this modifier's own content, or an experimental Android
 * window backdrop with an optional source fallback. Without a fallback, unavailable or failed
 * native backdrop draws content unchanged and does not demand source capture.
 * @param style Explicit appearance applied after defaults and [LocalGlassStyle].
 * @param performanceMode Rendering-fidelity policy, or `null` to inherit [LocalHazePerformanceMode].
 * The default selects the fixed `Balanced` profile. Named and fixed modes select a normalized,
 * deterministic profile.
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
  performanceMode: HazePerformanceMode? = null,
  expandLayerBounds: Boolean = true,
  interactionSource: InteractionSource? = null,
  interactionTransformTarget: GlassTransformTarget = GlassTransformTarget.MaterialOnly,
  interactionTransformPivot: GlassTransformPivot = GlassTransformPivot.Pointer,
  interactionReducedMotionPolicy: GlassReducedMotionPolicy = GlassReducedMotionPolicy.System,
): Modifier = hazeGlass(
  factory = GlassHazeEffectFactory,
  input = input,
  style = style,
  performanceMode = performanceMode,
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
  performanceMode: HazePerformanceMode?,
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
    performanceMode = performanceMode,
    interactionSource = interactionSource,
    interactionTransformTarget = interactionTransformTarget,
    interactionTransformPivot = interactionTransformPivot,
    interactionReducedMotionPolicy = interactionReducedMotionPolicy,
  ),
  expandLayerBounds = expandLayerBounds,
)

@Poko
internal class GlassNodeConfiguration(
  val style: GlassStyle,
  val performanceMode: HazePerformanceMode? = null,
  val interactionSource: InteractionSource?,
  val interactionTransformTarget: GlassTransformTarget = GlassTransformTarget.MaterialOnly,
  val interactionTransformPivot: GlassTransformPivot = GlassTransformPivot.Pointer,
  val interactionReducedMotionPolicy: GlassReducedMotionPolicy = GlassReducedMotionPolicy.System,
)

internal object GlassHazeEffectFactory : HazeEffectFactory<GlassNodeConfiguration> {
  override fun createRenderer(): HazeEffectRenderer<GlassNodeConfiguration> = GlassRuntimeEffect()
}
