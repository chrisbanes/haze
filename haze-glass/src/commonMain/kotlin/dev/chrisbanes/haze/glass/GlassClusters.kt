// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.Poko
import kotlin.math.hypot

/** Value key deliberately excludes member geometry, colours and interaction declarations. */
internal data class GlassMergeKey(
  val input: HazeInput,
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
  val alpha: Float,
)

internal fun glassMergeKey(
  input: HazeInput,
  qualityFraction: Float,
  effect: GlassRuntimeEffect,
  material: ResolvedGlassStyle,
): GlassMergeKey = GlassMergeKey(
  input, qualityFraction, effect.optics, material.resolvedOptics,
  material.surfaceProfile, material.specularIntensity, material.specularExponent,
  material.edgeShadow, material.edgeSoftnessPx, material.ambientResponse,
  material.chromaticAberrationStrength, material.chromaticAberrationMode,
  material.contrast, material.whitePoint, material.chromaMultiplier,
  material.contentNormalBlend, material.fresnelExponent, effect.lightPosition, material.alpha,
)

/**
 * Registration identity survives geometry updates; ordering is the input list's ordering.
 * Merged fields only model translated, axis-aligned rects, so other transforms never merge.
 */
internal data class GlassMemberSnapshot(
  val identity: Any,
  val rect: Rect,
  val key: GlassMergeKey,
  val material: ResolvedGlassStyle,
  val translationOnly: Boolean = true,
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

internal fun clusterGlassMembers(
  members: List<GlassMemberSnapshot>,
  spacingPx: Float,
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
          member.key == candidate.key && glassMemberGap(member.rect, candidate.rect) < spacingPx / 2f
        ) {
          component += index
          iterator.remove()
        }
      }
    }
    val ordered = component.sorted().map(valid::get)
    surfaces += GlassCluster(ordered.take(MAX_GLASS_MERGED_MEMBERS))
    ordered.drop(MAX_GLASS_MERGED_MEMBERS).forEach { surfaces += GlassCluster(listOf(it)) }
    if (ordered.size > MAX_GLASS_MERGED_MEMBERS) {
      overflow += GlassClusterOverflow(ordered.map { it.identity }, ordered.size - MAX_GLASS_MERGED_MEMBERS)
    }
  }
  // An intervening singleton must retain registration order relative to disjoint components.
  return GlassClusterModel(
    surfaces.sortedBy { surface -> valid.indexOf(surface.members.first()) },
    glassOpticalSize(valid.map { it.rect }),
    overflow,
  )
}
