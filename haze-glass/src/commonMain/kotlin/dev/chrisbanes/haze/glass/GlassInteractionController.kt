// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import dev.chrisbanes.haze.VisualEffectContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class GlassInteractionSlots(
  val focused: GlassInteractionSlot? = null,
  val hovered: GlassInteractionSlot? = null,
  val pressed: GlassInteractionSlot? = null,
)

internal enum class GlassInteractionState {
  Focused,
  Hovered,
  Pressed,
}

internal data class GlassInteractionSignals(
  val rawHovered: Boolean = false,
  val sourceHovered: Boolean = false,
  val sourceFocused: Boolean = false,
  val rawPressed: Boolean = false,
  val sourcePressed: Boolean = false,
) {
  val hovered: Boolean get() = rawHovered || sourceHovered
  val focused: Boolean get() = sourceFocused
  val pressed: Boolean get() = rawPressed || sourcePressed

  fun isActive(state: GlassInteractionState): Boolean = when (state) {
    GlassInteractionState.Focused -> focused
    GlassInteractionState.Hovered -> hovered
    GlassInteractionState.Pressed -> pressed
  }
}

internal data class OwnedGlassResponseValue(
  val state: GlassInteractionState?,
  val owner: GlassInteractionSlot?,
  val declaration: GlassResponseValue?,
  val identity: Float,
) {
  val value: Float get() = declaration?.value ?: identity
}

internal data class GlassInteractionTargets(
  val lightingIntensity: OwnedGlassResponseValue,
  val refractionMultiplier: OwnedGlassResponseValue,
  val whitePointDelta: OwnedGlassResponseValue,
  val scaleX: OwnedGlassResponseValue,
  val scaleY: OwnedGlassResponseValue,
)

internal data class GlassInteractionRenderState(
  val position: Offset,
  val lightingIntensity: Float = 0f,
  val refractionMultiplier: Float = 1f,
  val whitePointDelta: Float = 0f,
  val scaleX: Float = 1f,
  val scaleY: Float = 1f,
) {
  val hasLighting: Boolean get() = lightingIntensity > 0f
  val hasOptics: Boolean get() = refractionMultiplier != 1f || whitePointDelta != 0f
  val hasTransform: Boolean get() = scaleX != 1f || scaleY != 1f
}

internal data class GlassInteractionControllerConfiguration(
  val slots: GlassInteractionSlots,
  val positionAnimationSpec: FiniteAnimationSpec<Offset>,
  val reducedMotion: Boolean,
  val forceFullMotion: Boolean,
)

internal fun resolveGlassInteractionTargets(
  slots: GlassInteractionSlots,
  signals: GlassInteractionSignals,
): GlassInteractionTargets {
  fun resolve(
    identity: Float,
    property: (GlassInteractionResponse) -> GlassResponseValue?,
  ): OwnedGlassResponseValue {
    var result = OwnedGlassResponseValue(null, null, null, identity)
    fun apply(
      state: GlassInteractionState,
      active: Boolean,
      slot: GlassInteractionSlot?,
    ) {
      if (!active || slot == null) return
      property(slot.response)?.let { declaration ->
        result = OwnedGlassResponseValue(state, slot, declaration, identity)
      }
    }
    apply(GlassInteractionState.Focused, signals.focused, slots.focused)
    apply(GlassInteractionState.Hovered, signals.hovered, slots.hovered)
    apply(GlassInteractionState.Pressed, signals.pressed, slots.pressed)
    return result
  }

  return GlassInteractionTargets(
    lightingIntensity = resolve(0f, GlassInteractionResponse::lightingIntensity),
    refractionMultiplier = resolve(1f, GlassInteractionResponse::refractionMultiplier),
    whitePointDelta = resolve(0f, GlassInteractionResponse::whitePointDelta),
    scaleX = resolve(1f, GlassInteractionResponse::scaleX),
    scaleY = resolve(1f, GlassInteractionResponse::scaleY),
  )
}

