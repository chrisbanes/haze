// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.Poko

/**
 * A rounded-rectangle shape with continuous corners, like SwiftUI's
 * `RoundedRectangle(cornerRadius:style: .continuous)`.
 *
 * A circular corner turns straight where its radius ends. A continuous corner keeps its radius but
 * eases into each edge over a longer distance, about 1.53× the radius at full [smoothing].
 *
 * Pass it to [GlassStyleScope.shape] for Glass masking, refraction, lighting, and rim. Because it
 * is a Compose shape, the same instance can be passed to `Modifier.clip`, `Modifier.border`, or an
 * indication so that they match the Glass silhouette.
 *
 * Compose's `Interpolatable.lerp` interpolates corners and smoothing between two continuous shapes.
 * Corner units resolve at the actual size and density, and start/end corners follow layout direction.
 * Finite fractions can overshoot: negative interpolated radii become zero and smoothing stays in
 * `0f..1f`. Other shape types follow Compose's normal interpolation dispatch. If neither shape
 * supports the pair, Compose selects the first below `0.5f` and the second otherwise.
 *
 * Corner sizes resolve like [RoundedCornerShape]'s. When a corner's eased extent would not fit
 * along an edge, its smoothing is reduced, never its radius. A capsule, whose radius is half its
 * shortest side, therefore draws circular ends, and a shape whose corners overlap, such as a leaf,
 * draws circular corners.
 *
 * @param topStart The top-start corner size.
 * @param topEnd The top-end corner size.
 * @param bottomEnd The bottom-end corner size.
 * @param bottomStart The bottom-start corner size.
 * @param smoothing How far corners ease into their edges: `0f` is circular, matching
 * [RoundedCornerShape], and `1f`, the default, is calibrated to look like SwiftUI's `.continuous`
 * style. Finite values outside `0f..1f`, such as animation overshoot, are coerced into that range.
 * @throws IllegalArgumentException if [smoothing] is not finite.
 */
public class ContinuousRoundedCornerShape(
  topStart: CornerSize,
  topEnd: CornerSize,
  bottomEnd: CornerSize,
  bottomStart: CornerSize,
  smoothing: Float = 1f,
) : CornerBasedShape(
  topStart = topStart,
  topEnd = topEnd,
  bottomEnd = bottomEnd,
  bottomStart = bottomStart,
) {

  /** How far corners ease into their edges, in `0f..1f`: `0f` is circular. */
  public val smoothing: Float = run {
    require(smoothing.isFinite()) { "smoothing must be finite, was $smoothing" }
    // Adding 0f turns -0f into 0f, so equal shapes hash equally.
    smoothing.coerceIn(0f, 1f) + 0f
  }

  /**
   * Creates the outline for already-resolved corner sizes: a rectangle when every corner is square,
   * a rounded rectangle when every effective corner is circular, and otherwise a continuous path.
   */
  override fun createOutline(
    size: Size,
    topStart: Float,
    topEnd: Float,
    bottomEnd: Float,
    bottomStart: Float,
    layoutDirection: LayoutDirection,
  ): Outline {
    if (topStart + topEnd + bottomEnd + bottomStart == 0f) return Outline.Rectangle(size.toRect())
    val radii = toPhysicalCornerRadiiPx(
      topStart,
      topEnd,
      bottomEnd,
      bottomStart,
      size,
      layoutDirection,
      smoothing,
    )
    return if (radii.isCircular) {
      Outline.Rounded(radii.toRoundRect(size))
    } else {
      Outline.Generic(radii.toPath(size))
    }
  }

  /** Interpolates with another continuous shape, or returns null for another type. */
  override fun lerp(other: Any?, t: Float): ContinuousRoundedCornerShape? {
    if (other !is ContinuousRoundedCornerShape) return null
    if (t == 0f || this == other) return this
    if (t == 1f) return other
    return ContinuousRoundedCornerShape(
      topStart = interpolatedCorner(topStart, other.topStart, t),
      topEnd = interpolatedCorner(topEnd, other.topEnd, t),
      bottomEnd = interpolatedCorner(bottomEnd, other.bottomEnd, t),
      bottomStart = interpolatedCorner(bottomStart, other.bottomStart, t),
      smoothing = (smoothing.toDouble() + (other.smoothing.toDouble() - smoothing) * t).coerceIn(0.0, 1.0).toFloat(),
    )
  }

  /** Returns a copy of this shape with new corner sizes, keeping its [smoothing]. */
  override fun copy(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
  ): ContinuousRoundedCornerShape = ContinuousRoundedCornerShape(
    topStart = topStart,
    topEnd = topEnd,
    bottomEnd = bottomEnd,
    bottomStart = bottomStart,
    smoothing = smoothing,
  )

  /** Returns a copy of this shape with [smoothing], keeping its corner sizes. */
  public fun copy(smoothing: Float): ContinuousRoundedCornerShape = ContinuousRoundedCornerShape(
    topStart = topStart,
    topEnd = topEnd,
    bottomEnd = bottomEnd,
    bottomStart = bottomStart,
    smoothing = smoothing,
  )

  /** Whether [other] is a [ContinuousRoundedCornerShape] with equal corner sizes and [smoothing]. */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ContinuousRoundedCornerShape) return false
    return topStart == other.topStart &&
      topEnd == other.topEnd &&
      bottomEnd == other.bottomEnd &&
      bottomStart == other.bottomStart &&
      smoothing == other.smoothing
  }

  /** A hash code consistent with [equals]. */
  override fun hashCode(): Int {
    var result = topStart.hashCode()
    result = 31 * result + topEnd.hashCode()
    result = 31 * result + bottomEnd.hashCode()
    result = 31 * result + bottomStart.hashCode()
    result = 31 * result + smoothing.hashCode()
    return result
  }

  /** Describes the corner sizes and [smoothing]. */
  override fun toString(): String = "ContinuousRoundedCornerShape(topStart = $topStart, " +
    "topEnd = $topEnd, bottomEnd = $bottomEnd, bottomStart = $bottomStart, smoothing = $smoothing)"
}

