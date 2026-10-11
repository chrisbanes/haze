// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectSurface
import dev.chrisbanes.haze.HazeEffectSurfaceOwner
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.drawHazeEffectSurfaces

/**
 * Groups nearby, compatible Glass members belonging to this nearest container.
 *
 * All Glass output draws behind every container child. Backdrop input samples only drawing before
 * the container. [spacing] is finite and non-negative; zero disables bridges. Members use the
 * largest member's shortest side for optics, retaining their own styles and shape/colour values.
 *
 * [transitionSpec] and [reducedMotionPolicy] are stored for the transitions feature; membership
 * changes currently take effect immediately. Cap diagnostics follow [HazeLogger.enabled].
 */
@Stable
@ExperimentalHazeApi
public fun Modifier.hazeGlassContainer(
  spacing: Dp,
  transitionSpec: FiniteAnimationSpec<Float> = spring(),
  reducedMotionPolicy: GlassReducedMotionPolicy = GlassReducedMotionPolicy.System,
): Modifier {
  require(spacing.value.isFinite() && spacing.value >= 0f) { "spacing must be finite and non-negative" }
  return then(GlassContainerElement(spacing, transitionSpec, reducedMotionPolicy))
}

internal object GlassContainerTraversalKey

@Poko
internal class GlassContainerElement(
  val spacing: Dp,
  val transitionSpec: FiniteAnimationSpec<Float>,
  val reducedMotionPolicy: GlassReducedMotionPolicy,
) : ModifierNodeElement<GlassContainerNode>() {
  override fun create() = GlassContainerNode(spacing, transitionSpec, reducedMotionPolicy)
  override fun update(node: GlassContainerNode) {
    node.spacing = spacing
    node.transitionSpec = transitionSpec
    node.reducedMotionPolicy = reducedMotionPolicy
    node.memberChanged()
  }
  override fun InspectorInfo.inspectableProperties() {
    name = "hazeGlassContainer"
    properties["spacing"] = spacing
    properties["transitionSpec"] = transitionSpec
    properties["reducedMotionPolicy"] = reducedMotionPolicy
  }
}

internal class GlassContainerNode(
  var spacing: Dp,
  var transitionSpec: FiniteAnimationSpec<Float>,
  var reducedMotionPolicy: GlassReducedMotionPolicy,
) : Modifier.Node(), TraversableNode, DrawModifierNode, LayoutAwareModifierNode, HazeEffectSurfaceOwner {
  override val traverseKey: Any get() = GlassContainerTraversalKey
  override var surfaceCoordinates: LayoutCoordinates? = null
    private set
  private val members = mutableListOf<GlassMemberNode>()
  private var children: GraphicsLayer? = null
  private var refreshing = false
  private var overflow = emptyList<GlassClusterOverflow>()
  internal val renderer = GlassContainerRenderer(this)
  internal val spacingPx: Float get() = with(requireDensity()) { spacing.toPx() }
  internal var model = GlassClusterModel(emptyList(), null, emptyList())
    private set

  override fun onAttach() {
    children = requireGraphicsContext().createGraphicsLayer()
  }
  override fun onPlaced(coordinates: LayoutCoordinates) {
    surfaceCoordinates = coordinates
    memberChanged()
  }
  fun add(member: GlassMemberNode) {
    if (member !in members) members += member
    memberChanged()
  }
  fun remove(member: GlassMemberNode) {
    renderer.releaseMember(member)
    members.remove(member)
    member.binding.runtime?.opticalSizeOverride = null
    memberChanged()
  }
  fun memberChanged() {
    if (!isAttached || refreshing) return
    refreshing = true
    try {
      val rects = members.mapNotNull { it.binding.surface?.boundsInOwner() }
      val opticalSize = glassOpticalSize(rects)
      members.forEach { it.binding.runtime?.opticalSizeOverride = opticalSize }
      val snapshots = members.mapNotNull { member ->
        val binding = member.binding
        val rect = binding.surface?.boundsInOwner() ?: return@mapNotNull null
        val key = binding.key ?: return@mapNotNull null
        val material = binding.material ?: return@mapNotNull null
        GlassMemberSnapshot(member, rect, key, material)
      }
      model = clusterGlassMembers(snapshots, spacingPx)
      if (model.overflow != overflow) {
        overflow = model.overflow
        overflow.forEach { component ->
          HazeLogger.d("GlassContainer") { "Glass component exceeds cap $MAX_GLASS_MERGED_MEMBERS by ${component.excessCount}" }
        }
      }
      invalidateDraw()
    } finally {
      refreshing = false
    }
  }
  override fun onSurfaceChanged(surface: HazeEffectSurface) = memberChanged()
  override fun onSurfaceReleased(surface: HazeEffectSurface) = memberChanged()
  override fun canComposeNativeSurfaces(bounds: Rect): Boolean = renderer.canComposeNativeSurfaces(bounds)

  override fun ContentDrawScope.draw() {
    drawHazeEffectSurfaces(this@GlassContainerNode, { renderer.reconcile() }, checkNotNull(children))
  }

  override fun onDetach() {
    renderer.release()
    members.toList().forEach {
      it.binding.surface?.relocateTo(null)
      it.binding.runtime?.opticalSizeOverride = null
    }
    members.clear()
    children?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
    children = null
    surfaceCoordinates = null
  }
}
