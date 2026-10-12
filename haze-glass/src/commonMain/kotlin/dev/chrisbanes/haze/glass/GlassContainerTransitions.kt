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
  val left = lerp(from.rect.left, to.rect.left)
  val top = lerp(from.rect.top, to.rect.top)
  val colour = fraction.coerceIn(0f, 1f)
  return GlassMergedMember(
    rect = Rect(left, top, maxOf(lerp(from.rect.right, to.rect.right), left + 1f), maxOf(lerp(from.rect.bottom, to.rect.bottom), top + 1f)),
    cornerRadii = CornerRadii(
      lerp(from.cornerRadii.topLeft, to.cornerRadii.topLeft).coerceAtLeast(0f),
      lerp(from.cornerRadii.topRight, to.cornerRadii.topRight).coerceAtLeast(0f),
      lerp(from.cornerRadii.bottomRight, to.cornerRadii.bottomRight).coerceAtLeast(0f),
      lerp(from.cornerRadii.bottomLeft, to.cornerRadii.bottomLeft).coerceAtLeast(0f),
    ),
    tint = lerp(from.tint, to.tint, colour),
    backgroundColor = lerp(from.backgroundColor, to.backgroundColor, colour),
  )
}

/** This member's layout shape and colours, without interaction. */
internal fun GlassMemberSnapshot.shape(): GlassMergedMember =
  GlassMergedMember(layoutRect, material.cornerRadii, material.tint, material.backgroundColor)

/**
 * What this member draws: its interaction-transformed rect, with its corners scaled as the
 * renderer scales them.
 */
internal fun GlassMemberSnapshot.drawnShape(): GlassMergedMember {
  val scale = minOf(rect.width / layoutRect.width, rect.height / layoutRect.height)
  return GlassMergedMember(rect, material.cornerRadii * scale, material.tint, material.backgroundColor)
}

/**
 * [shape] interpolated from [from]'s [drawn outline][drawnShape] towards this member, so a morph
 * starts or ends where a pressed neighbour draws rather than at its layout rect.
 */
internal fun GlassMemberSnapshot.morphedFrom(from: GlassMemberSnapshot, fraction: Float): Pair<Rect, ResolvedGlassStyle> {
  val shape = lerpGlassMergedMember(from.drawnShape(), shape(), fraction)
  return shape.rect to material.copy(cornerRadii = shape.cornerRadii, tint = shape.tint, backgroundColor = shape.backgroundColor)
}

/**
 * The nearest live member that [member] can grow out of or retract into, or null.
 *
 * [ordered] is every registration's snapshot, with [member] at its registration position;
 * [excluded] members are not visible yet. A neighbour must satisfy [canTransitionWith], be within
 * the full spacing, and share a cluster with [member] placed on it, so the consecutive-run and cap
 * rules still hold.
 */
internal fun resolveGlassNeighbour(
  member: GlassMemberSnapshot,
  ordered: List<GlassMemberSnapshot>,
  spacingPx: Float,
  excluded: Set<Any> = emptySet(),
): GlassMemberSnapshot? {
  return ordered
    .filter { candidate ->
      candidate.identity is GlassMemberNode && candidate.identity !== member.identity && candidate.identity !in excluded &&
        member.canTransitionWith(candidate) && glassMemberGap(member.layoutRect, candidate.layoutRect) < spacingPx
    }
    .filter { candidate ->
      val probe = ordered.map {
        if (it.identity === member.identity) it.copy(rect = candidate.rect, layoutRect = candidate.layoutRect, key = candidate.key, link = candidate.identity) else it
      }
      clusterGlassMembers(probe, spacingPx).surfaces.any { cluster ->
        cluster.members.any { it.identity === member.identity } && cluster.members.any { it.identity === candidate.identity }
      }
    }
    .minByOrNull { glassMemberGap(member.layoutRect, it.layoutRect) }
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
    if (neighbourSnapshot != null) {
      // The neighbour's live key and material keep the link as the optical size changes.
      val (rect, material) = last.morphedFrom(neighbourSnapshot, progress.value)
      val merged = neighbourSnapshot.material.copy(cornerRadii = material.cornerRadii, tint = material.tint, backgroundColor = material.backgroundColor)
      return GlassMemberSnapshot(this, rect, neighbourSnapshot.key, merged, true, rect, link = neighbour)
    }
    val alpha = last.material.alpha * progress.value.coerceIn(0f, 1f)
    return GlassMemberSnapshot(this, last.layoutRect, last.key, last.material.copy(alpha = alpha), true, last.layoutRect, fading = true)
  }
}
