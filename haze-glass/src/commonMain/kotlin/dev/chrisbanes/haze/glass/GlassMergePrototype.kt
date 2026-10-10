// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.Poko

/**
 * Prototype only (#1438), never shipped: merges two rounded members into one Glass surface.
 *
 * [rectA] and [rectB] are in the Glass node's local coordinates. Members whose gap is less than
 * [spacing] are joined by a smooth bridge. The node should cover both members' union bounds
 * inflated by [spacing] on every side, so the merged field is never clipped.
 */
@ExperimentalHazeApi
@Poko
public class GlassMergePrototype(
  public val rectA: DpRect,
  public val shapeA: RoundedCornerShape,
  public val rectB: DpRect,
  public val shapeB: RoundedCornerShape,
  public val spacing: Dp,
) {
  init {
    requireMember("rectA", rectA)
    requireMember("rectB", rectB)
    requireSpecifiedFiniteNonNegative("spacing", spacing)
  }
}

private fun requireMember(property: String, rect: DpRect) {
  require(
    rect.left.value.isFinite() && rect.top.value.isFinite() &&
      rect.width.value.isFinite() && rect.height.value.isFinite() &&
      rect.width > 0.dp && rect.height > 0.dp,
  ) { "$property must be finite with a positive size" }
}

/** [GlassMergePrototype] resolved to material-local pixels. */
@Poko
internal class GlassMergeGeometry(
  val rectA: Rect,
  val radiiA: CornerRadii,
  val rectB: Rect,
  val radiiB: CornerRadii,
  val smoothingPx: Float,
)

internal operator fun GlassMergeGeometry.times(scale: Float): GlassMergeGeometry = GlassMergeGeometry(
  rectA = rectA.scale(scale),
  radiiA = radiiA * scale,
  rectB = rectB.scale(scale),
  radiiB = radiiB * scale,
  smoothingPx = smoothingPx * scale,
)

private fun Rect.scale(scale: Float) = Rect(left * scale, top * scale, right * scale, bottom * scale)

internal fun GlassMergePrototype.resolve(
  density: Density,
  layoutDirection: LayoutDirection,
): GlassMergeGeometry {
  val pxA = with(density) { Rect(rectA.left.toPx(), rectA.top.toPx(), rectA.right.toPx(), rectA.bottom.toPx()) }
  val pxB = with(density) { Rect(rectB.left.toPx(), rectB.top.toPx(), rectB.right.toPx(), rectB.bottom.toPx()) }
  return GlassMergeGeometry(
    rectA = pxA,
    radiiA = shapeA.radiiPx(pxA.size, density, layoutDirection),
    rectB = pxB,
    radiiB = shapeB.radiiPx(pxB.size, density, layoutDirection),
    smoothingPx = with(density) { spacing.toPx() } * overlapSmoothingScale(pxA, pxB),
  )
}

/** The smaller member's size, used to resolve size-responsive optics instead of the container. */
internal fun GlassMergeGeometry.opticsSize(): Size =
  if (rectA.minDimension <= rectB.minDimension) rectA.size else rectB.size

/**
 * The smooth minimum lowers the field by up to `k / 4` wherever both members are equally near, so
 * overlapping members would grow past their union. Keep full smoothing while the members are apart
 * (so bridging is unchanged), and fade it out as they overlap, reaching zero once the overlap depth
 * equals the smaller member's shortest side.
 */
private fun overlapSmoothingScale(a: Rect, b: Rect): Float {
  val separation = maxOf(
    maxOf(a.left - b.right, b.left - a.right),
    maxOf(a.top - b.bottom, b.top - a.bottom),
  )
  if (separation >= 0f) return 1f
  val fullOverlap = minOf(a.minDimension, b.minDimension)
  val t = (-separation / fullOverlap).coerceIn(0f, 1f)
  return 1f - t * t * (3f - 2f * t)
}

private fun RoundedCornerShape.radiiPx(size: Size, density: Density, layoutDirection: LayoutDirection) =
  toValidCornerRadiiPxOrNull(size, density, layoutDirection)?.takeIf { it.isFiniteAndNonNegative() }
    ?: CornerRadii.zero
