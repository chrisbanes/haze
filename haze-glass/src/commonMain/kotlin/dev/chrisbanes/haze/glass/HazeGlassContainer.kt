// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.TraversableNode.Companion.TraverseDescendantsAction
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.traverseDescendants
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import dev.chrisbanes.haze.HazeEffectContentTransform
import dev.chrisbanes.haze.HazeEffectSurface
import dev.chrisbanes.haze.HazeEffectSurfaceOwner
import dev.chrisbanes.haze.HazeInput
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
 * member's [GlassMemberTransition] declares, timed by [transitionSpec], which defaults to
 * `spring()`. [GlassReducedMotionPolicy.Reduced] keeps the morph with a critically damped spring at
 * full duration, whatever the platform scale is. [GlassReducedMotionPolicy.System] snaps,
 * as with [GlassMemberTransition.None], when the platform motion-duration scale is zero.
 *
 * A detached member's glass is drawn by the container through a live member with the same input,
 * resolving the same sources, until its animation ends. When no such member remains, for example
 * when the last member leaves, the glass disappears immediately. So does the glass of a member
 * that is rotated or scaled, or that uses [HazeInput.Content], whose content leaves with it.
 *
 * A running transition ends without animating when the member it grows from, retracts into or is
 * drawn through leaves, or when its member is no longer within [spacing] of that member. A member
 * whose nearest container changes while it stays in the layout, for example when a nested
 * container is added or removed around it, appears in the new container immediately. Moving a member
 * with `movableContentOf` detaches and reattaches it, so it detaches from the old container and
 * attaches to the new one, each with the transition described above. [transitionSpec] and
 * [reducedMotionPolicy] changes apply from the next transition. Leaving members never count
 * towards the shared optical size. Haze animates only the glass, so callers animate member content
 * themselves, for example with `AnimatedVisibility`. Cap diagnostics follow [HazeLogger.enabled].
 *
 * For the items of a scrollable layout, such as a `LazyRow` or `LazyColumn`, pass its
 * [scrollState]. While [scrollState] [is scrolling][ScrollableState.isScrollInProgress], including
 * the frame in which it stops, members that attach appear immediately and members that detach
 * disappear immediately, whatever their [GlassMemberTransition], so items do not animate as they
 * scroll into or out of view. Members that attach or detach while the layout is idle animate as
 * usual. A scroll that starts while a member's glass is still animating out ends that animation
 * immediately. Position changes made outside a scroll, such as `LazyListState.requestScrollToItem`,
 * are not scrolls. Without a [scrollState], items of a lazy layout that scroll into view animate like
 * any member that attaches after the first draw.
 */
@Stable
public fun Modifier.hazeGlassContainer(
  spacing: Dp,
  scrollState: ScrollableState? = null,
  transitionSpec: FiniteAnimationSpec<Float> = spring(),
  reducedMotionPolicy: GlassReducedMotionPolicy = GlassReducedMotionPolicy.System,
): Modifier {
  require(spacing.value.isFinite() && spacing.value >= 0f) { "spacing must be finite and non-negative" }
  return then(GlassContainerElement(spacing, scrollState, transitionSpec, reducedMotionPolicy))
}

internal object GlassContainerTraversalKey

@Poko
internal class GlassContainerElement(
  val spacing: Dp,
  val scrollState: ScrollableState?,
  val transitionSpec: FiniteAnimationSpec<Float>,
  val reducedMotionPolicy: GlassReducedMotionPolicy,
) : ModifierNodeElement<GlassContainerNode>() {
  override fun create() = GlassContainerNode(spacing, scrollState, transitionSpec, reducedMotionPolicy)
  override fun update(node: GlassContainerNode) {
    node.spacing = spacing
    node.scrollState = scrollState
    node.transitionSpec = transitionSpec
    node.reducedMotionPolicy = reducedMotionPolicy
    node.memberChanged()
  }
  override fun InspectorInfo.inspectableProperties() {
    name = "hazeGlassContainer"
    properties["spacing"] = spacing
    properties["scrollState"] = scrollState
    properties["transitionSpec"] = transitionSpec
    properties["reducedMotionPolicy"] = reducedMotionPolicy
  }
}

