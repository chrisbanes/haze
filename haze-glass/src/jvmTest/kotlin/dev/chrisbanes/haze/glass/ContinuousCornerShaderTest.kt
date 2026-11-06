// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

/**
 * Evaluates the Glass shader helpers in Skia against the Kotlin corner geometry. Each probe uses the
 * source variant that the renderer selects for its corners.
 */
class ContinuousCornerShaderTest {

  @Test
  fun sdRoundedRect_zeroSmoothingMatchesCircularFormula() {
    val key = opticalKey(ContinuousRoundedCornerShape(RADIUS.dp, smoothing = 0f))
    assertThat(continuousCorners(key.cornerRadii, key.mergedField)).isFalse()
    for (i in 0 until 20) {
      val point = Offset(5f + (i % 5) * 20f, 3f + (i / 5) * 24f)
      val circle = max(max(-point.x, -point.y), hypot(point.x - RADIUS, point.y - RADIUS) - RADIUS)
      assertThat(abs(difference(key, point, CIRCULAR_SD, circle)), "sd at $point").isLessThan(1e-3f)
    }
  }

  @Test
  fun sdRoundedRect_overlappingCircularCornersKeepEveryArc() {
    // A leaf: the top-left and bottom-right boxes overlap, so both arcs must apply.
    val key = opticalKey(RoundedCornerShape(topStartPercent = 100, bottomEndPercent = 100))
    assertThat(continuousCorners(key.cornerRadii, key.mergedField)).isFalse()
    val size = MATERIAL_SIZE.width
    listOf(Offset(390f, 390f), Offset(10f, 10f), Offset(200f, 200f), Offset(300f, 100f)).forEach { point ->
      val topLeft = hypot(point.x - size, point.y - size) - size
      val bottomRight = hypot(point.x, point.y) - size
      val expected = maxOf(maxOf(-point.x, -point.y, point.x - size), maxOf(point.y - size, topLeft, bottomRight))
      assertThat(abs(difference(key, point, CIRCULAR_SD, expected)), "sd at $point").isLessThan(1e-3f)
    }
  }

  @Test
  fun sdRoundedRect_continuousBoundaryMatchesKotlinPath() {
    val key = opticalKey(continuous)
    assertThat(continuousCorners(key.cornerRadii, key.mergedField)).isTrue()
    boundaryPoints().forEach { (point, _) ->
      assertThat(abs(probe(key, point, SD, scale = 4f)), "sd at $point").isLessThan(0.5f)
    }
  }

  @Test
  fun sdRoundedRect_continuousDiffersFromCircle() {
    // Past the circle's 45° point, the continuous boundary lies inside the circular corner.
    val (boundary, _) = topLeftBoundaryPoint(PI * 1.41)
    val centre = Offset(RADIUS, RADIUS)
    val outward = (boundary - centre) / (boundary - centre).getDistance()
    val circleSd = (boundary - centre).getDistance() - RADIUS
    val point = boundary - outward * (circleSd / 2f)
    assertThat((point - centre).getDistance() - RADIUS).isLessThan(-0.75f)

    assertThat(probe(opticalKey(continuous), point, SD, scale = 16f)).isGreaterThan(0.75f)
    assertThat(probe(opticalKey(ContinuousRoundedCornerShape(RADIUS.dp, smoothing = 0f)), point, CIRCULAR_SD, scale = 16f))
      .isLessThan(-0.75f)
  }

  @Test
  fun continuousVariant_circularCornerMatchesBase() {
    // Smoothed top corners select the continuous source; its circular bottom corners must match the
    // base source, which computes the same arc.
    val circular = opticalKey(RoundedCornerShape(80.dp, 80.dp, 40.dp, 40.dp))
    val mixed = circular.copy(cornerRadii = CornerRadii(80f, 80f, 40f, 40f, 1f, 1f, 0f, 0f))
    assertThat(continuousCorners(mixed.cornerRadii, mixed.mergedField)).isTrue()
    val size = MATERIAL_SIZE.width
    for (i in 0 until 20) {
      // Ten points in each bottom corner box, inside and outside the arc.
      val x = 2f + (i % 5) * 9f
      val y = size - 2f - (i / 5 % 2) * 17f - (i % 3) * 5f
      val point = if (i < 10) Offset(x, y) else Offset(size - x, y)
      val centre = Offset(if (i < 10) 40f else size - 40f, size - 40f)
      val arc = max(maxOf(-point.x, -point.y, point.x - size, point.y - size), (point - centre).getDistance() - 40f)
      assertThat(abs(difference(circular, point, CIRCULAR_SD, arc)), "base sd at $point").isLessThan(1e-3f)
      assertThat(abs(difference(mixed, point, SD, arc)), "continuous sd at $point").isLessThan(1e-3f)
    }
  }

  @Test
  fun capsule_resolvesCircularShader() {
    val size = Size(400f, 120f)
    val capsule = opticalKey(ContinuousRoundedCornerShape(percent = 50), size)
    assertThat(continuousCorners(capsule.cornerRadii, capsule.mergedField)).isFalse()
    // Identical shader inputs render identical pixels.
    assertThat(capsule).isEqualTo(opticalKey(RoundedCornerShape(50), size))
  }

