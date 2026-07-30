// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density

/**
 * A stateless, shareable descriptor that creates a renderer for one `hazeEffect` modifier node.
 *
 * The factory may be shared by any number of nodes. Each call to [createRenderer] must return an
 * independently owned renderer.
 */
public fun interface HazeEffectFactory<Style> {
  public fun createRenderer(): HazeEffectRenderer<Style>
}

/**
 * Built-in-only bridge for effects that require the complete legacy [VisualEffect] lifecycle.
 *
 * This keeps pointer input, foreground drawing, retained output, and platform resources internal
 * while ordinary custom effects continue to use [HazeEffectRenderer]'s semantic scopes.
 */
@InternalHazeApi
public interface HazeEffectVisualEffectFactory<Style> {
  public fun createVisualEffect(
    style: Style,
    sampling: HazeSampling,
  ): HazeEffectFactoryVisualEffect<Style>
}

/**
 * Node-owned full-runtime effect created by [HazeEffectVisualEffectFactory].
 *
 * Haze calls [updateStyle] with the complete replacement Style and sampling policy. Implementations
 * must not retain Style evaluation state outside this node-owned runtime.
 */
@InternalHazeApi
public interface HazeEffectFactoryVisualEffect<Style> : VisualEffect {
  public fun updateStyle(style: Style, sampling: HazeSampling)
}

/**
 * Node-owned rendering state for a custom Haze effect.
 *
 * Haze passes the complete current [Style] to every evaluation. Mutable rendering resources may
 * be held by the renderer and released in [dispose], but must not be stored in the factory or
 * Style.
 */
public interface HazeEffectRenderer<Style> {

  /** Draws the effect for the complete current [style]. */
  public fun HazeEffectDrawScope.draw(style: Style)

  /**
   * Returns the layer bounds required by the effect.
   *
   * The returned rect uses the same coordinate space as [HazeEffectLayoutScope.modifierBounds].
   */
  public fun HazeEffectLayoutScope.calculateLayerBounds(style: Style): Rect = modifierBounds

  /** Releases cached resources in response to system memory pressure. */
  public fun onTrimMemory(level: TrimMemoryLevel): Unit = Unit

  /**
   * Releases all renderer-owned resources.
   *
   * Haze calls this once when the factory is replaced or the modifier node detaches.
   */
  public fun dispose(): Unit = Unit
}

/**
 * Semantic drawing scope for a custom Haze effect.
 *
 * Source geometry and captured layers remain internal. Use [drawInput] to draw the input selected
 * by the modifier's [HazeInput].
 */
public interface HazeEffectDrawScope : DrawScope {

  /** Bounds of the modifier in the current effect layer. */
  public val modifierBounds: Rect

  /** Input-sampling policy supplied to the typed `hazeEffect` modifier. */
  public val sampling: HazeSampling

  /** Draws the modifier's selected source-backed or own-content input. */
  public fun drawInput()

  /** Returns the current value of [local] and observes it for redraw. */
  public fun <T> currentValueOf(local: CompositionLocal<T>): T
}

/**
 * Semantic layer-layout scope for a custom Haze effect.
 *
 * [modifierBounds] is initially aligned to the modifier. Bounds returned by
 * [HazeEffectRenderer.calculateLayerBounds] define the required effect layer around it.
 */
public interface HazeEffectLayoutScope : Density {

  /** Bounds of the modifier in the effect layer's coordinate space. */
  public val modifierBounds: Rect

  /** Returns the current value of [local] and observes it for bounds recalculation. */
  public fun <T> currentValueOf(local: CompositionLocal<T>): T
}

internal interface TypedHazeEffectVisualEffect : RetainedOutputVisualEffect {
  fun update(style: Any?, sampling: HazeSampling)
  fun onTrimMemory(level: TrimMemoryLevel)

  override fun canDrawRetainedOutput(context: VisualEffectContext): Boolean = true
  override fun clearRetainedOutput() = Unit
}

