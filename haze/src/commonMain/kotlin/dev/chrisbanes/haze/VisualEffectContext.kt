// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.unit.Density

/**
 * Context provided to [VisualEffect] implementations, exposing the environment and state
 * needed to render effects without requiring effects to hold a reference to [HazeEffectNode].
 *
 * This interface provides access to:
 * - Layout metrics (size, position, layer bounds)
 * - Rendering context (density, platform context)
 * - Configuration (input scale)
 * - Content areas to blur
 * - Composition locals
 *
 * Effects should prefer using the properties on this interface rather than storing
 * references to the node directly.
 */
@ExperimentalHazeApi
public interface VisualEffectContext {
  /**
   * The underlying [HazeEffectNode]. Available for advanced cases and bridging,
   * but effects should prefer using the specific properties on this interface.
   */
  public val node: HazeEffectNode

  /**
   * The [Density] for the current composition, used for converting between Dp and Px.
   */
  public val density: Density

  /**
   * The platform-specific context (e.g., Android Context on Android).
   */
  public val platformContext: PlatformContext

  /**
   * The input scale configuration for this effect.
   */
  public val inputScale: HazeInputScale

  /**
   * The size of the effect node's content.
   */
  public val size: Size

  /**
   * The size of the layer used for rendering the effect.
   */
  public val layerSize: Size

  /**
   * The offset of the layer relative to the content.
   */
  public val layerOffset: Offset

  /**
   * The position of the effect node on screen.
   */
  public val positionOnScreen: Offset

  /**
   * The list of [HazeArea]s that this effect should render.
   */
  public val areas: List<HazeArea>

  /**
   * Reads the current value of a [ProvidableCompositionLocal].
   */
  public fun <T> currentValueOf(local: ProvidableCompositionLocal<T>): T

  public val isAttached: Boolean
}

@OptIn(ExperimentalHazeApi::class)
internal class NodeVisualEffectContext(override val node: HazeEffectNode) : VisualEffectContext {
  override val density: Density get() = node.requireDensity()
  override val platformContext: PlatformContext get() = node.requirePlatformContext()
  override val inputScale: HazeInputScale get() = node.inputScale
  override val size: Size get() = node.size
  override val layerSize: Size get() = node.layerSize
  override val layerOffset: Offset get() = node.layerOffset
  override val positionOnScreen: Offset get() = node.positionOnScreen
  override val areas: List<HazeArea> get() = node.areas
  override fun <T> currentValueOf(local: ProvidableCompositionLocal<T>): T = node.currentValueOf(local)
  override val isAttached: Boolean get() = node.isAttached
}
