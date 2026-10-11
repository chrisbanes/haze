// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

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
 */
internal data class GlassMemberSnapshot(
  val identity: Any,
  val rect: Rect,
  val key: GlassMergeKey,
  val material: ResolvedGlassStyle,
  val translationOnly: Boolean,
  val layoutRect: Rect,
  val link: Any? = null,
)

@Poko
internal class GlassCluster(val members: List<GlassMemberSnapshot>)

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

internal fun glassOpticalSize(rects: List<Rect>): Size? = rects
  .filter { it.isValidGlassMember() }
  .maxOfOrNull { minOf(it.width, it.height) }
  ?.let { Size(it, it) }

/** Euclidean distance between closest rectangle points, including diagonal gaps. */
internal fun glassMemberGap(a: Rect, b: Rect): Float = hypot(
  maxOf(a.left - b.right, b.left - a.right, 0f),
  maxOf(a.top - b.bottom, b.top - a.bottom, 0f),
)

/** [opticalSize] defaults to every member's layout rect; a container passes its live members' only. */
internal fun clusterGlassMembers(
  members: List<GlassMemberSnapshot>,
  spacingPx: Float,
  opticalSize: Size? = glassOpticalSize(members.filter { it.rect.isValidGlassMember() }.map { it.layoutRect }),
): GlassClusterModel {
  require(spacingPx.isFinite() && spacingPx >= 0f)
  val valid = members.filter { it.rect.isValidGlassMember() }
  val remaining = valid.indices.toMutableList()
  val surfaces = mutableListOf<GlassCluster>()
  val overflow = mutableListOf<GlassClusterOverflow>()
  while (remaining.isNotEmpty()) {
    val component = mutableListOf(remaining.removeAt(0))
    var cursor = 0
    while (cursor < component.size) {
      val member = valid[component[cursor++]]
      val iterator = remaining.iterator()
      while (iterator.hasNext()) {
        val index = iterator.next()
        val candidate = valid[index]
        if (
          member.translationOnly && candidate.translationOnly &&
          member.key == candidate.key &&
          (
            glassMemberGap(member.rect, candidate.rect) < spacingPx / 2f ||
              member.link === candidate.identity || candidate.link === member.identity
            )
        ) {
          component += index
          iterator.remove()
        }
      }
    }
    val sorted = component.sorted()
    val ordered = sorted.map(valid::get)
    // A merged surface draws at one z-position, so only consecutive registrations may share one;
    // an intervening surface splits the capped members (ADR-0013: merging never reorders members).
    // ponytail: a run linked only through members outside it still shares one field (no false bridge,
    // but no dome); split runs by direct edges if that ever shows.
    var run = mutableListOf<Int>()
    for (index in sorted.take(MAX_GLASS_MERGED_MEMBERS)) {
      if (run.isNotEmpty() && run.last() != index - 1) {
        surfaces += GlassCluster(run.map(valid::get))
        run = mutableListOf()
      }
      run += index
    }
    surfaces += GlassCluster(run.map(valid::get))
    ordered.drop(MAX_GLASS_MERGED_MEMBERS).forEach { surfaces += GlassCluster(listOf(it)) }
    if (ordered.size > MAX_GLASS_MERGED_MEMBERS) {
      overflow += GlassClusterOverflow(ordered.map { it.identity }, ordered.size - MAX_GLASS_MERGED_MEMBERS)
    }
  }
  // An intervening singleton must retain registration order relative to disjoint components.
  return GlassClusterModel(
    surfaces.sortedBy { surface -> valid.indexOf(surface.members.first()) },
    opticalSize,
    overflow,
  )
}
