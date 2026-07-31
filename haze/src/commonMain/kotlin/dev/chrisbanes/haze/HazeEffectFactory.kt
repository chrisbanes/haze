// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope

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

/**
 * Built-in renderer lifecycle hook.
 *
 * This is intentionally separate from the supported custom-renderer surface. It exposes only
 * node-owned resources and invalidation, never live source records or modifier nodes.
 */
@InternalHazeApi
public interface HazeEffectRendererLifecycle<Style> {
  public fun attach(scope: HazeEffectLifecycleScope): Unit = Unit
  public fun update(
    scope: HazeEffectLifecycleScope,
    style: Style,
    sampling: HazeSampling,
  ): Unit = Unit
  public fun detach(): Unit = Unit
}

/** Built-in-only resources used while a renderer is attached to one modifier node. */
@InternalHazeApi
public interface HazeEffectLifecycleScope {
  public val modifierSize: Size
  public val coroutineScope: CoroutineScope
  public fun requirePlatformContext(): PlatformContext
  public fun requireGraphicsContext(): GraphicsContext
  public fun requireDensity(): Density
  public fun <T> currentValueOf(local: CompositionLocal<T>): T
  public fun invalidateDraw()
  public fun invalidateLayerBounds()
}

/** Built-in-only draw hooks which are not part of the third-party renderer contract. */
@InternalHazeApi
public interface HazeEffectRendererDrawHooks<Style> {
  public fun HazeEffectRuntimeDrawScope.prepareDraw(style: Style): Unit = Unit
  public fun HazeEffectRuntimeDrawScope.drawForeground(style: Style): Unit = Unit
  public fun shouldDrawContentBehind(): Boolean = false
  public fun shouldClipToNodeBounds(): Boolean = false
  public fun shouldPreferClipToInputBounds(): Boolean = false
}

/** Built-in-only retained-output capability. */
@InternalHazeApi
public interface HazeEffectRendererRetainedOutput {
  public fun canDrawRetainedOutput(): Boolean
  public fun shouldDrawRetainedOutput(): Boolean = canDrawRetainedOutput()
  public fun clearRetainedOutput()
}

/** Built-in-only pointer and content-transform capability. */
@InternalHazeApi
public interface HazeEffectRendererInteraction {
  public val observesPointerEvents: Boolean
  public fun onPointerEvent(event: PointerEvent, scope: HazeEffectLifecycleScope)
  public fun onCancelPointerInput(scope: HazeEffectLifecycleScope)
  public fun currentContentTransform(): HazeEffectContentTransform =
    HazeEffectContentTransform.Identity
}

/** Built-in-only transform applied to a modifier's own content and effect output. */
@InternalHazeApi
public class HazeEffectContentTransform(
  public val scaleX: Float,
  public val scaleY: Float,
  public val pivot: Offset,
) {
  init {
    require(scaleX.isFinite() && scaleX > 0f) { "scaleX must be finite and greater than zero" }
    require(scaleY.isFinite() && scaleY > 0f) { "scaleY must be finite and greater than zero" }
    require(pivot.x.isFinite() && pivot.y.isFinite()) { "pivot must be finite" }
  }

  override fun equals(other: Any?): Boolean =
    other is HazeEffectContentTransform &&
      scaleX == other.scaleX &&
      scaleY == other.scaleY &&
      pivot == other.pivot

  override fun hashCode(): Int {
    var result = scaleX.hashCode()
    result = 31 * result + scaleY.hashCode()
    return 31 * result + pivot.hashCode()
  }

  public companion object {
    public val Identity: HazeEffectContentTransform =
      HazeEffectContentTransform(1f, 1f, Offset.Zero)
  }
}

/**
 * Opaque built-in-only identity for the currently drawable input capture.
 *
 * Renderers may compare instances for equality but cannot inspect live source handles.
 */
@InternalHazeApi
public interface HazeEffectInputSnapshot

/**
 * Built-in-only semantic draw capability.
 *
 * Source geometry and layers remain owned by core. Built-ins can record the selected input into
 * their own target [DrawScope] without receiving source handles.
 */
