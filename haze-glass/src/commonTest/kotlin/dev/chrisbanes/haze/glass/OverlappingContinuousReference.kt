// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/*
 * An independent CPU reference for continuous corners whose boxes overlap (#1749): analytic
 * membership and densely sampled boundaries. Nothing here uses shader code or the corner paths.
 */

/** #1749 metrics, frozen before the shaders were measured against [OverlapReference]. */
internal object OverlapThresholds {
  const val CONTOUR_PX = 0.5
  const val NORMAL_DOT = 0.99
  const val DISTANCE_PX = 0.5
  const val DISTANCE_BAND_PX = 8.0
  const val JOIN_EXCLUSION_PX = 2.0

  /** Boundary points within this distance of the nearest are a tie: either normal is accepted. */
  const val TIE_PX = 0.5
  const val INTERIOR_COVERAGE = 0.95
  const val REFERENCE_PX = 0.1
}

/** A frozen #1749 case: logical radii in px at density 1, physical after [rtl] mapping. */
internal data class OverlapCase(
  val geometry: String,
  val width: Double,
  val height: Double,
  val topStart: Double,
  val topEnd: Double,
  val bottomEnd: Double,
  val bottomStart: Double,
  val requestedSmoothing: Double,
  val rtl: Boolean,
) {
  val name: String get() = "$geometry-s$requestedSmoothing-${if (rtl) "rtl" else "ltr"}"

  /** Physical radii: top-left, top-right, bottom-right, bottom-left. */
  val radii: DoubleArray = if (rtl) {
    doubleArrayOf(topEnd, topStart, bottomStart, bottomEnd)
  } else {
    doubleArrayOf(topStart, topEnd, bottomEnd, bottomStart)
  }

  /** Each corner keeps the adjacent-edge smoothing limits; diagonal corners may overlap (#1749). */
  val smoothing: DoubleArray = DoubleArray(4) { i ->
    val r = radii[i]
    if (r <= 0.0) {
      0.0
    } else {
      // Corner i touches horizontal edge (i, i xor 1) and vertical edge (i, 3 - i).
      val horizontal = edgeLimit(width, r, radii[i xor 1])
      val vertical = edgeLimit(height, r, radii[3 - i])
      minOf(requestedSmoothing, horizontal, vertical)
    }
  }

  val extents: DoubleArray = DoubleArray(4) { i ->
    radii[i] * (1.0 + CONTINUOUS_CORNER_EXTENT_FACTOR * smoothing[i])
  }

  val exponents: DoubleArray = DoubleArray(4) { i ->
    continuousCornerExponent(smoothing[i].toFloat()).toDouble()
  }

  fun centreX(i: Int): Double = if (SIGN_X[i] < 0) extents[i] else width - extents[i]
  fun centreY(i: Int): Double = if (SIGN_Y[i] < 0) extents[i] else height - extents[i]

  /** Whether each edge's radii fit, so Compose/Skia normalization leaves the radii unchanged. */
  val radiiNeedNoNormalization: Boolean
    get() = radii[0] + radii[1] <= width && radii[3] + radii[2] <= width &&
      radii[0] + radii[3] <= height && radii[1] + radii[2] <= height

  private fun edgeLimit(length: Double, first: Double, second: Double): Double {
    val sum = first + second
    if (sum <= 0.0) return 1.0
    return ((length - sum) / (CONTINUOUS_CORNER_EXTENT_FACTOR * sum)).coerceIn(0.0, 1.0)
  }

  companion object {
    val SIGN_X = intArrayOf(-1, 1, 1, -1)
    val SIGN_Y = intArrayOf(-1, -1, 1, 1)
  }
}

/** The frozen #1749 cases: six geometries, smoothing 0, 0.5 and 1, LTR and RTL. */
internal val OVERLAP_CASES: List<OverlapCase> = buildList {
  val geometries = listOf(
    OverlapGeometry("leaf60", 100.0, 100.0, 60.0, 0.0, 60.0, 0.0),
    OverlapGeometry("leaf70x55", 100.0, 100.0, 70.0, 0.0, 55.0, 0.0),
    OverlapGeometry("mirroredLeaf70x55", 100.0, 100.0, 0.0, 70.0, 0.0, 55.0),
    OverlapGeometry("diagonal40", 100.0, 100.0, 40.0, 0.0, 40.0, 0.0),
    OverlapGeometry("capsule200x80", 200.0, 80.0, 40.0, 40.0, 40.0, 40.0),
    OverlapGeometry("control200x100r16", 200.0, 100.0, 16.0, 16.0, 16.0, 16.0),
  )
  for (g in geometries) {
    for (s in listOf(0.0, 0.5, 1.0)) {
      for (rtl in listOf(false, true)) {
        add(OverlapCase(g.name, g.width, g.height, g.topStart, g.topEnd, g.bottomEnd, g.bottomStart, s, rtl))
      }
    }
  }
}

internal data class OverlapGeometry(
  val name: String,
  val width: Double,
  val height: Double,
  val topStart: Double,
  val topEnd: Double,
  val bottomEnd: Double,
  val bottomStart: Double,
)

/** The nearest reference boundary point to a query, and the same for the best other boundary piece. */
internal data class OverlapNearest(
  val signedDistance: Double,
  val normalX: Double,
  val normalY: Double,
  val piece: Int,
  /** Distance to the nearest point on any other piece, or infinity. */
  val otherDistance: Double,
  val otherNormalX: Double,
  val otherNormalY: Double,
  /** Unsigned distance and signed-distance-gradient normal (x, y) to each reachable piece. */
  val pieces: List<DoubleArray> = emptyList(),
  /**
   * Each distinct local distance minimum along the boundary, as (distance, normal x, normal y,
   * point x, point y), including two minima on the same piece.
   */
  val minima: List<DoubleArray> = emptyList(),
)

/**
 * Independent CPU reference: analytic membership, dense boundary sampling with segment projection,
 * and ray bisection. It never uses candidate shader code or `addContinuousRoundRect` polylines.
 */
internal class OverlapReference(val case: OverlapCase) {
  private val w = case.width
  private val h = case.height

  /** Corner constraint value, `<= 0` inside, or null when the point is outside that corner's box. */
  fun constraint(i: Int, x: Double, y: Double): Double? {
    val e = case.extents[i]
    if (e <= 0.0) return null
    val dx = x - case.centreX(i)
    val dy = y - case.centreY(i)
    if (dx * OverlapCase.SIGN_X[i] <= 0.0 || dy * OverlapCase.SIGN_Y[i] <= 0.0) return null
    val n = case.exponents[i]
    return (abs(dx) / e).pow(n) + (abs(dy) / e).pow(n) - 1.0
  }

  fun inside(x: Double, y: Double, tolerance: Double = 0.0): Boolean {
    if (x < -tolerance || y < -tolerance || x > w + tolerance || y > h + tolerance) return false
    for (i in 0 until 4) {
      val c = constraint(i, x, y) ?: continue
      if (c > tolerance) return false
    }
    return true
  }

  // Boundary samples: x, y, outward normal x/y, piece id (0-3 corners, 4-7 top/right/bottom/left).
  private val sx = ArrayList<Double>()
  private val sy = ArrayList<Double>()
  private val snx = ArrayList<Double>()
  private val sny = ArrayList<Double>()
  private val spiece = ArrayList<Int>()
  private val coarse = ArrayList<Int>()

  init {
    for (i in 0 until 4) sampleCorner(i)
    sampleEdge(4, 0.0, 0.0, w, 0.0, 0.0, -1.0)
    sampleEdge(5, w, 0.0, w, h, 1.0, 0.0)
    sampleEdge(6, w, h, 0.0, h, 0.0, 1.0)
    sampleEdge(7, 0.0, h, 0.0, 0.0, -1.0, 0.0)
    for (k in sx.indices step COARSE_STRIDE) coarse += k
  }

  val sampleCount: Int get() = sx.size

  private fun add(x: Double, y: Double, nx: Double, ny: Double, piece: Int) {
    if (!inside(x, y, 1e-9)) return
    sx += x
    sy += y
    snx += nx
    sny += ny
    spiece += piece
  }

  /** Samples one corner in two halves, each parameterized by its smaller coordinate. */
  private fun sampleCorner(i: Int) {
    val e = case.extents[i]
    if (e <= 0.0) return
    val n = case.exponents[i]
    val vMax = 0.5.pow(1.0 / n)
    val cx = case.centreX(i)
    val cy = case.centreY(i)
    val signX = OverlapCase.SIGN_X[i].toDouble()
    val signY = OverlapCase.SIGN_Y[i].toDouble()
    // Order samples along the arc so adjacent samples are adjacent on the curve.
    val points = ArrayList<DoubleArray>()
    for (k in 0..CORNER_SAMPLES) {
      val v = vMax * k / CORNER_SAMPLES
      val u = (1.0 - v.pow(n)).coerceAtLeast(0.0).pow(1.0 / n)
      points += doubleArrayOf(u, v) // half where x >= y
    }
    for (k in CORNER_SAMPLES - 1 downTo 0) {
      val v = vMax * k / CORNER_SAMPLES
      val u = (1.0 - v.pow(n)).coerceAtLeast(0.0).pow(1.0 / n)
      points += doubleArrayOf(v, u) // half where y >= x
    }
    for ((ax, ay) in points.map { it[0] to it[1] }) {
      val gx = ax.pow(n - 1.0)
      val gy = ay.pow(n - 1.0)
      val len = hypot(gx, gy)
      add(cx + signX * ax * e, cy + signY * ay * e, signX * gx / len, signY * gy / len, i)
    }
  }

  private fun sampleEdge(piece: Int, x0: Double, y0: Double, x1: Double, y1: Double, nx: Double, ny: Double) {
    val length = hypot(x1 - x0, y1 - y0)
    val count = (length / EDGE_SPACING).toInt().coerceAtLeast(1)
    for (k in 0..count) {
      val t = k.toDouble() / count
      add(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, nx, ny, piece)
    }
  }

  fun nearest(x: Double, y: Double): OverlapNearest {
    // Coarse candidates within a generous margin of the coarse minimum, then a local refine.
    var coarseMin = Double.MAX_VALUE
    for (k in coarse) coarseMin = minOf(coarseMin, hypot(sx[k] - x, sy[k] - y))
    val bestPerPiece = DoubleArray(8) { Double.MAX_VALUE }
    val pointPerPiece = Array(8) { DoubleArray(4) }
    val visited = mutableSetOf<Int>()
    for (k in coarse) {
      if (hypot(sx[k] - x, sy[k] - y) > coarseMin + COARSE_MARGIN) continue
      val from = (k - COARSE_STRIDE).coerceAtLeast(0)
      val to = (k + COARSE_STRIDE).coerceAtMost(sx.size - 1)
      for (j in from..to) visited += j
      for (j in from until to) {
        if (spiece[j] != spiece[j + 1]) continue
        // Project onto the segment between adjacent samples of the same piece.
        val ax = sx[j]
        val ay = sy[j]
        val bx = sx[j + 1]
        val by = sy[j + 1]
        val lx = bx - ax
        val ly = by - ay
        val l2 = lx * lx + ly * ly
        // Adjacent samples across a gap of excluded samples do not form a boundary segment.
        if (l2 > MAX_SEGMENT * MAX_SEGMENT) continue
        val t = if (l2 > 0.0) (((x - ax) * lx + (y - ay) * ly) / l2).coerceIn(0.0, 1.0) else 0.0
        val qx = ax + lx * t
        val qy = ay + ly * t
        val d = hypot(x - qx, y - qy)
        val piece = spiece[j]
        if (d < bestPerPiece[piece]) {
          bestPerPiece[piece] = d
          val nx = snx[j] + (snx[j + 1] - snx[j]) * t
          val ny = sny[j] + (sny[j + 1] - sny[j]) * t
          val nl = hypot(nx, ny)
          pointPerPiece[piece] = doubleArrayOf(qx, qy, nx / nl, ny / nl)
        }
      }
    }
    val sign = if (inside(x, y)) -1.0 else 1.0
    val best = bestPerPiece.indices.minBy { bestPerPiece[it] }
    val other = bestPerPiece.indices.filter { it != best }.minBy { bestPerPiece[it] }
    fun normal(piece: Int): Pair<Double, Double> {
      val p = pointPerPiece[piece]
      val d = bestPerPiece[piece]
      // The signed-distance gradient away from the boundary; the boundary normal on it.
      return if (d > NORMAL_FROM_POINT_MIN) {
        sign * (x - p[0]) / d to sign * (y - p[1]) / d
      } else {
        p[2] to p[3]
      }
    }
    val (nx, ny) = normal(best)
    val (onx, ony) = if (bestPerPiece[other] < Double.MAX_VALUE) normal(other) else 0.0 to 0.0
    val pieces = bestPerPiece.indices.filter { bestPerPiece[it] < Double.MAX_VALUE }.map {
      val (px, py) = normal(it)
      doubleArrayOf(bestPerPiece[it], px, py)
    }
    return OverlapNearest(sign * bestPerPiece[best], nx, ny, best, bestPerPiece[other], onx, ony, pieces, localMinima(x, y, sign, visited))
  }

  /**
   * Local minima of the distance from (x, y) over [visited] samples, compared with neighbours on the
   * same piece and refined onto adjacent segments; minima within 1 px of a nearer one are merged.
   */
  private fun localMinima(x: Double, y: Double, sign: Double, visited: Set<Int>): List<DoubleArray> {
    fun d(j: Int) = hypot(sx[j] - x, sy[j] - y)
    fun neighbour(j: Int, k: Int) = k in visited && spiece[k] == spiece[j] &&
      hypot(sx[k] - sx[j], sy[k] - sy[j]) <= MAX_SEGMENT
    val found = mutableListOf<DoubleArray>()
    for (j in visited) {
      val here = d(j)
      if (neighbour(j, j - 1) && d(j - 1) < here) continue
      if (neighbour(j, j + 1) && d(j + 1) < here) continue
      // Refine onto the adjacent segments.
      var best = doubleArrayOf(here, sx[j], sy[j], snx[j], sny[j])
      for (k in listOf(j - 1, j + 1)) {
        if (!neighbour(j, k)) continue
        val lx = sx[k] - sx[j]
        val ly = sy[k] - sy[j]
        val l2 = lx * lx + ly * ly
        val t = if (l2 > 0.0) (((x - sx[j]) * lx + (y - sy[j]) * ly) / l2).coerceIn(0.0, 1.0) else 0.0
        val qx = sx[j] + lx * t
        val qy = sy[j] + ly * t
        val dq = hypot(x - qx, y - qy)
        if (dq < best[0]) best = doubleArrayOf(dq, qx, qy, snx[j] + (snx[k] - snx[j]) * t, sny[j] + (sny[k] - sny[j]) * t)
      }
      found += best
    }
    val merged = mutableListOf<DoubleArray>()
    for (m in found.sortedBy { it[0] }) {
      if (merged.none { hypot(it[1] - m[1], it[2] - m[2]) <= 1.0 }) merged += m
    }
    return merged.map { (dist, qx, qy, bnx, bny) ->
      val (nx, ny) = if (dist > NORMAL_FROM_POINT_MIN) {
        sign * (x - qx) / dist to sign * (y - qy) / dist
      } else {
        val l = hypot(bnx, bny)
        bnx / l to bny / l
      }
      doubleArrayOf(dist, nx, ny, qx, qy)
    }
  }

  /** The zero contour along [rays] rays from the rectangle centre, bisected to 1e-4 px. */
  fun contour(rays: Int = 720): List<Pair<Double, Double>> {
    val cx = w / 2
    val cy = h / 2
    check(inside(cx, cy)) { "${case.name}: centre outside" }
    return (0 until rays).map { k ->
      val angle = 2 * PI * k / rays
      val dx = cos(angle)
      val dy = sin(angle)
      var lo = 0.0
      var hi = 0.5
      while (inside(cx + dx * hi, cy + dy * hi)) {
        lo = hi
        hi += 0.5
      }
      while (hi - lo > 1e-4) {
        val mid = (lo + hi) / 2
        if (inside(cx + dx * mid, cy + dy * mid)) lo = mid else hi = mid
      }
      val r = (lo + hi) / 2
      cx + dx * r to cy + dy * r
    }
  }

  /** Rectangle corners with zero radius: sharp joins excluded from normal checks. */
  val sharpCorners: List<Pair<Double, Double>> = (0 until 4).filter { case.extents[it] <= 0.0 }.map {
    (if (OverlapCase.SIGN_X[it] < 0) 0.0 else w) to (if (OverlapCase.SIGN_Y[it] < 0) 0.0 else h)
  }

  companion object {
    const val CORNER_SAMPLES = 4000
    const val EDGE_SPACING = 0.02
    const val COARSE_STRIDE = 20
    const val COARSE_MARGIN = 1.0
    const val NORMAL_FROM_POINT_MIN = 0.25
    const val MAX_SEGMENT = 1.0
  }
}
