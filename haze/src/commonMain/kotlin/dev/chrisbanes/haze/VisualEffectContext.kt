// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope

/**
 * Context provided to [VisualEffect] implementations during their lifecycle.
 *
 * This interface abstracts away the underlying node implementation, providing effects with
 * all the geometry, configuration, and accessor capabilities they need without direct
 * coupling to [HazeEffectNode].
 */
@ExperimentalHazeApi
public interface VisualEffectContext {

  // ==================== Geometry ====================

  /**
   * The position of the effect node.
   */
  public val position: Offset

  /**
   * The size of the effect node.
   */
  public val size: Size

  /**
   * The size of the graphics layer used for rendering the effect.
   * This may differ from [size] when layer bounds are expanded.
   */
  public val layerSize: Size

  /**
   * The offset of the graphics layer relative to the effect node's position.
   */
  public val layerOffset: Offset

  /**
   * The bounds of the root layout coordinates on screen.
   */
  public val rootBounds: Rect

  // ==================== Configuration ====================

  /**
   * The input scale factor configuration for this effect.
   */
  public val inputScale: HazeInputScale

  /**
   * An identifier for the window containing this effect, used for cross-window
   * invalidation tracking.
   */
  public val windowId: Any?

  /**
   * The list of [HazeArea]s that this effect should process.
   */
  public val areas: List<HazeArea>

  /**
   * The [HazeState] associated with this effect, if any.
   * When non-null, the effect operates in background blur mode.
   * When null, the effect operates in content (foreground) blur mode.
   */
  public val state: HazeState?

  /**
   * The resolved position strategy for this effect node.
   * This is the strategy computed from the configured strategy and the areas this effect observes.
   *
   * Default implementation returns [HazePositionStrategy.Local] for backward compatibility.
   */
  public val positionStrategy: HazePositionStrategy
    get() = HazePositionStrategy.Local

  // ==================== Area Geometry Helpers ====================

  /**
   * Returns the position of the given [area] in the coordinate space of this effect's
   * resolved position strategy.
   *
   * This is the preferred way for custom effects to read area positions, as it handles
   * coordinate space selection automatically.
   *
   * Default implementation returns [HazeArea.coordinates]'s local position for backward compatibility.
   */
  public fun positionOf(area: HazeArea): Offset {
    return area.coordinates.localPosition
  }

  /**
   * Returns the bounds of the given [area] in the coordinate space of this effect's
   * resolved position strategy, or `null` if the area's geometry is not yet specified.
   *
   * Default implementation computes bounds from local position for backward compatibility.
   */
  public fun boundsOf(area: HazeArea): Rect? {
    return area.coordinates.boundsFor(HazePositionStrategy.Local, area.size)
  }

  // ==================== Platform Accessors ====================

  /**
   * Returns the platform-specific context required for certain rendering operations.
   */
  @InternalHazeApi
  public fun requirePlatformContext(): PlatformContext

  /**
   * Returns the current [Density] for pixel-to-dp conversions.
   */
  public fun requireDensity(): Density

  /**
   * Returns the current value of the given [CompositionLocal].
   *
   * @param local The composition local to read.
   * @return The current value of the composition local.
   */
  public fun <T> currentValueOf(local: CompositionLocal<T>): T

  /**
   * Returns the [GraphicsContext] for creating and managing graphics layers.
   */
  public fun requireGraphicsContext(): GraphicsContext

  /**
   * CoroutineScope tied to the effect node lifecycle.
   *
   * Any jobs launched in this scope are cancelled when the owning node detaches.
   * Use this for short-lived effect work that should not outlive the node.
   */
  public val coroutineScope: CoroutineScope

  /**
   * Requests a redraw of the effect.
   */
  public fun invalidateDraw()
}

/**
 * Internal implementation of [VisualEffectContext] that wraps a [HazeEffectNode].
 */
@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal class HazeEffectNodeVisualEffectContext(
  internal val node: HazeEffectNode,
) : VisualEffectContext {

  override val position: Offset get() = node.position
  override val size: Size get() = node.size
  override val layerSize: Size get() = node.layerSize
  override val layerOffset: Offset get() = node.layerOffset
  override val rootBounds: Rect get() = node.rootBounds

  override val inputScale: HazeInputScale get() = node.inputScale
  override val windowId: Any? get() = node.windowId
  override val areas: List<HazeArea> get() = node.areas
  override val state: HazeState? get() = node.state
  override val positionStrategy: HazePositionStrategy get() = node.resolvedPositionStrategy

  override fun positionOf(area: HazeArea): Offset {
    return area.coordinates.positionFor(node.resolvedPositionStrategy)
  }

  override fun boundsOf(area: HazeArea): Rect? {
    return area.coordinates.boundsFor(node.resolvedPositionStrategy, area.size)
  }

  override val coroutineScope: CoroutineScope get() = node.coroutineScope
  override fun requirePlatformContext(): PlatformContext = node.requirePlatformContext()
  override fun requireDensity(): Density = node.requireDensity()
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = node.currentValueOf(local)
  override fun requireGraphicsContext(): GraphicsContext = node.requireGraphicsContext()
  override fun invalidateDraw() = node.invalidateHazeDraw(HazeInvalidationReason.VisualEffect)
}

// ==================== Optional Extension Helpers ====================

/**
 * Provides a [GraphicsLayer] for temporary use within the [block], automatically
 * releasing it when done.
 *
 * This is an optional helper that effects can use for efficient graphics layer management.
 */
@ExperimentalHazeApi
public inline fun <R> VisualEffectContext.withGraphicsLayer(block: (GraphicsLayer) -> R): R {
  val graphicsContext = requireGraphicsContext()
  val layer = graphicsContext.createGraphicsLayer()
  return try {
    block(layer)
  } finally {
    graphicsContext.releaseGraphicsLayer(layer)
  }
}