internal fun selectTransitionSpec(
  previous: OwnedGlassResponseValue,
  next: OwnedGlassResponseValue,
  previousSlots: GlassInteractionSlots,
  nextSlots: GlassInteractionSlots,
  previousSignals: GlassInteractionSignals,
  nextSignals: GlassInteractionSignals,
): FiniteAnimationSpec<Float>? {
  if (previous == next) return null

  fun GlassInteractionSlots.slot(state: GlassInteractionState?): GlassInteractionSlot? =
    when (state) {
      GlassInteractionState.Focused -> focused
      GlassInteractionState.Hovered -> hovered
      GlassInteractionState.Pressed -> pressed
      null -> null
    }

  val nextStateEntered = next.state != null &&
    !previousSignals.isActive(next.state) &&
    nextSignals.isActive(next.state)
  val nextResponseIsNew = next.owner != null &&
    previousSlots.slot(next.state)?.revision != next.owner.revision &&
    nextSlots.slot(next.state)?.revision == next.owner.revision

  return when {
    nextStateEntered || nextResponseIsNew -> next.declaration?.toSpec
    previous.declaration != null -> previous.declaration.fromSpec
    else -> next.declaration?.toSpec
  }
}

internal class GlassInteractionController(
  context: VisualEffectContext,
) {
  private val scope = context.coroutineScope
  private val invalidateDraw = context::invalidateDraw
  private val lightingIntensity = AnimatedFloatChannel(0f, scope, invalidateDraw)
  private val refractionMultiplier = AnimatedFloatChannel(1f, scope, invalidateDraw)
  private val whitePointDelta = AnimatedFloatChannel(0f, scope, invalidateDraw)
  private val scaleX = AnimatedFloatChannel(1f, scope, invalidateDraw)
  private val scaleY = AnimatedFloatChannel(1f, scope, invalidateDraw)
  private val position = Animatable(Offset.Zero, Offset.VectorConverter)

  private var configuration = GlassInteractionControllerConfiguration(
    slots = GlassInteractionSlots(),
    positionAnimationSpec = GlassDefaults.positionAnimationSpec,
    reducedMotion = false,
    forceFullMotion = false,
  )
  private var signals = GlassInteractionSignals()
  private var positionTarget = Offset.Zero
  private var positionJob: Job? = null
  private var disposed = false

  internal val isDisposedForTest: Boolean get() = disposed

  val renderState: GlassInteractionRenderState
    get() = GlassInteractionRenderState(
      position = position.value,
      lightingIntensity = lightingIntensity.currentValue,
      refractionMultiplier = refractionMultiplier.currentValue,
      whitePointDelta = whitePointDelta.currentValue,
      scaleX = if (configuration.reducedMotion) 1f else scaleX.currentValue,
      scaleY = if (configuration.reducedMotion) 1f else scaleY.currentValue,
    )

  fun updateConfiguration(configuration: GlassInteractionControllerConfiguration) {
    if (disposed || configuration == this.configuration) return
    val previous = this.configuration
    this.configuration = configuration
    retarget(
      previousSlots = previous.slots,
      nextSlots = configuration.slots,
      previousSignals = signals,
      nextSignals = signals,
    )
    if (!previous.reducedMotion && configuration.reducedMotion) {
      snapActiveTargetsForReducedMotion()
      retargetPosition(positionTarget, force = true)
    }
  }

  fun updateSignals(signals: GlassInteractionSignals) {
    if (disposed || signals == this.signals) return
    val previous = this.signals
    this.signals = signals
    retarget(
      previousSlots = configuration.slots,
      nextSlots = configuration.slots,
      previousSignals = previous,
      nextSignals = signals,
    )
  }

  fun updatePosition(position: Offset) {
    if (disposed || !position.isSpecified) return
    retargetPosition(position)
  }

  fun dispose() {
    if (disposed) return
    disposed = true
    lightingIntensity.cancel()
    refractionMultiplier.cancel()
    whitePointDelta.cancel()
    scaleX.cancel()
    scaleY.cancel()
    positionJob?.cancel()
    positionJob = null
  }

  private fun retarget(
    previousSlots: GlassInteractionSlots,
    nextSlots: GlassInteractionSlots,
    previousSignals: GlassInteractionSignals,
    nextSignals: GlassInteractionSignals,
  ) {
    val targets = resolveGlassInteractionTargets(nextSlots, nextSignals)
    lightingIntensity.retarget(
      targets.lightingIntensity,
      previousSlots,
      nextSlots,
      previousSignals,
      nextSignals,
      configuration.reducedMotion,
      configuration.forceFullMotion,
    )
    refractionMultiplier.retarget(
      targets.refractionMultiplier,
      previousSlots,
      nextSlots,
      previousSignals,
      nextSignals,
      configuration.reducedMotion,
      configuration.forceFullMotion,
    )
    whitePointDelta.retarget(
      targets.whitePointDelta,
      previousSlots,
      nextSlots,
      previousSignals,
      nextSignals,
      configuration.reducedMotion,
      configuration.forceFullMotion,
    )
    scaleX.retarget(
      targets.scaleX,
      previousSlots,
      nextSlots,
      previousSignals,
      nextSignals,
      configuration.reducedMotion,
      configuration.forceFullMotion,
    )
    scaleY.retarget(
      targets.scaleY,
      previousSlots,
      nextSlots,
      previousSignals,
      nextSignals,
      configuration.reducedMotion,
      configuration.forceFullMotion,
    )
  }

  private fun snapActiveTargetsForReducedMotion() {
    val targets = resolveGlassInteractionTargets(configuration.slots, signals)
    lightingIntensity.snapTo(targets.lightingIntensity)
    refractionMultiplier.snapTo(targets.refractionMultiplier)
    whitePointDelta.snapTo(targets.whitePointDelta)
    scaleX.snapTo(targets.scaleX)
    scaleY.snapTo(targets.scaleY)
  }

  private fun retargetPosition(target: Offset, force: Boolean = false) {
    if (!force && target == positionTarget) return
    positionTarget = target
    positionJob?.cancel()
    positionJob = scope.launch(
      if (configuration.forceFullMotion) FullMotionDurationScale else EmptyCoroutineContext,
    ) {
      if (configuration.reducedMotion) {
        position.snapTo(target)
        invalidateDraw()
      } else {
        position.animateTo(target, configuration.positionAnimationSpec) {
          invalidateDraw()
        }
      }
    }
  }
}

