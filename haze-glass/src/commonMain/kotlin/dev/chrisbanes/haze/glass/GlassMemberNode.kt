// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.platform.InspectorInfo
import dev.chrisbanes.haze.HazeEffectSurface
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.Poko

/** Connects the adjacent traversal and effect nodes without giving Glass a core node handle. */
internal class GlassMemberBinding(val input: HazeInput) {
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

@Poko
internal class GlassMemberElement(val binding: GlassMemberBinding) : ModifierNodeElement<GlassMemberNode>() {
  override fun create() = GlassMemberNode(binding)
  override fun update(node: GlassMemberNode) = node.update(binding)
  override fun InspectorInfo.inspectableProperties() {
    name = "hazeGlassMember"
  }
}

internal class GlassMemberNode(var binding: GlassMemberBinding) : Modifier.Node(), LayoutAwareModifierNode {
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
      container?.remove(this)
      container = nearest
      nearest?.add(this)
    }
    binding.surface?.relocateTo(nearest)
  }

  fun changed() {
    container?.memberChanged()
  }

  fun update(binding: GlassMemberBinding) {
    if (this.binding === binding) return
    this.binding.surface?.relocateTo(null)
    this.binding.runtime?.opticalSizeOverride = null
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
    binding.node = null
  }
}
