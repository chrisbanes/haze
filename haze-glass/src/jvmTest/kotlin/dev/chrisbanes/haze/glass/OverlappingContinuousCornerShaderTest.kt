// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Size
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

/**
 * Measures the production continuous corner helpers in Skia against [OverlapReference] for corners
 * whose boxes overlap, as in a leaf (#1749), and for disjoint controls.
 */
class OverlappingContinuousCornerShaderTest {

  @Test
  fun fitSmoothingTo_matchesReferenceRule() {
    OVERLAP_CASES.forEach { case ->
      val radii = CornerRadii(
        case.radii[0].toFloat(),
        case.radii[1].toFloat(),
        case.radii[2].toFloat(),
        case.radii[3].toFloat(),
        case.requestedSmoothing.toFloat(),
        case.requestedSmoothing.toFloat(),
        case.requestedSmoothing.toFloat(),
        case.requestedSmoothing.toFloat(),
      ).fitSmoothingTo(Size(case.width.toFloat(), case.height.toFloat()))
      val fitted = listOf(radii.topLeftSmoothing, radii.topRightSmoothing, radii.bottomRightSmoothing, radii.bottomLeftSmoothing)
      fitted.forEachIndexed { i, smoothing ->
        assertThat(abs(smoothing - case.smoothing[i]), "${case.name} corner $i").isLessThan(1e-6)
      }
    }
  }

  @Test
  fun reference_contourAgreesWithBoundarySamples() {
    OVERLAP_CASES.forEach { case ->
      val reference = OverlapReference(case)
      reference.contour().forEach { (x, y) ->
        assertThat(abs(reference.nearest(x, y).signedDistance), "${case.name} contour ($x, $y)")
          .isLessThan(OverlapThresholds.REFERENCE_PX)
      }
    }
  }

  @Test
  fun reference_circularCasesMatchAnalyticArcs() {
    OVERLAP_CASES.filter { c -> c.smoothing.all { it == 0.0 } }.forEach { case ->
      val reference = OverlapReference(case)
      forEachProbe(case) { x, y ->
        val actual = reference.nearest(x, y).signedDistance
        assertThat(abs(actual - analyticCircularSd(case, x, y)), "${case.name} at ($x, $y)")
          .isLessThan(OverlapThresholds.REFERENCE_PX)
      }
    }
  }

  @Test
  fun sdRoundedRect_matchesReferenceInEveryCase() {
    val failures = OVERLAP_CASES.mapNotNull { case -> measureDistance(case, render(case, NORMAL_FINITE_DIFFERENCE)) }
    assertThat(failures).isEmpty()
  }

  @Test
  fun sdRoundedRect_firstCornerOnlyFailsInOverlappingCases() {
    // The pre-#1749 helper: a single selected corner. Every smoothed leaf must fail, so the
    // measurement detects a missing second corner.
    val firstCornerOnly = GlassShaders.buildOptical(continuous = true).let { source ->
      val second = "if (corner.z <= 0.0) break;\n        sd = max(sd, roundedCornerSd("
      check(source.split(second).size == 2) { "expected one sdRoundedRect corner loop" }
      source.replace(second, "if (corner.z <= 0.0 || k > 0) break;\n        sd = max(sd, roundedCornerSd(")
    }
    val leaves = OVERLAP_CASES.filter { "leaf" in it.geometry.lowercase() && it.requestedSmoothing == 1.0 }
    leaves.forEach { case ->
      assertThat(measureDistance(case, render(case, NORMAL_FINITE_DIFFERENCE, firstCornerOnly)), case.name).isNotNull()
    }
  }

  @Test
  fun shapeGradient_matchesReferenceNormalWithinOpticalBand() {
    // The merged field's gradient: finite differences of sdRoundedRect.
    val failures = OVERLAPPING_CASES.flatMap { case -> measureNormal(case, render(case, NORMAL_FINITE_DIFFERENCE)) }
    assertThat(failures).isEmpty()
  }

  @Test
  fun gradSdRoundedRect_overlappingCornerMatchesCornerAlone() {
    val failures = OVERLAPPING_CASES.flatMap { case -> compareWithCornersAlone(case, NORMAL_GRAD_SD) }
    assertThat(failures).isEmpty()
  }

