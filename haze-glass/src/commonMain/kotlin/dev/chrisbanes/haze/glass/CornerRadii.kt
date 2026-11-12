// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.withSign

/**
 * Physical corner radii, with each corner's effective continuous-corner smoothing (`0` circular).
 */
internal data class CornerRadii(
  val topLeft: Float,
  val topRight: Float,
  val bottomRight: Float,
  val bottomLeft: Float,
  val topLeftSmoothing: Float = 0f,
  val topRightSmoothing: Float = 0f,
  val bottomRightSmoothing: Float = 0f,
  val bottomLeftSmoothing: Float = 0f,
) {
  fun isZero(): Boolean = topLeft == 0f && topRight == 0f && bottomRight == 0f && bottomLeft == 0f

  val isCircular: Boolean
    get() = topLeftSmoothing == 0f && topRightSmoothing == 0f &&
      bottomRightSmoothing == 0f && bottomLeftSmoothing == 0f

  /** Whether any corner is smoothed, which selects the continuous shader variant. */
  val isContinuous: Boolean get() = !isCircular

  fun withoutSmoothing(): CornerRadii =
    copy(topLeftSmoothing = 0f, topRightSmoothing = 0f, bottomRightSmoothing = 0f, bottomLeftSmoothing = 0f)

  companion object {
    val zero: CornerRadii = CornerRadii(0f, 0f, 0f, 0f)
  }
}

internal operator fun CornerRadii.times(scale: Float): CornerRadii = copy(
  topLeft = topLeft * scale,
  topRight = topRight * scale,
  bottomRight = bottomRight * scale,
  bottomLeft = bottomLeft * scale,
)

internal fun CornerBasedShape.toCornerRadiiPx(
  layerSize: Size,
  density: Density,
  layoutDirection: LayoutDirection,
): CornerRadii {
  val topStartPx = topStart.toPx(layerSize, density)
  val topEndPx = topEnd.toPx(layerSize, density)
  val bottomEndPx = bottomEnd.toPx(layerSize, density)
  val bottomStartPx = bottomStart.toPx(layerSize, density)
  require(topStartPx >= 0f && topEndPx >= 0f && bottomEndPx >= 0f && bottomStartPx >= 0f) {
    "Corner size in Px can't be negative(topStart = $topStartPx, topEnd = $topEndPx, " +
      "bottomEnd = $bottomEndPx, bottomStart = $bottomStartPx)!"
  }
  return toPhysicalCornerRadiiPx(
    topStartPx,
    topEndPx,
    bottomEndPx,
    bottomStartPx,
    layerSize,
    layoutDirection,
    requestedSmoothing,
  )
}

internal fun CornerBasedShape.toValidCornerRadiiPxOrNull(
  layerSize: Size,
  density: Density,
  layoutDirection: LayoutDirection,
): CornerRadii? {
  val topStartPx = topStart.toPx(layerSize, density)
  val topEndPx = topEnd.toPx(layerSize, density)
  val bottomEndPx = bottomEnd.toPx(layerSize, density)
  val bottomStartPx = bottomStart.toPx(layerSize, density)
  if (
    !topStartPx.isFinite() || !topEndPx.isFinite() ||
    !bottomEndPx.isFinite() || !bottomStartPx.isFinite()
  ) {
    return null
  }
  // Negative radii, such as animation overshoot, are treated as square corners.
  return toPhysicalCornerRadiiPx(
    topStartPx.coerceAtLeast(0f),
    topEndPx.coerceAtLeast(0f),
    bottomEndPx.coerceAtLeast(0f),
    bottomStartPx.coerceAtLeast(0f),
    layerSize,
    layoutDirection,
    requestedSmoothing,
  )
}

private val CornerBasedShape.requestedSmoothing: Float
  get() = (this as? ContinuousRoundedCornerShape)?.smoothing ?: 0f

/**
 * Falls back from an invalid or missing shape to [GlassDefaults.continuousShape], then to square
 * corners.
 */
internal fun CornerBasedShape?.resolveCornerRadiiPx(
  layerSize: Size,
  density: Density,
  layoutDirection: LayoutDirection,
): CornerRadii = this?.toValidCornerRadiiPxOrNull(layerSize, density, layoutDirection)
  ?.takeIf { it.isFiniteAndNonNegative() }
  ?: GlassDefaults.continuousShape.toCornerRadiiPx(layerSize, density, layoutDirection)
    .takeIf { it.isFiniteAndNonNegative() }
  ?: CornerRadii.zero

