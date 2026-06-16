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
 *
 * VisualEffect instances are single-owner and must not be attached to multiple
 * `Modifier.hazeEffect` nodes at the same time. Reusing the same effect instance
 * across concurrently active nodes will throw an [IllegalStateException].
 *
 * The built-in [Empty] singleton is exempt from this restriction and may be shared
 * safely across any number of nodes.
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
   * Geometry may not be resolved yet at this point. Implementations must tolerate
   * [VisualEffectContext.position], [VisualEffectContext.size], [VisualEffectContext.layerSize],
   * and [VisualEffectContext.layerOffset] being unspecified or zero during attach.
   */
  public fun attach(context: VisualEffectContext): Unit = Unit

  /**
   * Called when the effect should update its state from composition locals or other sources.
   *
   * You can safely read snapshot state in this function. When any snapshot state read in this
   * function is mutated, this function will be re-invoked.
   *
   * Commonly, this function will need to call [VisualEffectContext.invalidateDraw] when it detects
   * a scenario where the effect needs to be re-drawn.
   *
   * @param context The context providing access to composition locals and other state.
   */
  public fun update(context: VisualEffectContext): Unit = Unit

  /**
   * Called when this effect is detached from its context.
   *
   * Use this to release any resources acquired during [attach].
   */
  public fun detach(context: VisualEffectContext): Unit = Unit

  /**
   * Called when the system is running low on memory, or the app is being backgrounded.
   *
   * Implementations should release any heavy resources (such as cached bitmaps,
   * off-screen buffers, or native contexts) in response to the given [level].
   *
   * @param context The context providing access to geometry, configuration, and platform
   * capabilities. Use [VisualEffectContext.invalidateDraw] to request a redraw after
   * releasing resources.
   * @param level The severity of the memory-pressure event.
   */
  public fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel): Unit = Unit

  /**
   * Returns whether the source content should be drawn before the effect in foreground mode.
   *
   * This is called during drawing to determine draw order when [VisualEffectContext.state]
   * is null.
   *
   * @param context The context providing access to geometry, configuration, and platform
   * capabilities.
   */
  public fun shouldDrawContentBehind(context: VisualEffectContext): Boolean = false

  /**
   * Returns whether the effect output should be clipped to the node bounds.
   */
  public fun shouldClipToNodeBounds(): Boolean = false

  /**
   * Returns whether the effect prefers to clip to area bounds.
   */
  public fun shouldPreferClipToAreaBounds(): Boolean = false

  /**
   * Calculates the layer bounds required for this effect.
   *
   * The resulting rect should be in the same coordinate system as the passed in rect.
   * i.e. the content at [x,y] of [rect] should be the same content of the resulting rect.
   *
   * Coordinate-space note:
   * - In background mode (`context.state != null`), Haze passes a screen/root-aligned rect.
   * - In foreground mode (`context.state == null`), Haze passes a local node rect.
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

internal object EmptyVisualEffect : VisualEffect {
  override fun DrawScope.draw(context: VisualEffectContext) = Unit
}

internal interface RetainedOutputVisualEffect {
  fun canDrawRetainedOutput(context: VisualEffectContext): Boolean

  fun clearRetainedOutput()
}