@Poko
private class InterpolatedCornerSize(
  val start: CornerSize,
  val end: CornerSize,
  val fraction: Float,
) : CornerSize {
  override fun toPx(shapeSize: Size, density: Density): Float {
    val equalEndpoints = start == end
    val from = start.toPx(shapeSize, density)
    if (equalEndpoints && !from.isFinite()) return from
    val to = if (equalEndpoints) from else end.toPx(shapeSize, density)
    val radius = from.toDouble() + (to.toDouble() - from) * fraction
    return if (from.isFinite() && to.isFinite() && fraction.isFinite()) {
      // Compose adds two corners before fitting them, so keep that sum finite too.
      radius.coerceIn(0.0, Float.MAX_VALUE.toDouble() / 2.0).toFloat()
    } else {
      radius.toFloat().coerceAtLeast(0f)
    }
  }
}

private fun interpolatedCorner(start: CornerSize, end: CornerSize, fraction: Float): CornerSize =
  InterpolatedCornerSize(start, end, if (start == end) 0f else fraction)

/**
 * Creates a [ContinuousRoundedCornerShape] with the same size applied to all four corners.
 *
 * @param corner The size to apply to each corner.
 * @param smoothing How far corners ease into their edges: `0f` is circular and `1f` looks like
 * SwiftUI's `.continuous` style.
 */
public fun ContinuousRoundedCornerShape(
  corner: CornerSize,
  smoothing: Float = 1f,
): ContinuousRoundedCornerShape = ContinuousRoundedCornerShape(corner, corner, corner, corner, smoothing)

/**
 * Creates a [ContinuousRoundedCornerShape] with the same size applied to all four corners.
 *
 * @param size The size in [Dp] to apply to each corner.
 * @param smoothing How far corners ease into their edges: `0f` is circular and `1f` looks like
 * SwiftUI's `.continuous` style.
 */
public fun ContinuousRoundedCornerShape(
  size: Dp,
  smoothing: Float = 1f,
): ContinuousRoundedCornerShape = ContinuousRoundedCornerShape(CornerSize(size), smoothing)

