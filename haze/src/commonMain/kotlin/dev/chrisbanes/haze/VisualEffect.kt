// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density

/**
 * A visual effect that can be applied to content behind or in front of a composable.
 *
 * Implementations receive a [VisualEffectContext] during their lifecycle which provides
 * access to geometry, configuration, and platform capabilities without direct coupling
 * to the underlying node implementation.
 */
@ExperimentalHazeApi
public interface VisualEffect {
  /**
   * Draws the effect.
   *
   * @param context The context providing access to geometry, configuration, and platform
   * capabilities for rendering the effect.
   */
  public fun DrawScope.draw(context: VisualEffectContext)

  /**
   * Called when this effect is attached to a context.
   *
   * Use this to initialize any resources or state needed for the effect.
   *
   * @param context The context this effect is being attached to.
   */
  public fun attach(context: VisualEffectContext): Unit = Unit

  /**
   * Called when the effect should update its state from composition locals or other sources.
   *
   * @param context The context providing access to composition locals and other state.
   */
  public fun update(context: VisualEffectContext): Unit = Unit

  /**
   * Called when this effect is detached from its context.
   *
   * Use this to release any resources acquired during [attach].
   */
  public fun detach(): Unit = Unit

  /**
   * Returns whether the content should be drawn behind the effect for foreground blurring.
   * This is called during drawing to determine draw order.
   *
   * @param context The context providing access to geometry, configuration, and platform
   * capabilities.
   */
  public fun DrawScope.shouldDrawContentBehind(context: VisualEffectContext): Boolean = false

  /**
   * Returns whether the effect output should be clipped to the node bounds.
   */
  public fun shouldClip(): Boolean = false

  /**
   * Calculates the input scale factor based on the given scale configuration.
   *
   * @param scale The scale configuration.
   * @return The calculated scale factor to apply.
   */
  public fun calculateInputScaleFactor(scale: HazeInputScale): Float = when (scale) {
    is HazeInputScale.None -> 1f
    is HazeInputScale.Fixed -> scale.scale
    HazeInputScale.Auto -> 1f
  }

  /**
   * Returns whether the effect requires draw invalidation.
   */
  public fun requireInvalidation(): Boolean = false

  /**
   * Returns whether the effect prefers to clip to area bounds.
   */
  public fun preferClipToAreaBounds(): Boolean = false

  /**
   * Calculates the layer bounds required for this effect.
   *
   * The resulting rect should be in the same coordinate system as the passed in rect.
   * i.e. the content at [x,y] of [rect] should be the same content of the resulting rect.
   *
   * @param rect The original bounds rect.
   * @param density The density to use for pixel conversions.
   * @return The expanded bounds required for the effect.
   */
  public fun calculateLayerBounds(rect: Rect, density: Density): Rect = rect

  public companion object {
    /**
     * An empty and no-op visual effect that does nothing. Used as a placeholder when no
     * specific effect is selected.
     */
    public val Empty: VisualEffect get() = EmptyVisualEffect
  }
}

private object EmptyVisualEffect : VisualEffect {
  override fun DrawScope.draw(context: VisualEffectContext) {
    // No-op
  }
}