internal class GlassContainerNode(
  var spacing: Dp,
  var scrollState: ScrollableState?,
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

  /**
   * Members that attach or detach while this is true appear or disappear without a transition:
   * before the first draw, and from a scroll until the next draw. A lazy layout can detach
   * scrolled-out items after its scroll ends, so a scroll seen since the previous draw still counts.
   */
  private var suppressTransitions = true

  /**
   * Whether the system has turned animations off under [GlassReducedMotionPolicy.System], in which
   * case members snap as with [GlassMemberTransition.None]. Read it only while attached.
   */
  private val transitionsOff: Boolean
    get() = reducedMotionPolicy == GlassReducedMotionPolicy.System &&
      coroutineScope.coroutineContext[MotionDurationScale]?.scaleFactor == 0f

  private fun observeScroll() {
    if (suppressTransitions) return
    val state = scrollState ?: return
    if (Snapshot.withoutReadObservation { state.isScrollInProgress }) {
      suppressTransitions = true
      // A ghost's rect is fixed in owner space, so it would stay behind scrolled content.
      endGhosts { true }
    }
  }
  private var children: GraphicsLayer? = null
  private var membersClaimed = false
  private var refreshing = false
  private var spacingCheckPending = false

  /** Set by [memberChanged]; the next draw (or a leaving member's ghost) reclusters once for all of them. */
  private var reclusterPending = false

  /** Reclustering passes run so far; tests count them to prove a frame reclusters once. */
  internal var reclusterPasses = 0
    private set
  private var overflow = emptyList<GlassClusterOverflow>()
  internal val renderer = GlassContainerRenderer(this)
  internal val spacingPx: Float get() = with(requireDensity()) { spacing.toPx() }
  internal var model = GlassClusterModel(emptyList(), null, emptyList())
    private set

  override fun onAttach() {
    children = requireGraphicsContext().createGraphicsLayer()
    // Reactivation with an unchanged layout is not placed again (#1473), so members reattaching
    // after this node would find no coordinates and never recluster; a first attach is unmeasured.
    val coordinates = requireLayoutCoordinates()
    if (coordinates.isAttached && coordinates.size != IntSize.Zero) surfaceCoordinates = coordinates
  }
  override fun onPlaced(coordinates: LayoutCoordinates) {
    surfaceCoordinates = coordinates
    if (!membersClaimed) {
      membersClaimed = true
      // Claim members that were placed before this container: an unchanged member is not re-placed
      // or re-published, so nothing else reconnects it.
      traverseDescendants(GlassMemberTraversalKey) {
        (it as GlassMemberNode).connect()
        TraverseDescendantsAction.ContinueTraversal
      }
    }
    memberChanged()
  }

  /** [appearing] is false when the member moves from another container, where it was visible. */
  fun add(member: GlassMemberNode, appearing: Boolean = true) {
    if (member !in registrations) {
      registrations += member
      // A member that appears before the first draw or during a scroll does not animate, even when
      // its first snapshot arrives later.
      observeScroll()
      if (appearing && !suppressTransitions) pending += member
    }
    memberChanged()
  }

  /** [leaving] is false when the member moves to another container, where it stays visible. */
  fun remove(member: GlassMemberNode, leaving: Boolean = true) {
    observeScroll()
    // ghostOf reads latest, so the pass that records this member's departure snapshot (or drops a
    // collapsed member's) runs before it is removed. While suppressed or off, no ghost is made.
    if (leaving && reclusterPending && !suppressTransitions && !transitionsOff) recluster(layoutComplete = false)
    renderer.releaseMember(member)
    pending -= member
    attaching.remove(member)?.job?.cancel()
    // Transitions with the leaving member end now rather than retargeting: a growing member snaps
    // to its own shape (or fades in, if it had not started) and a ghost drawn through the leaving
    // member disappears.
    attaching.filterValues { it.neighbour === member }.keys.forEach { endGrow(it) }
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
    // A Content member's glass samples its own content, which leaves with it; another member's
    // surface would draw unrelated content in its place.
    if (!isAttached || suppressTransitions || transitionsOff || member.transition == GlassMemberTransition.None || !last.translationOnly ||
      !last.layoutRect.isValidGlassMember() || !last.rect.isValidGlassMember() || last.key.input is HazeInput.Content
    ) {
      return null
    }
    // Only members that can draw now may carry a ghost or split its neighbour's run; a member whose
    // material is unpublished keeps a stale snapshot.
    val live = liveSnapshots().filterValues { it.layoutRect.isValidGlassMember() }.keys
    val ordered = registrations.mapNotNull { if (it === member) last else latest[it] }
      .filter { it.identity === member || it.identity !is GlassMemberNode || it.identity in live }
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
    // Reduced keeps a non-overshooting morph at a zero scale; System at zero snaps before reaching here.
    return coroutineScope.launch(if (reduced || full) FullMotionDurationScale else EmptyCoroutineContext) {
      progress.animateTo(target, glassTransitionSpec(transitionSpec, reduced)) { memberChanged() }
      onEnd()
      memberChanged()
    }
  }

  /**
   * Notes that a member, its material or a transition changed. Every placement during a scroll
   * passes through here, so this only marks the container dirty; [recluster] runs once per frame.
   */
  fun memberChanged() {
    // A member can register before this container has coordinates; onPlaced marks it dirty then.
    if (!isAttached || refreshing || surfaceCoordinates == null) return
    // A scroll that ends before the next draw must still suppress transitions.
    observeScroll()
    reclusterPending = true
    invalidateDraw()
  }

  private fun recluster(layoutComplete: Boolean) {
    if (!isAttached || refreshing || surfaceCoordinates == null) return
    reclusterPending = false
    reclusterPasses++
    if (layoutComplete) spacingCheckPending = false
    refreshing = true
    try {
      // Reached from a draw or a member's removal: the animated interaction and transition reads
      // below must not make that draw observe them and re-run every frame.
      Snapshot.withoutReadObservation {
        if (pending.isNotEmpty()) startTransitions(liveSnapshots())
        val registered = members
        val opticalSize = glassOpticalSize(registered.mapNotNull { it.localSize() })
        registered.forEach { member ->
          val runtime = member.binding.runtime ?: return@forEach
          val alpha = attaching[member]?.takeIf { it.neighbour == null }?.progress?.value?.coerceIn(0f, 1f) ?: 1f
          if (runtime.opticalSizeOverride == opticalSize && runtime.transitionAlpha == alpha) return@forEach
          runtime.opticalSizeOverride = opticalSize
          runtime.transitionAlpha = alpha
          // Snapshots below must not use material resolved at the previous optical size or alpha.
          runtime.refreshMemberMaterial()
        }
        var live = liveSnapshots()
        // A collapsed member keeps its last full-size snapshot, which would become its ghost when it
        // leaves. A leaving member's release pass still has valid bounds, so it keeps its departure snapshot.
        registered.forEach { member ->
          if (member !in live && member.binding.surface?.let { it.boundsInOwner() == null } == true) latest.remove(member)
        }
        // Liveness and compatibility hold on every pass; spacing is checked once layout is complete.
        val broken = attaching.filter { (member, attach) ->
          val neighbour = attach.neighbour ?: return@filter false
          val snapshot = live[member] ?: return@filter false
          val neighbourSnapshot = live[neighbour] ?: return@filter true
          if (!snapshot.canTransitionWith(neighbourSnapshot)) return@filter true
          // Placement moves members one at a time, so spacing can be transiently wrong mid-layout.
          if (snapshot.isWithinSpacingOf(neighbour, live)) return@filter false
          if (!layoutComplete) {
            spacingCheckPending = true
            return@filter false
          }
          true
        }
        // A converted member's material changed, so its snapshot is retaken.
        if (broken.keys.map { endGrow(it) }.any { it }) live = liveSnapshots()
        // A ghost draws through its anchor's surface, so it ends once that anchor cannot draw it: the
        // anchor collapsed, left the layout, or changed input or resolved sources. A valid anchor
        // also keeps the model's optical size non-null while ghosts remain. A retracting ghost also
        // needs a neighbour it could still have chosen: compatible and within the current spacing.
        endGhosts { ghost ->
          val anchor = live[ghost.anchor]
          anchor == null || !anchor.layoutRect.isValidGlassMember() || !anchor.drawsSourcesOf(ghost.last) ||
            anchor.key.input != ghost.last.key.input || anchor.key.expandLayerBounds != ghost.last.key.expandLayerBounds ||
            (ghost.neighbour != null && (!ghost.last.canTransitionWith(anchor) || !ghost.last.isWithinSpacingOf(ghost.anchor, live)))
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
          model = clusterGlassMembers(snapshots, spacingPx, glassOpticalSize(live.keys.mapNotNull { it.localSize() }))
          val split = model.surfaces.flatMap { cluster ->
            cluster.members.filter { member -> member.link != null && cluster.members.none { it.identity === member.link } }
          }.map { it.identity }
          if (split.isEmpty()) break
          // A grow converted to a fade has no link, so it cannot split again and the loop ends.
          if (split.filterIsInstance<GlassMemberNode>().map { endGrow(it) }.any { it }) live = liveSnapshots()
          endGhosts { it in split }
        }
        if (model.overflow != overflow) {
          overflow = model.overflow
          overflow.forEach { component ->
            HazeLogger.d("GlassContainer") { "Glass component exceeds cap $MAX_GLASS_MERGED_MEMBERS by ${component.excessCount}" }
          }
        }
      }
    } finally {
      refreshing = false
    }
  }

  /**
   * Ends [member]'s grow, snapping it to its own shape. A grow still at its start has not drawn
   * a morphed frame, so it fades in instead of popping in at full size. Returns whether the grow
   * became a fade.
   */
  private fun endGrow(member: GlassMemberNode): Boolean {
    val attach = attaching.remove(member) ?: return false
    attach.job?.cancel()
    if (attach.neighbour == null || attach.progress.value != 0f || suppressTransitions || transitionsOff) return false
    val fade = GlassMemberAttach(null)
    attaching[member] = fade
    fade.job = animate(fade.progress, 1f) { attaching.remove(member) }
    // This pass has already applied transition alphas, so the fade starts transparent here.
    member.binding.runtime?.let {
      it.transitionAlpha = 0f
      it.refreshMemberMaterial()
    }
    return true
  }

  /**
   * The member's untransformed size, or null until it is placed or while its owner-space bounds are degenerate,
   * matching its exclusion from clusters. Owner-space bounds would grow with rotation or scale, so they only gate.
   */
  private fun GlassMemberNode.localSize(): Size? =
    if (isAttached && binding.surface?.boundsInOwner()?.isValidGlassMember() == true) this.requireLayoutCoordinates().size.toSize() else null

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

  private fun GlassMemberSnapshot.isWithinSpacingOf(neighbour: GlassMemberNode, live: Map<GlassMemberNode, GlassMemberSnapshot>): Boolean {
    val drawn = drawnSnapshot(neighbour, live) ?: return false
    // A growing neighbour can overshoot away from its own layout rect, which stays as near as it was.
    return minOf(glassMemberGap(layoutRect, drawn.layoutRect), glassMemberGap(layoutRect, live.getValue(neighbour).layoutRect)) < spacingPx
  }

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
      if (suppressTransitions || transitionsOff || member.transition == GlassMemberTransition.None) continue
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
    if (reclusterPending || spacingCheckPending) recluster(layoutComplete = true)
    // A detached node (deactivated reusable content) can still be drawn once more.
    val layer = children ?: return
    drawHazeEffectSurfaces(
      this@GlassContainerNode,
      {
        // Recording the children can publish a member's first material, which would otherwise
        // reach the model a frame late.
        if (reclusterPending) recluster(layoutComplete = true)
        renderer.reconcile()
      },
      layer,
    )
    suppressTransitions = false
  }

  override fun onDetach() {
    // Releasing member surfaces below notifies memberChanged; without coordinates it returns early,
    // so no later pass restores the overrides this resets.
    surfaceCoordinates = null
    membersClaimed = false
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
    reclusterPending = false
    model = GlassClusterModel(emptyList(), null, emptyList())
    suppressTransitions = true
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