/**
 * Creates a [ContinuousRoundedCornerShape] with the same size applied to all four corners.
 *
 * @param size The size in pixels to apply to each corner.
 * @param smoothing How far corners ease into their edges: `0f` is circular and `1f` looks like
 * SwiftUI's `.continuous` style.
 */
public fun ContinuousRoundedCornerShape(
  size: Float,
  smoothing: Float = 1f,
): ContinuousRoundedCornerShape = ContinuousRoundedCornerShape(CornerSize(size), smoothing)

/**
 * Creates a [ContinuousRoundedCornerShape] with the same size applied to all four corners.
 *
 * @param percent The size in percent of the shape's shortest side to apply to each corner. `50`
 * makes a capsule, which draws circular ends.
 * @param smoothing How far corners ease into their edges: `0f` is circular and `1f` looks like
 * SwiftUI's `.continuous` style.
 */
public fun ContinuousRoundedCornerShape(
  percent: Int,
  smoothing: Float = 1f,
): ContinuousRoundedCornerShape = ContinuousRoundedCornerShape(CornerSize(percent), smoothing)

/**
 * Creates a [ContinuousRoundedCornerShape] with sizes defined in [Dp].
 *
 * @param topStart The top-start corner size.
 * @param topEnd The top-end corner size.
 * @param bottomEnd The bottom-end corner size.
 * @param bottomStart The bottom-start corner size.
 * @param smoothing How far corners ease into their edges: `0f` is circular and `1f` looks like
 * SwiftUI's `.continuous` style.
 */
public fun ContinuousRoundedCornerShape(
  topStart: Dp = 0.dp,
  topEnd: Dp = 0.dp,
  bottomEnd: Dp = 0.dp,
  bottomStart: Dp = 0.dp,
  smoothing: Float = 1f,
): ContinuousRoundedCornerShape = ContinuousRoundedCornerShape(
  topStart = CornerSize(topStart),
  topEnd = CornerSize(topEnd),
  bottomEnd = CornerSize(bottomEnd),
  bottomStart = CornerSize(bottomStart),
  smoothing = smoothing,
)

/**
 * Creates a [ContinuousRoundedCornerShape] with sizes defined in pixels.
 *
 * @param topStart The top-start corner size in pixels.
 * @param topEnd The top-end corner size in pixels.
 * @param bottomEnd The bottom-end corner size in pixels.
 * @param bottomStart The bottom-start corner size in pixels.
 * @param smoothing How far corners ease into their edges: `0f` is circular and `1f` looks like
 * SwiftUI's `.continuous` style.
 */
public fun ContinuousRoundedCornerShape(
  topStart: Float = 0f,
  topEnd: Float = 0f,
  bottomEnd: Float = 0f,
  bottomStart: Float = 0f,
  smoothing: Float = 1f,
): ContinuousRoundedCornerShape = ContinuousRoundedCornerShape(
  topStart = CornerSize(topStart),
  topEnd = CornerSize(topEnd),
  bottomEnd = CornerSize(bottomEnd),
  bottomStart = CornerSize(bottomStart),
  smoothing = smoothing,
)

/**
 * Creates a [ContinuousRoundedCornerShape] with sizes defined in percent of the shape's shortest
 * side.
 *
 * @param topStartPercent The top-start corner size in percent.
 * @param topEndPercent The top-end corner size in percent.
 * @param bottomEndPercent The bottom-end corner size in percent.
 * @param bottomStartPercent The bottom-start corner size in percent.
 * @param smoothing How far corners ease into their edges: `0f` is circular and `1f` looks like
 * SwiftUI's `.continuous` style.
 */
public fun ContinuousRoundedCornerShape(
  topStartPercent: Int = 0,
  topEndPercent: Int = 0,
  bottomEndPercent: Int = 0,
  bottomStartPercent: Int = 0,
  smoothing: Float = 1f,
): ContinuousRoundedCornerShape = ContinuousRoundedCornerShape(
  topStart = CornerSize(topStartPercent),
  topEnd = CornerSize(topEndPercent),
  bottomEnd = CornerSize(bottomEndPercent),
  bottomStart = CornerSize(bottomStartPercent),
  smoothing = smoothing,
)
