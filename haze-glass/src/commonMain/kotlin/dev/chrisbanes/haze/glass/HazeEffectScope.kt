// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectScope

/**
 * Configures a [GlassVisualEffect] for this effect scope.
 *
 * The outermost call for an effect owns its interaction-slot transaction. If its [block] throws,
 * interaction-slot mutations, such as [GlassVisualEffect.hovered], are rolled back. Nested calls
 * share that transaction, so catching a nested call's exception does not create a savepoint or
 * roll back its mutations. Direct property writes, such as [GlassVisualEffect.tint] or
 * [GlassVisualEffect.optics], remain applied even when the owning transaction rolls back.
 */
@ExperimentalHazeApi
public inline fun HazeEffectScope.glassEffect(
  block: GlassVisualEffect.() -> Unit = {},
) {
  val effect = visualEffect as? GlassVisualEffect ?: GlassVisualEffect()
  visualEffect = effect
  val ownsTransaction = effect.beginInteractionSlotTransaction()
  var failure: Throwable? = null
  try {
    effect.block()
  } catch (throwable: Throwable) {
    failure = throwable
    effect.rollbackInteractionSlotTransaction(ownsTransaction)
    throw throwable
  } finally {
    if (failure == null) {
      effect.commitInteractionSlotTransaction(ownsTransaction)
    }
  }
}
