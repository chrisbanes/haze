// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectSurface
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.Poko

/**
 * Connects the adjacent traversal and effect nodes without giving Glass a core node handle.
 * Equal by [input] and [expandLayerBounds] only: a re-created modifier hands over the live connection instead of reconnecting.
 */
@Poko
internal class GlassMemberBinding(val input: HazeInput, val expandLayerBounds: Boolean) {
  var node: GlassMemberNode? = null
  var runtime: GlassRuntimeEffect? = null
  var surface: HazeEffectSurface? = null
  var material: ResolvedGlassStyle? = null
  var key: GlassMergeKey? = null

  fun publish(runtime: GlassRuntimeEffect, surface: HazeEffectSurface, material: ResolvedGlassStyle, key: GlassMergeKey) {
    this.runtime = runtime
    this.surface = surface
    if (this.material != material || this.key != key) {
      this.material = material
      this.key = key
      node?.changed()
    }
    node?.connect()
  }

  fun adopt(previous: GlassMemberBinding) {
    node = previous.node
    runtime = runtime ?: previous.runtime
    surface = surface ?: previous.surface
    material = material ?: previous.material
    key = key ?: previous.key
    previous.node = null
    previous.runtime = null
    previous.surface = null
    previous.material = null
    previous.key = null
  }

  fun release(runtime: GlassRuntimeEffect) {
    if (this.runtime !== runtime) return
    surface?.relocateTo(null)
    this.runtime = null
    surface = null
    material = null
    key = null
    node?.changed()
  }
}

/** Updates whenever the effect configuration does, so both nodes always hold the same binding. */
@Poko
internal class GlassMemberElement(
  val binding: GlassMemberBinding,
  val configuration: GlassNodeConfiguration,
  val factory: HazeEffectFactory<GlassNodeConfiguration>,
) : ModifierNodeElement<GlassMemberNode>() {
  override fun create() = GlassMemberNode(binding, configuration.memberTransition)
  override fun update(node: GlassMemberNode) {
    node.transition = configuration.memberTransition
    node.update(binding)
  }
  override fun InspectorInfo.inspectableProperties() {
    name = "hazeGlassMember"
  }
}

/** [transition] is read by the container when this member's glass appears and when it leaves. */
internal class GlassMemberNode(
  var binding: GlassMemberBinding,
  var transition: GlassMemberTransition,
) : Modifier.Node(), LayoutAwareModifierNode {
  var container: GlassContainerNode? = null
    private set

  override fun onAttach() {
    binding.node = this
    connect()
  }

  override fun onPlaced(coordinates: LayoutCoordinates) = connect()

  fun connect() {
    if (!isAttached) return
    val nearest = findNearestAncestor(GlassContainerTraversalKey) as? GlassContainerNode
    if (container !== nearest) {
      val moving = container != null
      container?.remove(this, leaving = false)
      container = nearest
      nearest?.add(this, appearing = !moving)
    }
    binding.surface?.relocateTo(nearest)
  }

  fun changed() {
    container?.memberChanged()
  }

  /** Rotation, scale or skew relative to [owner] cannot be represented by a merged field rect. */
  fun hasTranslationOnlyTransform(owner: LayoutCoordinates): Boolean {
    val matrix = Matrix()
    owner.transformFrom(requireLayoutCoordinates(), matrix)
    matrix[3, 0] = 0f
    matrix[3, 1] = 0f
    matrix[3, 2] = 0f
    return matrix.values.contentEquals(Matrix().values)
  }

  fun update(binding: GlassMemberBinding) {
    if (this.binding === binding) return
    if (this.binding == binding) {
      binding.adopt(this.binding)
      this.binding = binding
      return
    }
    this.binding.surface?.relocateTo(null)
    this.binding.runtime?.opticalSizeOverride = null
    this.binding.runtime?.transitionAlpha = 1f
    this.binding.node = null
    this.binding = binding
    if (isAttached) {
      binding.node = this
      connect()
    }
  }

  override fun onDetach() {
    container?.remove(this)
    container = null
    binding.surface?.relocateTo(null)
    binding.runtime?.opticalSizeOverride = null
    binding.runtime?.transitionAlpha = 1f
    binding.node = null
  }
}
