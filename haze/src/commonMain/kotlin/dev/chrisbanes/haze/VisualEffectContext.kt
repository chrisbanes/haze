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
import androidx.compose.ui.node.invalidateDraw
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
   * The position of the effect node on screen.
   */
  public val positionOnScreen: Offset

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
  public val rootBoundsOnScreen: Rect

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
   * The [VisualEffect] currently attached to this context.
   */
  public val visualEffect: VisualEffect

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
   * CoroutineScope to launch coroutines from
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

  override val positionOnScreen: Offset get() = node.positionOnScreen
  override val size: Size get() = node.size
  override val layerSize: Size get() = node.layerSize
  override val layerOffset: Offset get() = node.layerOffset
  override val rootBoundsOnScreen: Rect get() = node.rootBoundsOnScreen

  override val inputScale: HazeInputScale get() = node.inputScale
  override val windowId: Any? get() = node.windowId
  override val areas: List<HazeArea> get() = node.areas
  override val state: HazeState? get() = node.state

  override val visualEffect: VisualEffect get() = node.visualEffect

  override val coroutineScope: CoroutineScope get() = node.coroutineScope
  override fun requirePlatformContext(): PlatformContext = node.requirePlatformContext()
  override fun requireDensity(): Density = node.requireDensity()
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = node.currentValueOf(local)
  override fun requireGraphicsContext(): GraphicsContext = node.requireGraphicsContext()
  override fun invalidateDraw() = node.invalidateDraw()
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