  @Test
  fun shapeSd_mergedContinuousMemberMatchesStandalone() {
    val radii = continuous.resolveCornerRadiiPx(MATERIAL_SIZE, Density(1f), LayoutDirection.Ltr)
    val field = GlassMergedField(
      members = listOf(
        GlassMergedMember(Rect(Offset.Zero, MATERIAL_SIZE), radii, Color.Transparent, Color.Transparent),
        GlassMergedMember(Rect(Offset(1000f, 1000f), MATERIAL_SIZE), radii, Color.Transparent, Color.Transparent),
      ),
      mergeDistancePx = 0f,
      opticalSize = MATERIAL_SIZE,
    )
    assertThat(field.isContinuous).isTrue()
    boundaryPoints().forEach { (point, _) ->
      val inner = point + Offset(3f, 3f)
      // The single-surface uniforms describe the same shape at the same size.
      val value = probe(opticalKey(continuous), inner, "shapeSd(probe) - $SD", field = field)
      assertThat(abs(value), "merged sd at $inner").isLessThan(1e-3f)
    }
  }

  @Test
  fun edgeRefractionGradient_followsContinuousNormal() {
    val key = opticalKey(continuous)
    boundaryPoints().forEach { (point, normal) ->
      val x = probe(key, point, "edgeRefractionGradient(probe, materialSize, cornerRadii, cornerExponents).x", scale = 4f)
      val y = probe(key, point, "edgeRefractionGradient(probe, materialSize, cornerRadii, cornerExponents).y", scale = 4f)
      val dot = (x * normal.x + y * normal.y) / hypot(x, y)
      assertThat(dot, "normal at $point").isGreaterThan(0.99f)
    }
  }

  private val continuous = ContinuousRoundedCornerShape(RADIUS.dp)

  /** Boundary points and outward normals from the Kotlin geometry, in the top-left corner. */
  private fun boundaryPoints(): List<Pair<Offset, Offset>> =
    (1..16).map { i -> topLeftBoundaryPoint(PI + PI * 0.5 * i / 17) }

  private fun topLeftBoundaryPoint(angle: Double): Pair<Offset, Offset> {
    val radii = continuous.resolveCornerRadiiPx(MATERIAL_SIZE, Density(1f), LayoutDirection.Ltr)
    assertThat(radii.topLeftSmoothing).isEqualTo(1f)
    val extent = continuousCornerExtent(radii.topLeft, radii.topLeftSmoothing)
    val exponent = continuousCornerExponent(radii.topLeftSmoothing)
    val delta = continuousCornerPoint(angle, extent, exponent)
    val normal = Offset(
      sign(delta.x) * (abs(delta.x) / extent).pow(exponent - 1f),
      sign(delta.y) * (abs(delta.y) / extent).pow(exponent - 1f),
    )
    return Offset(extent, extent) + delta to normal / normal.getDistance()
  }

  private fun opticalKey(shape: CornerBasedShape, size: Size = MATERIAL_SIZE): GlassOpticalEffectKey {
    val effect = GlassRuntimeEffect().apply {
      style = when (shape) {
        is ContinuousRoundedCornerShape -> GlassStyle { shape(shape) }
        is RoundedCornerShape -> GlassStyle { shape(shape) }
        else -> error("Unsupported $shape")
      }
    }
    val resolved = resolveGlassStyle(effect, size, Density(1f), LayoutDirection.Ltr)
    return buildGlassRenderParams(resolved, GlassCoordinates(size, Offset.Zero, size, 1f)).opticalEffectKey()
  }

  /** `value - expected`, encoded at a resolution that resolves differences well below 1e-3. */
  private fun difference(
    key: GlassOpticalEffectKey,
    point: Offset,
    value: String,
    expected: Float,
  ): Float = probe(key, point, "($value) - ($expected)", scale = 0.01f)

  /** Encodes [value] as `0.5 + value / scale` in the red channel and decodes it. */
  private fun probe(
    key: GlassOpticalEffectKey,
    point: Offset,
    value: String,
    scale: Float = 0.01f,
    field: GlassMergedField? = null,
  ): Float {
    val continuous = key.cornerRadii.isContinuous || field?.isContinuous == true
    val source = GlassShaders.buildOptical(merged = field != null, continuous = continuous)
      .substringBefore("vec4 main(vec2 coord)") + """
      uniform float2 probe;
      vec4 main(vec2 coord) {
        return vec4(0.5 + ($value) / $scale, 0.0, 0.0, 1.0);
      }
    """.trimIndent()
    val filter = createRuntimeShaderRenderEffect(RuntimeEffect.makeForShader(source), arrayOf("content"), arrayOf(null)) {
      setOpticalUniforms(key)
      field?.let { setMergedFieldUniforms(it, includeTint = true, includeOptics = true) }
      setFloatUniform("probe", point.x, point.y)
    }
    val image = ImageBitmap(1, 1)
    with(Canvas(image)) {
      val bounds = Rect(0f, 0f, 1f, 1f)
      saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = filter })
      drawRect(bounds, Paint().apply { color = Color.Black })
      restore()
    }
    return (image.toPixelMap()[0, 0].red - 0.5f) * scale
  }

  private companion object {
    // Large enough for a clear gap between the circular and continuous curves at full smoothing.
    const val RADIUS = 100f
    const val SD = "sdRoundedRect(probe, materialSize, cornerRadii, cornerExponents)"
    const val CIRCULAR_SD = "sdRoundedRect(probe, materialSize, cornerRadii)"
    val MATERIAL_SIZE = Size(400f, 400f)
  }
}