@InternalHazeApi
public interface HazeEffectRuntimeDrawScope : HazeEffectDrawScope {
  public val modifierSize: Size
  public val layerSize: Size
  public val layerOffset: Offset
  public val hasDrawableInput: Boolean
  public val inputSnapshot: HazeEffectInputSnapshot?
  public val coroutineScope: CoroutineScope
  public fun requirePlatformContext(): PlatformContext
  public fun requireGraphicsContext(): GraphicsContext
  public fun requireDensity(): Density = this
  public fun invalidateDraw()
  public fun DrawScope.drawInput()
}

/** Provides a temporary graphics layer to a built-in renderer and releases it after [block]. */
@InternalHazeApi
public inline fun <R> HazeEffectRuntimeDrawScope.withGraphicsLayer(
  block: (androidx.compose.ui.graphics.layer.GraphicsLayer) -> R,
): R {
  val graphicsContext = requireGraphicsContext()
  val layer = graphicsContext.createGraphicsLayer()
  return try {
    block(layer)
  } finally {
    graphicsContext.releaseGraphicsLayer(layer)
  }
}

@OptIn(InternalHazeApi::class)
internal class HazeEffectDrawScopeImpl(
  private val drawScope: DrawScope,
  private val node: HazeEffectNode,
  override val sampling: HazeSampling,
) : HazeEffectRuntimeDrawScope, DrawScope by drawScope {

  override val modifierBounds: Rect
    get() = Rect(offset = node.layerOffset, size = node.size)

  override val modifierSize: Size
    get() = node.size

  override val layerSize: Size
    get() = node.layerSize

  override val layerOffset: Offset
    get() = node.layerOffset

  override val hasDrawableInput: Boolean
    get() = node.hasDrawableSourceLayers()

  override val inputSnapshot: HazeEffectInputSnapshot?
    get() = node.inputSnapshot()

  override val coroutineScope: CoroutineScope
    get() = node.coroutineScope

  override fun drawInput() {
    with(this) { drawScope.drawInput() }
  }

  override fun DrawScope.drawInput() {
    val owner = this@HazeEffectDrawScopeImpl.node
    val effectPosition = owner.position
    translate(left = owner.layerOffset.x, top = owner.layerOffset.y) {
      for (area in owner.areas) {
        val sourcePosition = Snapshot.withoutReadObservation {
          area.coordinates.positionFor(owner.resolvedPositionStrategy)
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

  override fun <T> currentValueOf(local: CompositionLocal<T>): T {
    return node.currentValueOf(local)
  }

  override fun requirePlatformContext(): PlatformContext = node.requirePlatformContext()
  override fun requireGraphicsContext(): GraphicsContext = node.requireGraphicsContext()
  override fun requireDensity(): Density = node.requireDensity()
  override fun invalidateDraw() = node.invalidateVisualEffectDraw()
}

internal class HazeEffectLayoutScopeImpl(
  density: Density,
  private val node: HazeEffectNode,
  override val modifierBounds: Rect,
) : HazeEffectLayoutScope, Density by density {

  override fun <T> currentValueOf(local: CompositionLocal<T>): T {
    return node.currentValueOf(local)
  }
}

@OptIn(InternalHazeApi::class)
internal class HazeEffectLifecycleScopeImpl(
  private val node: HazeEffectNode,
) : HazeEffectLifecycleScope {
  override val modifierSize: Size get() = node.size
  override val coroutineScope: CoroutineScope get() = node.coroutineScope
  override fun requirePlatformContext(): PlatformContext = node.requirePlatformContext()
  override fun requireGraphicsContext(): GraphicsContext = node.requireGraphicsContext()
  override fun requireDensity(): Density = node.requireDensity()
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = node.currentValueOf(local)
  override fun invalidateDraw() = node.invalidateVisualEffectDraw()
  override fun invalidateLayerBounds() = node.invalidateVisualEffectLayerBounds()
}
