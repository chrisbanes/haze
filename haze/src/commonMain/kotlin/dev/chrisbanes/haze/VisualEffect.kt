// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope

@ExperimentalHazeApi
public interface VisualEffect {
  /**
   * Draws the effect.
   */
  public fun DrawScope.draw(node: HazeEffectNode)

  /**
   * Attaches this effect to the given node.
   */
  public fun attach(node: HazeEffectNode): Unit = Unit

  public fun update(): Unit = Unit

  /**
   * Detaches this effect from its node.
   */
  public fun detach(): Unit = Unit

  public fun DrawScope.shouldDrawContentBehind(): Boolean = false

  public fun shouldClip(): Boolean = false

  public fun calculateInputScaleFactor(scale: HazeInputScale): Float = when (scale) {
    is HazeInputScale.None -> 1f
    is HazeInputScale.Fixed -> scale.scale
    HazeInputScale.Auto -> 1f
  }

  public fun requireInvalidation(): Boolean = false

  public fun preferClipToAreaBounds(): Boolean = false

  /**
   * The resulting rect should be in the same coordinate system of the passed in rect. i.e. the
   * content at [x,y] of [rect] should be the same content of the resulting rect.
   */
  public fun calculateLayerBounds(rect: Rect): Rect = rect

  public companion object {
    /**
     * An empty and no-op visual effect that does nothing. Used as a placeholder when no
     * specific effect is selected.
     */
    public val Empty: VisualEffect get() = EmptyVisualEffect
  }
}

private object EmptyVisualEffect : VisualEffect {
  override fun DrawScope.draw(node: HazeEffectNode) {
    // No-op
  }
}
