// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.lerp
import dev.chrisbanes.haze.HazeInput
import kotlinx.coroutines.Job

/** Reduced motion keeps the morph but removes its elasticity (ADR-0013, Transitions). */
internal fun glassTransitionSpec(spec: FiniteAnimationSpec<Float>, reduced: Boolean): FiniteAnimationSpec<Float> {
  if (!reduced) return spec
  return spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = (spec as? SpringSpec<Float>)?.stiffness ?: Spring.StiffnessMedium)
}

/**
 * Interpolates shape and colours. Geometry follows [fraction] through spring overshoot, keeping at
 * least 1px so the field stays valid; colours clamp to their endpoints.
 */
internal fun lerpGlassMergedMember(from: GlassMergedMember, to: GlassMergedMember, fraction: Float): GlassMergedMember {
  fun lerp(start: Float, stop: Float) = start + (stop - start) * fraction
  fun lerp(start: Rect, stop: Rect): Rect {
    val left = lerp(start.left, stop.left)
    val top = lerp(start.top, stop.top)
    return Rect(left, top, maxOf(lerp(start.right, stop.right), left + 1f), maxOf(lerp(start.bottom, stop.bottom), top + 1f))
  }
  val colour = fraction.coerceIn(0f, 1f)
  return GlassMergedMember(
    rect = lerp(from.rect, to.rect),
    cornerRadii = CornerRadii(
      lerp(from.cornerRadii.topLeft, to.cornerRadii.topLeft).coerceAtLeast(0f),
      lerp(from.cornerRadii.topRight, to.cornerRadii.topRight).coerceAtLeast(0f),
      lerp(from.cornerRadii.bottomRight, to.cornerRadii.bottomRight).coerceAtLeast(0f),
      lerp(from.cornerRadii.bottomLeft, to.cornerRadii.bottomLeft).coerceAtLeast(0f),
    ),
    tint = lerp(from.tint, to.tint, colour),
    backgroundColor = lerp(from.backgroundColor, to.backgroundColor, colour),
    fallbackBaseColor = to.fallbackBaseColor,
    layoutRect = lerp(from.layoutRect, to.layoutRect),
  )
}

/** This member's layout shape and colours, without interaction. */
internal fun GlassMemberSnapshot.shape(): GlassMergedMember =
  GlassMergedMember(layoutRect, material.cornerRadii, material.tint, material.backgroundColor, fallbackBaseColor = material.fallbackBaseColor)

/**
 * What this member draws: its interaction-transformed rect, with its corners scaled as the
 * renderer scales them, sampling through its [sampleLayoutRect][GlassMemberSnapshot.sampleLayoutRect].
 */
internal fun GlassMemberSnapshot.drawnShape(): GlassMergedMember {
  val scale = minOf(rect.width / layoutRect.width, rect.height / layoutRect.height)
  return GlassMergedMember(rect, material.cornerRadii * scale, material.tint, material.backgroundColor, fallbackBaseColor = material.fallbackBaseColor, layoutRect = sampleLayoutRect)
}

/**
 * [shape] interpolated from [from]'s [drawn outline][drawnShape] towards this member, so a morph
 * starts or ends where a pressed neighbour draws rather than at its layout rect.
 */
internal fun GlassMemberSnapshot.morphedFrom(from: GlassMemberSnapshot, fraction: Float): Pair<Rect, ResolvedGlassStyle> {
  val shape = lerpGlassMergedMember(from.drawnShape(), shape(), fraction)
  return shape.rect to material.copy(cornerRadii = shape.cornerRadii, tint = shape.tint, backgroundColor = shape.backgroundColor, fallbackBaseColor = shape.fallbackBaseColor)
}

/**
 * The nearest live member that [member] can grow out of or retract into, or null.
 *
 * [ordered] is every registration's snapshot, with [member] at its registration position;
 * [excluded] members are not visible yet. A neighbour must satisfy [canTransitionWith], be within
 * the full merge distance of its group's bounds (unless it shares [member]'s non-null group key,
 * which prefers it at any distance), and share a cluster with [member] placed on it, so the consecutive-run and cap rules
 * still hold. Every live member of that cluster must also resolve [member]'s sources.
 */
