// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
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
import dev.chrisbanes.haze.HazeEffectContentTransform
import dev.chrisbanes.haze.HazeEffectSurface
import dev.chrisbanes.haze.HazeEffectSurfaceOwner
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.drawHazeEffectSurfaces
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Groups nearby, compatible Glass members belonging to this nearest container.
 *
 * All Glass output draws behind every container child. Backdrop input samples only drawing before
 * the container. [spacing] is finite and non-negative; zero disables bridges. Members use the
 * largest member's shortest side for optics, retaining their own styles and shape/colour values.
 *
 * Without RuntimeShader support, or when a cluster exceeds the render budget, each cluster draws
 * as one surface clipped to the hard union of its members, with no bridge. Members keep their own
 * tint and background; where members overlap, the earlier registered member's colours apply.
 *
 * Members that attach or detach after the container's first draw animate their glass as each
 * member's [GlassMemberTransition] declares, timed by [transitionSpec]. Under reduced motion, as
 * resolved by [reducedMotionPolicy], the transition keeps its morph with a critically damped
 * spring, even when the system removes animations. A detached member's glass is drawn by the
 * container through a live member with the same input, resolving the same sources, until its
 * animation ends; when no such member remains, for example when the last member leaves, the glass disappears immediately, as
 * it does for a member that is rotated or scaled. A transition ends without animating when the
 * member it grows from, retracts into or is drawn through leaves. A member moving to another
 * container appears there immediately. A running transition ends once its member is no longer
 * within [spacing] of its neighbour; [transitionSpec] and [reducedMotionPolicy] changes apply from
 * the next transition.
 * Leaving members never count towards the shared optical size. Haze animates only the glass, so
 * callers animate member content themselves, for example with `AnimatedVisibility`. Cap
 * diagnostics follow [HazeLogger.enabled].
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

  /** Registration order: live members, and ghosts in the position of the member that left. */
  private val registrations = mutableListOf<Any>()
  private val members: List<GlassMemberNode> get() = registrations.filterIsInstance<GlassMemberNode>()

  /** Members whose glass has not appeared yet; their transition starts with their first snapshot. */
  private val pending = mutableSetOf<GlassMemberNode>()
  private val attaching = mutableMapOf<GlassMemberNode, GlassMemberAttach>()

  /** Each registration's last drawn snapshot; a leaving member's surface may already be released. */
  private val latest = mutableMapOf<Any, GlassMemberSnapshot>()

  /** Members present before the first draw appear without a transition. */
  private var settled = false
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

  /** [appearing] is false when the member moves from another container, where it was visible. */
  fun add(member: GlassMemberNode, appearing: Boolean = true) {
    if (member !in registrations) {
      registrations += member
      if (appearing) pending += member
    }
    memberChanged()
  }

  /** [leaving] is false when the member moves to another container, where it stays visible. */
  fun remove(member: GlassMemberNode, leaving: Boolean = true) {
    renderer.releaseMember(member)
    pending -= member
    attaching.remove(member)?.job?.cancel()
    // Transitions with the leaving member end now rather than retargeting: a growing member snaps
    // to its own shape and a ghost drawn through the leaving member disappears.
    attaching.values.filter { it.neighbour === member }.forEach { it.job?.cancel() }
    attaching.values.removeAll { it.neighbour === member }
    endGhosts { it.anchor === member }
    member.binding.runtime?.let {
      it.opticalSizeOverride = null
      it.transitionAlpha = 1f
    }
    val last = latest.remove(member)
    val index = registrations.indexOf(member)
    if (index >= 0) {
      val ghost = last?.takeIf { leaving }?.let { ghostOf(member, it) }
      if (ghost != null) registrations[index] = ghost else registrations.removeAt(index)
    }
    memberChanged()
  }

  private fun endGhosts(predicate: (GlassMemberGhost) -> Boolean) {
    val ended = registrations.filterIsInstance<GlassMemberGhost>().filter(predicate)
    ended.forEach {
      it.job?.cancel()
      latest.remove(it)
    }
    registrations.removeAll(ended)
  }

  private fun ghostOf(member: GlassMemberNode, last: GlassMemberSnapshot): GlassMemberGhost? {
    // An axis-aligned field rect cannot draw a rotated or scaled member's glass.
    if (!isAttached || !settled || member.transition == GlassMemberTransition.None || !last.translationOnly) return null
    val ordered = registrations.mapNotNull { if (it === member) last else latest[it] }
    // A ghost draws through its anchor's input, so the anchor must resolve the same sources.
    val neighbour = if (member.transition == GlassMemberTransition.MatchedGeometry) resolveGlassNeighbour(last, ordered, spacingPx) else null
    // A detached member has released its own renderer, so a live member with the same input draws it.
    val anchor = neighbour ?: resolveGlassBorrowedAnchor(last, ordered.filter { it.drawsSourcesOf(last) }) ?: return null
    val ghost = GlassMemberGhost(last, neighbour?.identity as GlassMemberNode?, anchor.identity as GlassMemberNode)
    ghost.job = animate(ghost.progress, 0f) {
      registrations.remove(ghost)
      latest.remove(ghost)
    }
    return ghost
  }

  private fun animate(progress: Animatable<Float, AnimationVector1D>, target: Float, onEnd: () -> Unit): Job {
    val systemScale = coroutineScope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val (reduced, full) = reducedMotion(reducedMotionPolicy, systemScale)
    // Reduced motion keeps a non-overshooting morph even when the system removes animations.
    return coroutineScope.launch(if (reduced || full) FullMotionDurationScale else EmptyCoroutineContext) {
      progress.animateTo(target, glassTransitionSpec(transitionSpec, reduced)) { memberChanged() }
      onEnd()
      memberChanged()
    }
  }

  fun memberChanged() {
    // onPlaced reclusters once this container has coordinates; a member can register first.
    if (!isAttached || refreshing || surfaceCoordinates == null) return
    refreshing = true
    try {
      if (pending.isNotEmpty()) startTransitions(liveSnapshots())
      val registered = members
      val opticalSize = glassOpticalSize(registered.mapNotNull { it.binding.surface?.boundsInOwner() })
      registered.forEach { member ->
        val runtime = member.binding.runtime ?: return@forEach
        val alpha = attaching[member]?.takeIf { it.neighbour == null }?.progress?.value?.coerceIn(0f, 1f) ?: 1f
        if (runtime.opticalSizeOverride == opticalSize && runtime.transitionAlpha == alpha) return@forEach
        runtime.opticalSizeOverride = opticalSize
        runtime.transitionAlpha = alpha
        // Snapshots below must not use material resolved at the previous optical size or alpha.
        runtime.refreshMemberMaterial()
      }
      val live = liveSnapshots()
      // A grow ends, snapping to the member's own shape, once its neighbour is no longer live or
      // could no longer have been chosen: the start conditions, including the current spacing,
      // hold on every pass.
      val broken = attaching.filter { (member, attach) ->
        val neighbour = attach.neighbour ?: return@filter false
        val snapshot = live[member] ?: return@filter false
        // The gap is measured to what the neighbour draws, as when it was chosen.
        live[neighbour]?.let { snapshot.canTransitionWith(it) && snapshot.isWithinSpacingOf(neighbour, live) } != true
      }
      broken.forEach { (member, attach) ->
        attach.job?.cancel()
        attaching.remove(member)
      }
      // A ghost draws through its anchor's surface, so it ends once that anchor cannot draw it: the
      // anchor collapsed, left the layout, or changed input or resolved sources. A valid anchor
      // also keeps the model's optical size non-null while ghosts remain. A retracting ghost also
      // needs a translation-only neighbour within the current spacing; it takes the neighbour's
      // key, so keys always match.
      endGhosts { ghost ->
        val anchor = live[ghost.anchor]
        anchor == null || !anchor.layoutRect.isValidGlassMember() || !anchor.drawsSourcesOf(ghost.last) ||
          anchor.key.input != ghost.last.key.input || anchor.key.expandLayerBounds != ghost.last.key.expandLayerBounds ||
          (ghost.neighbour != null && (!anchor.translationOnly || !ghost.last.isWithinSpacingOf(ghost.anchor, live)))
      }
      // A transition's link only draws when it shares its neighbour's cluster, which can stop
      // holding when registrations between them change (for example a fading ghost splits a run).
      // Such a transition ends, like any other broken start condition, and the pass reclusters.
      while (true) {
        // Morphs follow what their neighbour draws now, which may itself still be growing.
        val drawn = mutableMapOf<Any, GlassMemberSnapshot>()
        val snapshots = registrations.mapNotNull { registration ->
          when (registration) {
            is GlassMemberNode -> live[registration]?.let { snapshot ->
              val attach = attaching[registration] ?: return@let snapshot
              // A fading member draws alone until it is opaque, even beside an equally faded member.
              if (attach.neighbour == null) return@let snapshot.copy(fading = true)
              val neighbour = drawn[attach.neighbour] ?: drawnSnapshot(attach.neighbour, live) ?: return@let snapshot
              val (rect, material) = snapshot.morphedFrom(neighbour, attach.progress.value)
              registration.snapshot(rect, snapshot.key, material, link = neighbour.identity).copy(sourceSelection = snapshot.sourceSelection)
            }
            // A ghost's neighbour is its anchor, which is live after the pruning above.
            is GlassMemberGhost -> registration.snapshot(registration.neighbour?.let { drawn[it] ?: checkNotNull(drawnSnapshot(it, live)) })
            else -> null
          }?.also { drawn[registration] = it }
        }
        snapshots.forEach { latest[it.identity] = it }
        // Optical size changes immediately and counts live layout rects only, never ghosts.
        model = clusterGlassMembers(snapshots, spacingPx, glassOpticalSize(live.values.map { it.layoutRect }))
        val split = model.surfaces.flatMap { cluster ->
          cluster.members.filter { member -> member.link != null && cluster.members.none { it.identity === member.link } }
        }.map { it.identity }
        if (split.isEmpty()) break
        split.filterIsInstance<GlassMemberNode>().forEach { attaching.remove(it)?.job?.cancel() }
        endGhosts { it in split }
      }
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

  private fun liveSnapshots(): Map<GlassMemberNode, GlassMemberSnapshot> = members.mapNotNull { member ->
    val binding = member.binding
    val layoutRect = binding.surface?.boundsInOwner() ?: return@mapNotNull null
    val key = binding.key ?: return@mapNotNull null
    val material = binding.material ?: return@mapNotNull null
    member to member.snapshot(layoutRect, key, material).copy(sourceSelection = checkNotNull(binding.surface).inputSelection())
  }.toMap()

  /** [layoutRect] is the member's layout rect, or its transition rect while it animates. */
  private fun GlassMemberNode.snapshot(layoutRect: Rect, key: GlassMergeKey, material: ResolvedGlassStyle, link: Any? = null): GlassMemberSnapshot {
    val owner = checkNotNull(surfaceCoordinates)
    // Interaction transforms are draw-time scales, applied after the transition rect.
    val transform = binding.runtime?.currentInteractionTransform(layoutRect.size)
    val rect = transform?.let { layoutRect.transformedBy(it) } ?: layoutRect
    // A merged member is one rounded rect with one circular light, so unequal axes draw standalone.
    val uniformScale = transform == null || abs(transform.scaleX - transform.scaleY) < 1e-4f
    return GlassMemberSnapshot(this, rect, key, material, uniformScale && hasTranslationOnlyTransform(owner), layoutRect, link)
  }

  private fun GlassMemberSnapshot.isWithinSpacingOf(neighbour: GlassMemberNode, live: Map<GlassMemberNode, GlassMemberSnapshot>): Boolean =
    drawnSnapshot(neighbour, live)?.let { glassMemberGap(layoutRect, it.layoutRect) < spacingPx } == true

  /** What [member] draws now: a member growing out of its neighbour is at its last morphed shape. */
  private fun drawnSnapshot(member: GlassMemberNode, live: Map<GlassMemberNode, GlassMemberSnapshot>): GlassMemberSnapshot? =
    live[member]?.let { snapshot -> latest[member]?.takeIf { attaching[member]?.neighbour != null } ?: snapshot }

  private fun startTransitions(live: Map<GlassMemberNode, GlassMemberSnapshot>) {
    val ordered = registrations.mapNotNull { if (it is GlassMemberNode) drawnSnapshot(it, live) else latest[it] }
    // Members appearing in this pass are not visible yet, so none can grow out of another.
    val appearing = pending.toSet()
    for (member in pending.toList()) {
      val snapshot = live[member] ?: continue
      pending -= member
      if (!settled || member.transition == GlassMemberTransition.None) continue
      val neighbour = if (member.transition == GlassMemberTransition.MatchedGeometry) resolveGlassNeighbour(snapshot, ordered, spacingPx, excluded = appearing) else null
      val attach = GlassMemberAttach(neighbour?.identity as GlassMemberNode?)
      attaching[member] = attach
      attach.job = animate(attach.progress, 1f) { attaching.remove(member) }
    }
  }

  override fun onSurfaceChanged(surface: HazeEffectSurface) = memberChanged()
  override fun onSurfaceReleased(surface: HazeEffectSurface) = memberChanged()
  override fun canComposeNativeSurfaces(bounds: Rect): Boolean = renderer.canComposeNativeSurfaces(bounds)

  override fun ContentDrawScope.draw() {
    drawHazeEffectSurfaces(this@GlassContainerNode, { renderer.reconcile() }, checkNotNull(children))
    settled = true
  }

  override fun onDetach() {
    // Releasing member surfaces below reclusters; without coordinates that pass returns early
    // instead of restoring the overrides this resets.
    surfaceCoordinates = null
    renderer.release()
    // Detaching cancels coroutineScope, so transitions end here without completing.
    members.forEach {
      it.binding.surface?.relocateTo(null)
      it.binding.runtime?.opticalSizeOverride = null
      it.binding.runtime?.transitionAlpha = 1f
    }
    registrations.clear()
    pending.clear()
    attaching.clear()
    latest.clear()
    settled = false
    children?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
    children = null
  }
}

/** Scales this rect about the transform's pivot, which is relative to the rect's top-left. */
private fun Rect.transformedBy(transform: HazeEffectContentTransform): Rect {
  val pivot = topLeft + transform.pivot
  return Rect(
    left = pivot.x + (left - pivot.x) * transform.scaleX,
    top = pivot.y + (top - pivot.y) * transform.scaleY,
    right = pivot.x + (right - pivot.x) * transform.scaleX,
    bottom = pivot.y + (bottom - pivot.y) * transform.scaleY,
  )
}
