// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.Poko
import kotlin.math.hypot

/** Value key deliberately excludes member geometry, colours and interaction declarations. */
internal data class GlassMergeKey(
  val input: HazeInput,
  /** Members that disabled layer expansion only merge with each other, keeping their bounds. */
  val expandLayerBounds: Boolean,
  val qualityFraction: Float,
  val optics: GlassOptics,
  val resolvedOptics: ResolvedGlassOptics,
  val surfaceProfile: Float,
  val specularIntensity: Float,
  val specularExponent: Float,
  val edgeShadow: Color,
  val edgeSoftnessPx: Float,
  val ambientResponse: Float,
  val chromaticAberrationStrength: Float,
  val chromaticAberrationMode: Float,
  val contrast: Float,
  val whitePoint: Float,
  val chromaMultiplier: Float,
  val contentNormalBlend: Float,
  val fresnelExponent: Float,
  val lightAlignment: Alignment,
  /** Start/End light alignments resolve to opposite sides under different directions. */
  val layoutDirection: LayoutDirection,
  val alpha: Float,
)

internal fun glassMergeKey(
  input: HazeInput,
  expandLayerBounds: Boolean,
  qualityFraction: Float,
  effect: GlassRuntimeEffect,
  material: ResolvedGlassStyle,
  layoutDirection: LayoutDirection,
): GlassMergeKey = GlassMergeKey(
  input, expandLayerBounds, qualityFraction, effect.optics, material.resolvedOptics,
  material.surfaceProfile, material.specularIntensity, material.specularExponent,
  material.edgeShadow, material.edgeSoftnessPx, material.ambientResponse,
  material.chromaticAberrationStrength, material.chromaticAberrationMode,
  material.contrast, material.whitePoint, material.chromaMultiplier,
  material.contentNormalBlend, material.fresnelExponent, effect.lightPosition, layoutDirection, material.alpha,
)

/**
 * Registration identity survives geometry updates; ordering is the input list's ordering.
 * Merged fields only model translated, axis-aligned rects, so other transforms never merge; neither
 * does a member whose interaction scale is currently non-uniform.
 * [rect] includes the member's interaction transform and drives clustering; [layoutRect] does not,
 * so surface bounds and optics stay put while a member is pressed.
 * A member with a [link] is transitioning with that member and stays connected to it at any gap.
 * A [fading] member's alpha differs from any field it could join, so it never merges.
 * [sourceSelection] is the source areas the member's input resolved to.
 * [sampleLayoutRect] is the unpressed rect content samples through; a ghost keeps its live
 * member's, as its [layoutRect] is its drawn rect.
 * [groupKey] is the member's declared Glass group, and [shape] its rounded-corner shape; a group
 * draws with its first member's.
 */
internal data class GlassMemberSnapshot(
  val identity: Any,
  val rect: Rect,
  val key: GlassMergeKey,
  val material: ResolvedGlassStyle,
  val translationOnly: Boolean,
  val layoutRect: Rect,
  val link: Any? = null,
  val fading: Boolean = false,
  val sourceSelection: Set<Any> = emptySet(),
  val sampleLayoutRect: Rect = layoutRect,
  val groupKey: Any? = null,
  val shape: RoundedCornerShape? = null,
)

/** Whether two members declare the same Glass group. */
internal fun GlassMemberSnapshot.isGroupedWith(other: GlassMemberSnapshot): Boolean =
  groupKey != null && groupKey == other.groupKey

/**
 * [groups] partitions the cluster into drawn units: a Glass group is one unit, every other member
 * a singleton unit. [members] is the flat, registration-ordered list of them.
 */
@Poko
internal class GlassCluster(val groups: List<List<GlassMemberSnapshot>>) {
  val members: List<GlassMemberSnapshot> = groups.flatten()
}

@Poko
internal class GlassClusterOverflow(val members: List<Any>, val excessCount: Int)

@Poko
internal class GlassClusterModel(
  val surfaces: List<GlassCluster>,
  val opticalSize: Size?,
  val overflow: List<GlassClusterOverflow>,
)

internal fun Rect.isValidGlassMember(): Boolean =
  left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
    width > 0f && height > 0f

/** [sizes] are members' untransformed layout sizes, so rotation or scale never changes the optical size. */
internal fun glassOpticalSize(sizes: List<Size>): Size? = sizes
  .filter { it.width.isFinite() && it.height.isFinite() && it.minDimension > 0f }
  .maxOfOrNull { it.minDimension }
  ?.let { Size(it, it) }