@OptIn(ExperimentalHazeApi::class)
internal class TypedHazeEffectVisualEffectImpl<Style>(
  private val renderer: HazeEffectRenderer<Style>,
  style: Style,
  private var sampling: HazeSampling,
) : VisualEffect, TypedHazeEffectVisualEffect {
  private var style: Style = style
  private var context: VisualEffectContext? = null
  private var disposed = false

  override fun attach(context: VisualEffectContext) {
    this.context = context
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    val scope = HazeEffectDrawScopeImpl(
      drawScope = this,
      context = context,
      sampling = sampling,
    )
    with(renderer) {
      scope.draw(style)
    }
  }

  override fun calculateLayerBounds(rect: Rect, density: Density): Rect {
    val context = checkNotNull(context) { "Typed Haze renderer is not attached" }
    val modifierBounds = Rect(offset = Offset.Zero, size = rect.size)
    val scope = HazeEffectLayoutScopeImpl(
      density = density,
      context = context,
      modifierBounds = modifierBounds,
    )
    val requiredBounds = with(renderer) {
      scope.calculateLayerBounds(style)
    }
    return Rect(
      offset = rect.topLeft + requiredBounds.topLeft,
      size = requiredBounds.size,
    )
  }

  override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
    onTrimMemory(level)
  }

  override fun onTrimMemory(level: TrimMemoryLevel) {
    renderer.onTrimMemory(level)
  }

  override fun detach(context: VisualEffectContext) {
    this.context = null
    if (!disposed) {
      disposed = true
      renderer.dispose()
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun update(style: Any?, sampling: HazeSampling) {
    this.style = style as Style
    this.sampling = sampling
  }
}

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal class HazeEffectFactoryVisualEffectBridge<Style>(
  private val effect: HazeEffectFactoryVisualEffect<Style>,
) : VisualEffect, TypedHazeEffectVisualEffect, InteractiveVisualEffect {
  private var attachedContext: VisualEffectContext? = null

  override val observesPointerEvents: Boolean
    get() = (effect as? InteractiveVisualEffect)?.observesPointerEvents == true

  override fun attach(context: VisualEffectContext) {
    effect.attach(context)
    attachedContext = context
  }

  override fun update(context: VisualEffectContext) {
    effect.update(context)
  }

  override fun DrawScope.prepareDraw(context: VisualEffectContext) {
    with(effect) { prepareDraw(context) }
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    with(effect) { draw(context) }
  }

  override fun DrawScope.drawForeground(context: VisualEffectContext) {
    with(effect) { drawForeground(context) }
  }

  override fun shouldDrawContentBehind(context: VisualEffectContext): Boolean =
    effect.shouldDrawContentBehind(context)

  override fun shouldClipToNodeBounds(): Boolean = effect.shouldClipToNodeBounds()

  override fun shouldPreferClipToAreaBounds(): Boolean = effect.shouldPreferClipToAreaBounds()

  override fun calculateLayerBounds(rect: Rect, density: Density): Rect =
    effect.calculateLayerBounds(rect, density)

  override fun onPointerEvent(event: androidx.compose.ui.input.pointer.PointerEvent, context: VisualEffectContext) {
    (effect as? InteractiveVisualEffect)?.onPointerEvent(event, context)
  }

  override fun onCancelPointerInput(context: VisualEffectContext) {
    (effect as? InteractiveVisualEffect)?.onCancelPointerInput(context)
  }

  override fun currentContentTransform(context: VisualEffectContext): VisualEffectTransform =
    (effect as? InteractiveVisualEffect)?.currentContentTransform(context)
      ?: VisualEffectTransform.Identity

  override fun canDrawRetainedOutput(context: VisualEffectContext): Boolean =
    (effect as? RetainedOutputVisualEffect)?.canDrawRetainedOutput(context) == true

  override fun shouldDrawRetainedOutput(context: VisualEffectContext): Boolean =
    (effect as? RetainedOutputVisualEffect)?.shouldDrawRetainedOutput(context) == true

  override fun clearRetainedOutput() {
    (effect as? RetainedOutputVisualEffect)?.clearRetainedOutput()
  }

  override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
    effect.onTrimMemory(context, level)
  }

  override fun onTrimMemory(level: TrimMemoryLevel) {
    attachedContext?.let { effect.onTrimMemory(it, level) }
  }

  override fun detach(context: VisualEffectContext) {
    try {
      effect.detach(context)
    } finally {
      attachedContext = null
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun update(style: Any?, sampling: HazeSampling) {
    effect.updateStyle(style as Style, sampling)
  }
}

private class HazeEffectDrawScopeImpl(
  private val drawScope: DrawScope,
  private val context: VisualEffectContext,
  override val sampling: HazeSampling,
) : HazeEffectDrawScope, DrawScope by drawScope {

  override val modifierBounds: Rect
    get() = Rect(offset = context.layerOffset, size = context.size)

  override fun drawInput() {
    val layerOffset = context.layerOffset
    val effectPosition = context.position
    val areas = context.areas
    with(drawScope) {
      translate(left = layerOffset.x, top = layerOffset.y) {
        for (area in areas) {
          val sourcePosition = Snapshot.withoutReadObservation {
            this@HazeEffectDrawScopeImpl.context.positionOf(area)
          }
          val relativePosition = (
            sourcePosition.takeIf(Offset::isSpecified) ?: Offset.Zero
            ) - effectPosition
          translate(left = relativePosition.x, top = relativePosition.y) {
            val layer = area.contentLayer
              ?.takeUnless { it.isReleased }
              ?.takeUnless { it.size.width <= 0 || it.size.height <= 0 }
            if (layer != null) {
              drawLayer(layer)
            }
          }
        }
      }
    }
  }

  override fun <T> currentValueOf(local: CompositionLocal<T>): T {
    return context.currentValueOf(local)
  }
}

private class HazeEffectLayoutScopeImpl(
  density: Density,
  private val context: VisualEffectContext,
  override val modifierBounds: Rect,
) : HazeEffectLayoutScope, Density by density {

  override fun <T> currentValueOf(local: CompositionLocal<T>): T {
    return context.currentValueOf(local)
  }
}
