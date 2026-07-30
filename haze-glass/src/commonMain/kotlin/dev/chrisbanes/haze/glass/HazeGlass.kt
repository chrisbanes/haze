// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectFactoryVisualEffect
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectVisualEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InteractiveVisualEffect
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.RetainedOutputVisualEffect
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.VisualEffectTransform
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
  ): HazeEffectFactoryVisualEffect<GlassNodeConfiguration> {
    val configuration = GlassVisualEffect().apply {
      this.style = style.style
      interactionSource = style.interactionSource
      interactionLightRadiusFraction = style.interactionLightRadiusFraction
      interactionTransformTarget = style.interactionTransformTarget
      interactionTransformPivot = style.interactionTransformPivot
      interactionPositionAnimationSpec = style.interactionPositionAnimationSpec
      interactionReducedMotionPolicy = style.interactionReducedMotionPolicy
    }
    return GlassHazeEffectFactoryVisualEffect(
      configuration = configuration,
      renderer = configuration.createRenderer(),
    )
  }
}

internal class GlassHazeEffectFactoryVisualEffect(
  internal val configuration: GlassVisualEffect,
  internal val renderer: VisualEffect,
) : HazeEffectFactoryVisualEffect<GlassNodeConfiguration>,
  VisualEffect by renderer,
  InteractiveVisualEffect,
  RetainedOutputVisualEffect {

  private val interactiveRenderer: InteractiveVisualEffect?
    get() = renderer as? InteractiveVisualEffect

  private val retainedRenderer: RetainedOutputVisualEffect?
    get() = renderer as? RetainedOutputVisualEffect

  override fun updateStyle(style: GlassNodeConfiguration, sampling: HazeSampling) {
    configuration.style = style.style
    configuration.interactionSource = style.interactionSource
    configuration.interactionLightRadiusFraction = style.interactionLightRadiusFraction
    configuration.interactionTransformTarget = style.interactionTransformTarget
    configuration.interactionTransformPivot = style.interactionTransformPivot
    configuration.interactionPositionAnimationSpec = style.interactionPositionAnimationSpec
    configuration.interactionReducedMotionPolicy = style.interactionReducedMotionPolicy
  }

  override val observesPointerEvents: Boolean
    get() = interactiveRenderer?.observesPointerEvents == true

  override fun onPointerEvent(event: PointerEvent, context: VisualEffectContext) {
    interactiveRenderer?.onPointerEvent(event, context)
  }

  override fun onCancelPointerInput(context: VisualEffectContext) {
    interactiveRenderer?.onCancelPointerInput(context)
  }

  override fun currentContentTransform(context: VisualEffectContext): VisualEffectTransform =
    interactiveRenderer?.currentContentTransform(context) ?: VisualEffectTransform.Identity

  override fun canDrawRetainedOutput(context: VisualEffectContext): Boolean =
    retainedRenderer?.canDrawRetainedOutput(context) == true

  override fun shouldDrawRetainedOutput(context: VisualEffectContext): Boolean =
    retainedRenderer?.shouldDrawRetainedOutput(context) == true

  override fun clearRetainedOutput() {
    retainedRenderer?.clearRetainedOutput()
  }
}