/** Bounding box of a non-empty collection of rects. */
internal fun Iterable<Rect>.union(): Rect = reduce { a, b ->
  Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
}

/** Euclidean distance between closest rectangle points, including diagonal gaps. */
internal fun glassMemberGap(a: Rect, b: Rect): Float = hypot(
  maxOf(a.left - b.right, b.left - a.right, 0f),
  maxOf(a.top - b.bottom, b.top - a.bottom, 0f),
)

/**
 * Partitions [members], in order, into units. A unit grows while the next member has the unit's
 * non-null group key and merge key and is neither fading nor rotated or scaled; any other member
 * ends the unit, so a non-consecutive group splits into runs (ADR-0013: merging never reorders).
 */
internal fun formGlassGroupUnits(members: List<GlassMemberSnapshot>): List<List<GlassMemberSnapshot>> {
  val units = mutableListOf<MutableList<GlassMemberSnapshot>>()
  for (member in members) {
    val unit = units.lastOrNull()
    val first = unit?.first()
    if (
      first != null && member.isGroupedWith(first) && first.canCluster() && member.canCluster() && member.key == first.key
    ) {
      unit += member
    } else {
      units += mutableListOf(member)
    }
  }
  return units
}

private fun GlassMemberSnapshot.canCluster() = translationOnly && !fading

/** [opticalSize] defaults to every member's layout rect; a container passes its live members' only. */
internal fun clusterGlassMembers(
  members: List<GlassMemberSnapshot>,
  mergeDistancePx: Float,
  opticalSize: Size? = glassOpticalSize(members.filter { it.rect.isValidGlassMember() }.map { it.layoutRect.size }),
): GlassClusterModel {
  requireFiniteNonNegative("mergeDistancePx", mergeDistancePx)
  val valid = members.filter { it.rect.isValidGlassMember() }
  // Clustering works on units: gaps use a unit's bounds, a member's link counts for its whole unit,
  // and the cap and the consecutive-run split count units.
  val units = formGlassGroupUnits(valid)
  val unitRects = units.map { unit -> unit.map { it.rect }.union() }
  val remaining = units.indices.toMutableList()
  val surfaces = mutableListOf<GlassCluster>()
  val overflow = mutableListOf<GlassClusterOverflow>()
  while (remaining.isNotEmpty()) {
    val component = mutableListOf(remaining.removeAt(0))
    var cursor = 0
    while (cursor < component.size) {
      val unitIndex = component[cursor++]
      val unit = units[unitIndex]
      val member = unit.first()
      val iterator = remaining.iterator()
      while (iterator.hasNext()) {
        val index = iterator.next()
        val candidateUnit = units[index]
        val candidate = candidateUnit.first()
        // The gap or link test is cheap and rejects most pairs; the key comparison is a deep equals.
        if (
          member.canCluster() && candidate.canCluster() &&
          (
            // Start the field before its bridge closes, so facing edges stretch continuously.
            glassMemberGap(unitRects[unitIndex], unitRects[index]) < mergeDistancePx ||
              unit.any { a -> candidateUnit.any { b -> a.link === b.identity || b.link === a.identity } }
            ) &&
          member.key == candidate.key
        ) {
          component += index
          iterator.remove()
        }
      }
    }
    val sorted = component.sorted()
    // A merged surface draws at one z-position, so only consecutive registrations may share one;
    // an intervening surface splits the capped units (ADR-0013: merging never reorders members).
    // ponytail: a run linked only through units outside it still shares one field (no false bridge,
    // but no dome); split runs by direct edges if that ever shows.
    var run = mutableListOf<Int>()
    for (index in sorted.take(MAX_GLASS_MERGED_MEMBERS)) {
      if (run.isNotEmpty() && run.last() != index - 1) {
        surfaces += run.toCluster(units)
        run = mutableListOf()
      }
      run += index
    }
    surfaces += run.toCluster(units)
    sorted.drop(MAX_GLASS_MERGED_MEMBERS).forEach { surfaces += listOf(it).toCluster(units) }
    if (sorted.size > MAX_GLASS_MERGED_MEMBERS) {
      overflow += GlassClusterOverflow(
        sorted.flatMap { units[it] }.map { it.identity },
        sorted.size - MAX_GLASS_MERGED_MEMBERS,
      )
    }
  }
  // An intervening singleton must retain registration order relative to disjoint components.
  return GlassClusterModel(
    surfaces.sortedBy { surface -> valid.indexOf(surface.members.first()) },
    opticalSize,
    overflow,
  )
}

private fun List<Int>.toCluster(units: List<List<GlassMemberSnapshot>>) =
  GlassCluster(map { units[it] })
