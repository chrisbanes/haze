// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope

public interface VisualEffect {
  /**
   * Draws the effect using the provided [context].
   *
   * Implementations should use properties from [VisualEffectContext] rather than storing
   * references to [HazeEffectNode].
   */
  public fun DrawScope.drawEffect(context: VisualEffectContext)

  /**
   * Attaches this effect to the given node.
   */
  public fun attach(node: HazeEffectNode): Unit = Unit

  /**
   * Called when the effect should update its state from composition locals or other sources.
   *
   * Implementations should use [VisualEffectContext] to read composition locals and other state.
   */
  public fun update(context: VisualEffectContext): Unit = Unit

  /**
   * Detaches this effect from its node.
   */
  public fun detach(): Unit = Unit

  public fun DrawScope.shouldDrawContentBehind(context: VisualEffectContext): Boolean = false

  public fun shouldClip(): Boolean = false

  /**
   * Calculates the input scale factor to use for this effect given the provided [context].
   *
   * Implementations should use properties from [VisualEffectContext] rather than storing
   * references to [HazeEffectNode].
   */
  public fun calculateInputScaleFactor(context: VisualEffectContext): Float {
    return when (val scale = context.inputScale) {
      is HazeInputScale.None -> 1f
      is HazeInputScale.Fixed -> scale.scale
      HazeInputScale.Auto -> 1f
    }
  }

  public fun needInvalidation(): Boolean = false

  public fun preferClipToAreaBounds(): Boolean = false

  /**
   * Expands the layer rect for this effect using the provided [context].
   *
   * Implementations should use properties from [VisualEffectContext] rather than storing
   * references to [HazeEffectNode].
   *
   * The resulting rect should be in the same coordinate system of the passed in rect. i.e. the
   * content at [x,y] of [rect] should be the same content of the resulting rect.
   */
  public fun expandLayerRect(rect: Rect, context: VisualEffectContext): Rect = rect

  public companion object {
    /**
     * An empty and no-op visual effect that does nothing. Used as a placeholder when no
     * specific effect is selected.
     */
    public val Empty: VisualEffect get() = EmptyVisualEffect
  }
}

@OptIn(ExperimentalHazeApi::class)
private object EmptyVisualEffect : VisualEffect {
  override fun DrawScope.drawEffect(context: VisualEffectContext) {
    // No-op
  }
}