  @Test
  fun edgeRefractionGradient_overlappingCornerMatchesCornerAlone() {
    val failures = OVERLAPPING_CASES.flatMap { case -> compareWithCornersAlone(case, NORMAL_EDGE_REFRACTION) }
    assertThat(failures).isEmpty()
  }

  // --- Harness ---

  private class ProbeImage(val width: Int, val data: IntArray) {
    /** (sd, normal x, normal y) at the material-space pixel (x, y), whose centre is (x + 0.5, y + 0.5). */
    fun at(x: Int, y: Int): Triple<Double, Double, Double> {
      val i = ((y + PAD) * width + (x + PAD)) * 4
      val encoded = data[i] * 256 + data[i + 1]
      val angle = data[i + 2] / 255.0 * 2 * PI - PI
      return Triple(encoded / 65535.0 * SD_SCALE - SD_SCALE / 2, cos(angle), sin(angle))
    }
  }

  /** Draws the production distance and [normal] for [case], encoded per pixel. */
  private fun render(
    case: OverlapCase,
    normal: String,
    production: String = GlassShaders.buildOptical(continuous = true),
    only: Int? = null,
  ): ProbeImage {
    val extents = FloatArray(4) { if (only == null || only == it) case.extents[it].toFloat() else 0f }
    val source = production.substringBefore("vec4 main(vec2 coord)") + """
      vec4 main(vec2 coord) {
        vec2 p = coord - vec2($PAD.0);
        float sd = sdRoundedRect(p, materialSize, cornerRadii, cornerExponents);
        vec2 n = $normal;
        // 16-bit distance across red and green; the normal's angle in blue.
        float u = floor(clamp(sd / $SD_SCALE + 0.5, 0.0, 1.0) * 65535.0 + 0.5);
        float hi = floor(u / 256.0);
        float angle = atan(n.y, n.x) / 6.28318530718 + 0.5;
        return vec4(hi / 255.0, (u - hi * 256.0) / 255.0, angle, 1.0);
      }
    """.trimIndent()
    val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(source)).apply {
      child("content", Shader.makeEmpty())
      uniform("materialSize", case.width.toFloat(), case.height.toFloat())
      uniform("sampleStep", SAMPLE_STEP_PX)
      uniform("cornerRadii", extents[0], extents[1], extents[2], extents[3])
      uniform("cornerExponents", case.exponents[0].toFloat(), case.exponents[1].toFloat(), case.exponents[2].toFloat(), case.exponents[3].toFloat())
    }
    val width = case.width.toInt() + 2 * PAD
    val height = case.height.toInt() + 2 * PAD
    val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
    val bitmap = Bitmap().apply { check(allocPixels(info)) }
    Canvas(bitmap).drawRect(Rect.makeWH(width.toFloat(), height.toFloat()), Paint().apply { shader = builder.makeShader() })
    val bytes = checkNotNull(bitmap.readPixels(info, width * 4, 0, 0))
    return ProbeImage(width, IntArray(bytes.size) { bytes[it].toInt() and 0xFF })
  }

  /** Contour, distance within the band, and interior coverage; a failure summary or null. */
  private fun measureDistance(case: OverlapCase, image: ProbeImage): String? {
    val reference = OverlapReference(case)
    var crossings = 0
    var referenceCrossings = 0
    var contourMax = 0.0
    fun crossing(x0: Int, y0: Int, x1: Int, y1: Int) {
      val a = image.at(x0, y0).first
      val b = image.at(x1, y1).first
      if (reference.inside(x0 + 0.5, y0 + 0.5) != reference.inside(x1 + 0.5, y1 + 0.5)) referenceCrossings++
      if ((a < 0) == (b < 0) || a == b) return
      // Located linearly between the pixel centres and measured with the reference distance.
      val t = a / (a - b)
      crossings++
      contourMax = max(contourMax, abs(reference.nearest(x0 + 0.5 + (x1 - x0) * t, y0 + 0.5 + (y1 - y0) * t).signedDistance))
    }
    for (y in -PAD until case.height.toInt() + PAD) {
      for (x in -PAD until case.width.toInt() + PAD) {
        if (x + 1 < case.width.toInt() + PAD) crossing(x, y, x + 1, y)
        if (y + 1 < case.height.toInt() + PAD) crossing(x, y, x, y + 1)
      }
    }
    var distanceMax = 0.0
    var interior = 0
    var covered = 0
    val references = nearest(case)
    forEachProbe(case) { px, py ->
      val sd = image.at((px - 0.5).toInt(), (py - 0.5).toInt()).first
      val ref = references.getValue(px to py).signedDistance
      if (ref < 0) {
        interior++
        if (sd < 0) covered++
      }
      if (abs(ref) <= OverlapThresholds.DISTANCE_BAND_PX) distanceMax = max(distanceMax, abs(sd - ref))
    }
    val coverage = covered.toDouble() / max(interior, 1)
    val passes = crossings >= referenceCrossings * 0.9 && contourMax <= OverlapThresholds.CONTOUR_PX &&
      distanceMax <= OverlapThresholds.DISTANCE_PX && coverage >= OverlapThresholds.INTERIOR_COVERAGE
    val summary = "${case.name}: crossings $crossings/$referenceCrossings contourMax %.3f distanceMax %.3f coverage %.4f"
      .format(contourMax, distanceMax, coverage)
    println("1749 $summary")
    return if (passes) null else summary
  }

  /**
   * Normals within the optical band inside the boundary and the distance band outside it, away from
   * sharp joins. Boundary points within the tie distance of the nearest are tied: a
   * normal matching any of them, or lying between two of them on a medial axis, where the distance
   * has no single gradient, is accepted.
   */
  private fun measureNormal(case: OverlapCase, image: ProbeImage): List<String> {
    val references = nearest(case)
    val reference = OverlapReference(case)
    val failures = mutableListOf<String>()
    var worst = 1.0
    var probes = 0
    forEachProbe(case) { px, py ->
      val ref = references.getValue(px to py)
      if (ref.signedDistance < -OPTICAL_BAND_PX || ref.signedDistance > OverlapThresholds.DISTANCE_BAND_PX) return@forEachProbe
      if (reference.sharpCorners.any { (cx, cy) -> hypot(px - cx, py - cy) < OverlapThresholds.JOIN_EXCLUSION_PX }) return@forEachProbe
      probes++
      val (sd, nx, ny) = image.at((px - 0.5).toInt(), (py - 0.5).toInt())
      val nearest = abs(ref.signedDistance)
      val tied = ref.minima.filter { it[0] - nearest < OverlapThresholds.TIE_PX }.map { it[1] to it[2] } + (ref.normalX to ref.normalY)
      val dot = tied.maxOf { (ax, ay) -> nx * ax + ny * ay }
      val between = tied.any { a -> tied.any { b -> isBetween(nx, ny, a, b) } }
      worst = minOf(worst, if (between) 1.0 else dot)
      if (dot <= OverlapThresholds.NORMAL_DOT && !between && failures.size < 6) {
        failures += "${case.name} (%.1f, %.1f) sd %.2f ref %.2f dot %.4f angle %.1f°".format(
          px, py, sd, ref.signedDistance, dot, Math.toDegrees(atan2(ny, nx)),
        )
      }
    }
    println("1749 ${case.name}: worst normal dot %.4f over %d probes within %.0f px".format(worst, probes, OPTICAL_BAND_PX))
    return failures
  }

  /** Whether (x, y) lies strictly inside the narrower angle from [a] to [b]. */
  private fun isBetween(x: Double, y: Double, a: Pair<Double, Double>, b: Pair<Double, Double>): Boolean {
    fun cross(p: Pair<Double, Double>, qx: Double, qy: Double) = p.first * qy - p.second * qx
    val ab = cross(a, b.first, b.second)
    if (abs(ab) < 1e-6 || a.first * b.first + a.second * b.second < -0.999) return false
    return cross(a, x, y) * ab > 0 && cross(b, x, y) * ab < 0
  }

  /** The reference at every probe of [case], shared by every test in this JVM. */
  private fun nearest(case: OverlapCase): Map<Pair<Double, Double>, OverlapNearest> = NEAREST.getOrPut(case.name) {
    val reference = OverlapReference(case)
    buildMap { forEachProbe(case) { x, y -> put(x to y, reference.nearest(x, y)) } }
  }

  /**
   * Compares [normal] in [case] with the same corner drawn without the other corners, wherever the
   * reference's nearest boundary is that corner within the optical band inside the shape, at least
   * [CORNER_MARGIN_PX] nearer than any other piece. Overlapping a corner must not change its normal.
   */
  private fun compareWithCornersAlone(case: OverlapCase, normal: String): List<String> {
    val image = render(case, normal)
    val references = nearest(case)
    val failures = mutableListOf<String>()
    for (corner in 0 until 4) {
      if (case.extents[corner] <= 0.0) continue
      val alone = render(case, normal, only = corner)
      forEachProbe(case) { px, py ->
        val ref = references.getValue(px to py)
        if (ref.piece != corner || ref.signedDistance > 0.0 || ref.signedDistance < -OPTICAL_BAND_PX) return@forEachProbe
        if (ref.otherDistance + ref.signedDistance < CORNER_MARGIN_PX) return@forEachProbe
        val x = (px - 0.5).toInt()
        val y = (py - 0.5).toInt()
        val (_, nx, ny) = image.at(x, y)
        val (_, ax, ay) = alone.at(x, y)
        val dot = nx * ax + ny * ay
        if (dot <= ALONE_DOT && failures.size < 6) {
          failures += "${case.name} corner $corner (%.1f, %.1f) ref %.2f dot %.4f".format(px, py, ref.signedDistance, dot)
        }
      }
    }
    return failures
  }

  /** Every pixel centre in the padded probe area. */
  private inline fun forEachProbe(case: OverlapCase, block: (Double, Double) -> Unit) {
    for (y in -PAD until case.height.toInt() + PAD) {
      for (x in -PAD until case.width.toInt() + PAD) block(x + 0.5, y + 0.5)
    }
  }

  /** The exact distance for circular corners: the rectangle, cut by every corner disc whose box contains the point. */
  private fun analyticCircularSd(case: OverlapCase, x: Double, y: Double): Double {
    val w = case.width
    val h = case.height
    val dx = max(-x, x - w)
    val dy = max(-y, y - h)
    var sd = if (dx > 0 || dy > 0) hypot(max(dx, 0.0), max(dy, 0.0)) else max(dx, dy)
    for (i in 0 until 4) {
      val r = case.radii[i]
      if (r <= 0.0) continue
      val cx = case.centreX(i)
      val cy = case.centreY(i)
      if ((x - cx) * OverlapCase.SIGN_X[i] > 0 && (y - cy) * OverlapCase.SIGN_Y[i] > 0) sd = max(sd, hypot(x - cx, y - cy) - r)
    }
    return sd
  }

  private companion object {
    /**
     * Cases whose diagonal corner boxes overlap. The disjoint capsule and r16 control keep their
     * single-corner helpers unchanged: their first-order distance bends normals (dot ≥ 0.96) about
     * half an extent inside, and their corner normals blend in adjacent edges by design.
     */
    val OVERLAPPING_CASES = OVERLAP_CASES.filter { it.geometry != "control200x100r16" && it.geometry != "capsule200x80" }

    val NEAREST = java.util.concurrent.ConcurrentHashMap<String, Map<Pair<Double, Double>, OverlapNearest>>()

    const val PAD = 8
    const val SD_SCALE = 256.0
    const val SAMPLE_STEP_PX = 1f

    /**
     * Consumers read normals within the refraction height of the edge, a quarter of the shortest
     * side by default: 25 px for these 100 px shapes.
     */
    const val OPTICAL_BAND_PX = 25.0

    /** Pieces nearer than this to a tie are left to the reference comparison. */
    const val CORNER_MARGIN_PX = 1.0

    /** Two 8-bit encoded angles one step apart. */
    const val ALONE_DOT = 0.999

    const val NORMAL_FINITE_DIFFERENCE = """normalize(vec2(
          sdRoundedRect(p + vec2(0.25, 0.0), materialSize, cornerRadii, cornerExponents) -
            sdRoundedRect(p - vec2(0.25, 0.0), materialSize, cornerRadii, cornerExponents),
          sdRoundedRect(p + vec2(0.0, 0.25), materialSize, cornerRadii, cornerExponents) -
            sdRoundedRect(p - vec2(0.0, 0.25), materialSize, cornerRadii, cornerExponents)
        ))"""
    const val NORMAL_EDGE_REFRACTION = "normalize(edgeRefractionGradient(p, materialSize, cornerRadii, cornerExponents))"
    const val NORMAL_GRAD_SD = "normalize(gradSdRoundedRect(p, materialSize, cornerRadii, cornerExponents, 1.0))"
  }
}