/**
 * Applies Compose's per-side and Skia's per-edge radius normalization, then fits [smoothing] to the
 * normalized radii with [fitSmoothingTo].
 */
internal fun toPhysicalCornerRadiiPx(
  initialTopStartPx: Float,
  initialTopEndPx: Float,
  initialBottomEndPx: Float,
  initialBottomStartPx: Float,
  layerSize: Size,
  layoutDirection: LayoutDirection,
  smoothing: Float = 0f,
): CornerRadii = normalizedPhysicalCornerRadiiPx(
  initialTopStartPx,
  initialTopEndPx,
  initialBottomEndPx,
  initialBottomStartPx,
  layerSize,
  layoutDirection,
).let { radii ->
  if (smoothing == 0f) {
    radii
  } else {
    radii.copy(
      topLeftSmoothing = smoothing,
      topRightSmoothing = smoothing,
      bottomRightSmoothing = smoothing,
      bottomLeftSmoothing = smoothing,
    ).fitSmoothingTo(layerSize)
  }
}

private fun normalizedPhysicalCornerRadiiPx(
  initialTopStartPx: Float,
  initialTopEndPx: Float,
  initialBottomEndPx: Float,
  initialBottomStartPx: Float,
  layerSize: Size,
  layoutDirection: LayoutDirection,
): CornerRadii {
  var topStartPx = initialTopStartPx
  var topEndPx = initialTopEndPx
  var bottomEndPx = initialBottomEndPx
  var bottomStartPx = initialBottomStartPx
  val minDimension = min(layerSize.width, layerSize.height)

  if (topStartPx + bottomStartPx > minDimension) {
    val scale = minDimension / (topStartPx + bottomStartPx)
    topStartPx *= scale
    bottomStartPx *= scale
  }
  if (topEndPx + bottomEndPx > minDimension) {
    val scale = minDimension / (topEndPx + bottomEndPx)
    topEndPx *= scale
    bottomEndPx *= scale
  }
  val physicalRadii = if (layoutDirection == LayoutDirection.Ltr) {
    CornerRadii(
      topLeft = topStartPx,
      topRight = topEndPx,
      bottomRight = bottomEndPx,
      bottomLeft = bottomStartPx,
    )
  } else {
    CornerRadii(
      topLeft = topEndPx,
      topRight = topStartPx,
      bottomRight = bottomStartPx,
      bottomLeft = bottomEndPx,
    )
  }

  var scale = edgeScale(layerSize.width, physicalRadii.topLeft, physicalRadii.topRight)
  scale = min(scale, edgeScale(layerSize.height, physicalRadii.topRight, physicalRadii.bottomRight))
  scale = min(scale, edgeScale(layerSize.width, physicalRadii.bottomRight, physicalRadii.bottomLeft))
  scale = min(scale, edgeScale(layerSize.height, physicalRadii.bottomLeft, physicalRadii.topLeft))

  if (scale == 1.0) return physicalRadii

  return CornerRadii(
    topLeft = (physicalRadii.topLeft.toDouble() * scale).toFloat(),
    topRight = (physicalRadii.topRight.toDouble() * scale).toFloat(),
    bottomRight = (physicalRadii.bottomRight.toDouble() * scale).toFloat(),
    bottomLeft = (physicalRadii.bottomLeft.toDouble() * scale).toFloat(),
  )
}

/**
 * Interpolates radii and smoothing, following [fraction] through overshoot while keeping radii
 * non-negative and smoothing in `0..1`. Refit the result with [fitSmoothingTo].
 */
internal fun lerp(from: CornerRadii, to: CornerRadii, fraction: Float): CornerRadii {
  fun lerp(start: Float, stop: Float) = start + (stop - start) * fraction
  return CornerRadii(
    topLeft = lerp(from.topLeft, to.topLeft).coerceAtLeast(0f),
    topRight = lerp(from.topRight, to.topRight).coerceAtLeast(0f),
    bottomRight = lerp(from.bottomRight, to.bottomRight).coerceAtLeast(0f),
    bottomLeft = lerp(from.bottomLeft, to.bottomLeft).coerceAtLeast(0f),
    topLeftSmoothing = lerp(from.topLeftSmoothing, to.topLeftSmoothing).coerceIn(0f, 1f),
    topRightSmoothing = lerp(from.topRightSmoothing, to.topRightSmoothing).coerceIn(0f, 1f),
    bottomRightSmoothing = lerp(from.bottomRightSmoothing, to.bottomRightSmoothing).coerceIn(0f, 1f),
    bottomLeftSmoothing = lerp(from.bottomLeftSmoothing, to.bottomLeftSmoothing).coerceIn(0f, 1f),
  )
}