private class AnimatedFloatChannel(
  identity: Float,
  private val scope: CoroutineScope,
  private val invalidateDraw: () -> Unit,
) {
  private val value = Animatable(identity)
  private var owner = OwnedGlassResponseValue(null, null, null, identity)
  private var job: Job? = null

  val currentValue: Float get() = value.value

  fun retarget(
    target: OwnedGlassResponseValue,
    previousSlots: GlassInteractionSlots,
    nextSlots: GlassInteractionSlots,
    previousSignals: GlassInteractionSignals,
    nextSignals: GlassInteractionSignals,
    reducedMotion: Boolean,
    forceFullMotion: Boolean,
  ) {
    if (target == owner) return
    val previous = owner
    owner = target
    val spec = selectTransitionSpec(
      previous = previous,
      next = target,
      previousSlots = previousSlots,
      nextSlots = nextSlots,
      previousSignals = previousSignals,
      nextSignals = nextSignals,
    )
    job?.cancel()
    job = scope.launch(if (forceFullMotion) FullMotionDurationScale else EmptyCoroutineContext) {
      if (reducedMotion || spec == null) {
        value.snapTo(target.value)
        invalidateDraw()
      } else {
        value.animateTo(target.value, spec) {
          invalidateDraw()
        }
      }
    }
  }

  fun snapTo(target: OwnedGlassResponseValue) {
    owner = target
    job?.cancel()
    job = scope.launch {
      value.snapTo(target.value)
      invalidateDraw()
    }
  }

  fun cancel() {
    job?.cancel()
    job = null
  }
}

private object FullMotionDurationScale : MotionDurationScale {
  override val scaleFactor: Float = 1f
}