internal fun resolveGlassNeighbour(
  member: GlassMemberSnapshot,
  ordered: List<GlassMemberSnapshot>,
  mergeDistancePx: Float,
  excluded: Set<Any> = emptySet(),
): GlassMemberSnapshot? {
  // A group counts as one member, so a candidate is as near as its unit's bounds.
  val unitBounds = glassUnitLayoutBounds(ordered)
  fun GlassMemberSnapshot.gap() = glassMemberGap(member.layoutRect, unitBounds[identity] ?: layoutRect)
  return ordered
    .filter { candidate ->
      candidate.identity is GlassMemberNode && candidate.identity !== member.identity && candidate.identity !in excluded &&
        member.canTransitionWith(candidate) &&
        // Members of one group draw as one shape at any distance.
        (candidate.isGroupedWith(member) || candidate.gap() < mergeDistancePx)
    }
    // Same group first, then nearest; the first candidate that can cluster with the member wins.
    .sortedWith(compareBy({ !it.isGroupedWith(member) }, { it.gap() }))
    .firstOrNull { candidate ->
      val probe = ordered.map {
        if (it.identity === member.identity) it.copy(rect = candidate.rect, layoutRect = candidate.layoutRect, key = candidate.key, link = candidate.identity) else it
      }
      clusterGlassMembers(probe, mergeDistancePx).surfaces.any { cluster ->
        cluster.members.any { it.identity === member.identity } && cluster.members.any { it.identity === candidate.identity } &&
          // A cluster mixing resolved sources draws its members separately, so the morph would not show.
          // Ghosts draw through their anchor, so only live members count.
          cluster.members.filter { it.identity is GlassMemberNode }.all { it.drawsSourcesOf(member) }
      }
    }
}

/** The nearest live member whose input can draw a [Materialize][GlassMemberTransition.Materialize] ghost. */
internal fun resolveGlassBorrowedAnchor(member: GlassMemberSnapshot, ordered: List<GlassMemberSnapshot>): GlassMemberSnapshot? = ordered
  .filter {
    it.identity is GlassMemberNode && it.identity !== member.identity &&
      it.key.input == member.key.input && it.key.expandLayerBounds == member.key.expandLayerBounds
  }
  .minByOrNull { glassMemberGap(member.layoutRect, it.layoutRect) }

/**
 * Whether this member can grow out of or retract into [neighbour] as one merged surface. The
 * container checks it when a transition starts and on every pass while it runs.
 */
internal fun GlassMemberSnapshot.canTransitionWith(neighbour: GlassMemberSnapshot): Boolean =
  // Morphs interpolate both layout rects, so a collapsed member cannot take part.
  layoutRect.isValidGlassMember() && neighbour.layoutRect.isValidGlassMember() &&
    translationOnly && neighbour.translationOnly &&
    // A leaving member's key was resolved at the optical size it set, so ignore that value.
    neighbour.key == key.copy(resolvedOptics = neighbour.key.resolvedOptics) &&
    // A surface resolving other sources would draw both members separately.
    neighbour.drawsSourcesOf(this)

/**
 * Whether this member's input draws [other]'s sources, so one surface can draw both. Content
 * inputs are distinct areas by design, as for merged members, so only source-backed selections
 * must match.
 */
internal fun GlassMemberSnapshot.drawsSourcesOf(other: GlassMemberSnapshot): Boolean =
  other.key.input is HazeInput.Content || sourceSelection == other.sourceSelection

/** A member's glass appearing: grows out of [neighbour], or fades in when it is null. */
internal class GlassMemberAttach(val neighbour: GlassMemberNode?) {
  val progress = Animatable(0f)
  var job: Job? = null
}

/**
 * A detached member's glass, drawn by the container until its animation ends. It retracts into
 * [neighbour], or fades in place when that is null. [anchor] is the live member whose surface
 * draws it; the ghost ends when [anchor] leaves.
 */
internal class GlassMemberGhost(val last: GlassMemberSnapshot, val neighbour: GlassMemberNode?, val anchor: GlassMemberNode) {
  val progress = Animatable(1f)
  var job: Job? = null

  fun snapshot(neighbourSnapshot: GlassMemberSnapshot?): GlassMemberSnapshot {
    val drawn = last.drawnShape()
    if (neighbourSnapshot != null) {
      // The neighbour's live key and material keep the link as the optical size changes.
      val shape = lerpGlassMergedMember(neighbourSnapshot.drawnShape(), drawn, progress.value)
      val merged = neighbourSnapshot.material.copy(cornerRadii = shape.cornerRadii, tint = shape.tint, backgroundColor = shape.backgroundColor, fallbackBaseColor = shape.fallbackBaseColor)
      return GlassMemberSnapshot(this, shape.rect, neighbourSnapshot.key, merged, true, shape.rect, link = neighbour, sampleLayoutRect = shape.layoutRect, groupKey = last.groupKey, shape = last.shape)
    }
    val alpha = last.material.alpha * progress.value.coerceIn(0f, 1f)
    return GlassMemberSnapshot(this, drawn.rect, last.key, last.material.copy(cornerRadii = drawn.cornerRadii, alpha = alpha), true, drawn.rect, fading = true, sampleLayoutRect = drawn.layoutRect, groupKey = last.groupKey, shape = last.shape)
  }
}