internal fun CornerRadii.isFiniteAndNonNegative(): Boolean =
  topLeft.isFinite() && topLeft >= 0f &&
    topRight.isFinite() && topRight >= 0f &&
    bottomRight.isFinite() && bottomRight >= 0f &&
    bottomLeft.isFinite() && bottomLeft >= 0f &&
    topLeftSmoothing in 0f..1f && topRightSmoothing in 0f..1f &&
    bottomRightSmoothing in 0f..1f && bottomLeftSmoothing in 0f..1f

private fun edgeScale(limit: Float, first: Float, second: Float): Double {
  val doubleLimit = limit.toDouble()
  val sum = first.toDouble() + second.toDouble()
  return if (sum > doubleLimit) doubleLimit / sum else 1.0
}

internal fun CornerRadii.toRoundRect(size: Size): RoundRect = RoundRect(
  left = 0f,
  top = 0f,
  right = size.width,
  bottom = size.height,
  topLeftCornerRadius = CornerRadius(topLeft),
  topRightCornerRadius = CornerRadius(topRight),
  bottomRightCornerRadius = CornerRadius(bottomRight),
  bottomLeftCornerRadius = CornerRadius(bottomLeft),
)

/**
 * How far past its radius a fully smoothed continuous corner extends along each edge, as a fraction
 * of the radius. From the widely published reverse-engineered iOS continuous-corner curve.
 */
internal const val CONTINUOUS_CORNER_EXTENT_FACTOR: Float = 0.52866483f

/** The distance from a corner, along each edge, at which a continuous corner meets the edge. */
internal fun continuousCornerExtent(radius: Float, smoothing: Float): Float =
  radius * (1f + CONTINUOUS_CORNER_EXTENT_FACTOR * smoothing)

/**
 * The superellipse exponent of a continuous corner, chosen so that its 45° point stays on the
 * circular corner's 45° point. Exactly `2` (a circle) when [smoothing] is `0`.
 */
internal fun continuousCornerExponent(smoothing: Float): Float {
  if (smoothing == 0f) return 2f
  val inset = (1.0 - 1.0 / sqrt(2.0)) / (1.0 + CONTINUOUS_CORNER_EXTENT_FACTOR * smoothing)
  return (ln(0.5) / ln(1.0 - inset)).toFloat()
}

/**
 * Reduces each corner's smoothing, never its radius, so that adjacent corner extents fit along each
 * edge of [size] and diagonal corner boxes cannot overlap. A capsule's corners become exactly
 * circular. When the radii alone overlap, as in a leaf shape or a lerp overshoot, every corner
 * becomes circular so that the shape renders with the exact circular shaders.
 */
internal fun CornerRadii.fitSmoothingTo(size: Size): CornerRadii {
  if (isCircular) return this
  if (!isRadiusDisjoint(size)) return withoutSmoothing()
  val top = edgeSmoothingLimit(size.width, topLeft, topRight)
  val right = edgeSmoothingLimit(size.height, topRight, bottomRight)
  val bottom = edgeSmoothingLimit(size.width, bottomRight, bottomLeft)
  val left = edgeSmoothingLimit(size.height, bottomLeft, topLeft)
  // Diagonal boxes overlap only when their extents exceed both sides.
  val longest = size.maxDimension
  val falling = edgeSmoothingLimit(longest, topLeft, bottomRight)
  val rising = edgeSmoothingLimit(longest, topRight, bottomLeft)
  return copy(
    topLeftSmoothing = fittedSmoothing(topLeft, topLeftSmoothing, left, top, falling),
    topRightSmoothing = fittedSmoothing(topRight, topRightSmoothing, top, right, rising),
    bottomRightSmoothing = fittedSmoothing(bottomRight, bottomRightSmoothing, right, bottom, falling),
    bottomLeftSmoothing = fittedSmoothing(bottomLeft, bottomLeftSmoothing, bottom, left, rising),
  )
}

/** Each corner's continuous extent as a circular radius, without smoothing. */
internal fun CornerRadii.extents(): CornerRadii = CornerRadii(
  continuousCornerExtent(topLeft, topLeftSmoothing),
  continuousCornerExtent(topRight, topRightSmoothing),
  continuousCornerExtent(bottomRight, bottomRightSmoothing),
  continuousCornerExtent(bottomLeft, bottomLeftSmoothing),
)

/**
 * Whether no two corner boxes can overlap in [size], judging by the radii alone, allowing [slack]
 * pixels of overlap.
 */
internal fun CornerRadii.isRadiusDisjoint(size: Size, slack: Float = 0f): Boolean {
  val width = size.width + slack
  val height = size.height + slack
  val longest = size.maxDimension + slack
  return topLeft + topRight <= width && bottomLeft + bottomRight <= width &&
    topLeft + bottomLeft <= height && topRight + bottomRight <= height &&
    topLeft + bottomRight <= longest && topRight + bottomLeft <= longest
}

private fun edgeSmoothingLimit(length: Float, first: Float, second: Float): Float {
  val radii = first + second
  if (radii <= 0f) return 1f
  return ((length - radii) / (CONTINUOUS_CORNER_EXTENT_FACTOR * radii)).coerceIn(0f, 1f)
}

private fun fittedSmoothing(
  radius: Float,
  smoothing: Float,
  firstEdge: Float,
  secondEdge: Float,
  diagonal: Float,
): Float = if (radius <= 0f) 0f else minOf(smoothing, minOf(firstEdge, secondEdge, diagonal))

/**
 * The point at [angle] (radians, y down) on a continuous corner's superellipse, relative to the
 * centre of its corner box. [angle] `π` to `3π/2` traces the top-left corner from its left edge.
 */
internal fun continuousCornerPoint(angle: Double, extent: Float, exponent: Float): Offset {
  val power = 2.0 / exponent
  val c = cos(angle)
  val s = sin(angle)
  return Offset(
    (extent * abs(c).pow(power).withSign(c)).toFloat(),
    (extent * abs(s).pow(power).withSign(s)).toFloat(),
  )
}

/** This shape as a path in [size], identical to [toRoundRect] when every corner is circular. */
internal fun CornerRadii.toPath(size: Size): Path = Path().apply {
  addContinuousRoundRect(Rect(Offset.Zero, size), this@toPath)
}

/** Adds [radii] in [rect]; circular corners use [Path.addRoundRect]. */
internal fun Path.addContinuousRoundRect(rect: Rect, radii: CornerRadii) {
  if (radii.isCircular) {
    addRoundRect(radii.toRoundRect(rect.size).translate(rect.topLeft))
    return
  }
  // Corners in clockwise order: radius, smoothing, the corner's position, and its start angle.
  addCorner(rect.left, rect.top, 1f, 1f, PI, radii.topLeft, radii.topLeftSmoothing, first = true)
  addCorner(rect.right, rect.top, -1f, 1f, PI * 1.5, radii.topRight, radii.topRightSmoothing)
  addCorner(rect.right, rect.bottom, -1f, -1f, 0.0, radii.bottomRight, radii.bottomRightSmoothing)
  addCorner(rect.left, rect.bottom, 1f, -1f, PI * 0.5, radii.bottomLeft, radii.bottomLeftSmoothing)
  close()
}

private fun Path.addCorner(
  cornerX: Float,
  cornerY: Float,
  inwardX: Float,
  inwardY: Float,
  startAngle: Double,
  radius: Float,
  smoothing: Float,
  first: Boolean = false,
) {
  if (radius <= 0f) {
    if (first) moveTo(cornerX, cornerY) else lineTo(cornerX, cornerY)
    return
  }
  val extent = continuousCornerExtent(radius, smoothing)
  val exponent = continuousCornerExponent(smoothing)
  val centreX = cornerX + inwardX * extent
  val centreY = cornerY + inwardY * extent
  // shortcut: at most 64 line segments per corner; switch to cubic segments if corners of several
  // hundred px show facets.
  val segments = ceil(extent / 2f).toInt().coerceIn(8, 64)
  for (i in 0..segments) {
    val point = continuousCornerPoint(startAngle + PI * 0.5 * i / segments, extent, exponent)
    if (first && i == 0) moveTo(centreX + point.x, centreY + point.y) else lineTo(centreX + point.x, centreY + point.y)
  }
}
