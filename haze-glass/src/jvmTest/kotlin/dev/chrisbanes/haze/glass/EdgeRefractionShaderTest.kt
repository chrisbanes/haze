// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

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
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

/** Runs the production displacement helper in Skia, encoding its vector into a readable pixel. */
class EdgeRefractionShaderTest {
  @Test
  fun clearEdge_matchesMeasuredNativeFalloff() {
    // Native card's left edge, iOS 27.0 (24A434), four-phase measurement.
    val native = mapOf(1f to 40.24f, 2f to 32.99f, 4f to 22.82f, 8f to 11.22f, 12f to 5.11f, 20f to -0.09f)
    native.forEach { (depth, expected) ->
      val actual = displacement(Offset(depth, 88f))
      assertThat(abs(actual.x - expected), "native displacement at $depth dp").isLessThan(2.5f)
      assertThat(abs(actual.y)).isLessThan(0.4f)
    }
  }

  @Test
  fun clearCorner_matchesMeasuredNativeDirection() {
    // Angles measured at 2pt depth around a visible 28pt corner, from its left tangent to diagonal.
    for ((angle, expected) in listOf(0f to 19f, 15f to 27.9f, 30f to 36.2f, 45f to 44.2f)) {
      val radians = angle * kotlin.math.PI.toFloat() / 180f
      val point = Offset(28f - 26f * cos(radians), 28f - 26f * sin(radians))
      val actual = displacement(point)
      val direction = atan2(actual.y, actual.x) * 180f / kotlin.math.PI.toFloat()
      assertThat(abs(direction - expected), "native direction at $angle degrees").isLessThan(3f)
    }
  }

  @Test
  fun edgeControls_remainIndependentAndZeroIsNeutral() {
    val point = Offset(8f, 88f)
    val base = displacement(point)
    assertThat(abs(displacement(point, heightFraction = 0f).x - base.x)).isLessThan(0.4f)
    assertThat(abs(displacement(point, surface = SurfaceProfile.Concave).x - base.x)).isLessThan(0.4f)
    assertThat(displacement(point, width = 14f).x).isLessThan(base.x * 0.4f)
    assertThat(abs(displacement(point, width = 0f).x)).isLessThan(0.4f)
    assertThat(abs(displacement(point, strength = 0f).x)).isLessThan(0.4f)
    assertThat(abs(displacement(point, scale = 0f).x)).isLessThan(0.4f)
    assertThat(displacement(point, fold = 1f).x).isLessThan(base.x * 0.5f)
  }

  @Test
  fun edgeCoordinates_preserveDensityAndWorkingScale() {
    val point = Offset(8f, 15f)
    val expected = displacement(point)
    for (density in listOf(1f, 2f, 3f)) {
      for (inputScale in listOf(0.5f, 0.75f, 1f)) {
        val actual = displacement(point, density = density, inputScale = inputScale)
        assertThat((actual - expected).getDistance()).isLessThan(0.8f)
      }
    }
  }

  @Test
  fun capsule_capsDisplacementAtHalfHeightAndInteriorSettles() {
    val capsule = Size(240f, 64f)
    assertThat(displacement(Offset(0f, 32f), size = capsule, radius = 32f).x).isLessThan(32.4f)
    assertThat(abs(displacement(Offset(120f, 32f), size = capsule, radius = 32f).x)).isLessThan(0.4f)
    assertThat(abs(displacement(Offset(140f, 88f)).x)).isLessThan(0.4f)
  }

  @Test
  fun asymmetricCorners_andSquareCornersHaveContinuousTangents() {
    val radii = CornerRadii(0f, 8f, 22f, 30f)
    // Probe either side of each optical arc's join to a straight edge, including a zero radius.
    for (point in listOf(Offset(1f, 1f), Offset(268f, 2f), Offset(278f, 12f), Offset(2f, 131f))) {
      val before = displacement(point - Offset(0.05f, 0.05f), corners = radii)
      val after = displacement(point + Offset(0.05f, 0.05f), corners = radii)
      assertThat((after - before).getDistance(), "tangent at $point").isLessThan(2f)
    }
  }

  @Test
  fun wideEdgeBand_remainsContinuousAtOpticalArcCenters() {
    // Small corners leave the optical arc centre inside the active refraction band.
    for (radius in listOf(0.1f, 1f, 8f)) {
      val center = Offset(radius * 1.5f, radius * 1.5f)
      val atCenter = displacement(center, radius = radius)
      val before = displacement(center - Offset(0.05f, 0.05f), radius = radius)
      val after = displacement(center + Offset(0.05f, 0.05f), radius = radius)
      assertThat((before - atCenter).getDistance(), "before arc centre at radius $radius").isLessThan(1f)
      assertThat((after - atCenter).getDistance(), "after arc centre at radius $radius").isLessThan(1f)
    }
    // Joining only at the centre still leaves a jump on either axis close to that centre.
    for (point in listOf(Offset(12f, 11.5f), Offset(11.5f, 12f))) {
      val before = displacement(point - Offset(0.005f, 0.005f), radius = 8f)
      val after = displacement(point + Offset(0.005f, 0.005f), radius = 8f)
      assertThat((after - before).getDistance(), "near-centre corner axis at $point").isLessThan(0.8f)
    }
    val center = Offset(32f, 32f)
    val atCenter = displacement(center, size = Size(240f, 64f), radius = 32f, width = 112f)
    for (offset in listOf(Offset(-0.05f, -0.05f), Offset(0.05f, 0.05f))) {
      val adjacent = displacement(center + offset, size = Size(240f, 64f), radius = 32f, width = 112f)
      assertThat((adjacent - atCenter).getDistance(), "capsule arc centre").isLessThan(1f)
    }
  }

  private fun displacement(
    point: Offset,
    size: Size = Size(280f, 176f),
    radius: Float = 28f,
    corners: CornerRadii = CornerRadii(radius, radius, radius, radius),
    width: Float = 28f,
    strength: Float = 0.85f,
    scale: Float = 56f,
    fold: Float = 0f,
    heightFraction: Float = 0.35f,
    surface: SurfaceProfile = SurfaceProfile.Circle,
    density: Float = 1f,
    inputScale: Float = 1f,
  ): Offset {
    val effect = GlassRuntimeEffect().apply {
      style = GlassStyle {
        optics(
          GlassOptics(
            refractionStrength = strength,
            refractionDisplacement = scale.dp,
            refractionHeightFraction = heightFraction,
            refractionFoldStrength = fold,
            refractionProfile = RefractionProfile.Edge(width.dp),
          ),
        )
        surfaceProfile(surface)
      }
    }
    val resolved = resolveGlassStyle(effect, size * density, Density(density), LayoutDirection.Ltr)
    val factor = density * inputScale
    val params = buildGlassRenderParams(
      resolved,
      GlassCoordinates(size * factor, Offset.Zero, size * factor, inputScale),
    ).copy(cornerRadii = corners * factor)
    val filter = createRuntimeShaderRenderEffect(shader, arrayOf("content"), arrayOf(null)) {
      setOpticalUniforms(params.opticalEffectKey())
      setFloatUniform("probe", point.x * factor, point.y * factor)
      setFloatUniform("probeScale", factor)
    }
    val image = ImageBitmap(1, 1)
    with(Canvas(image)) {
      val bounds = Rect(0f, 0f, 1f, 1f)
      saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = filter })
      drawRect(bounds, Paint().apply { color = Color.Black })
      restore()
    }
    val color = image.toPixelMap()[0, 0]
    return Offset((color.red - 0.5f) * 128f, (color.green - 0.5f) * 128f)
  }

  private companion object {
    // Retain every production helper and uniform. Only the output stage exposes the sampling vector.
    val shader = RuntimeEffect.makeForShader(
      GlassShaders.buildOptical().substringBefore("vec4 main(vec2 coord)") + """
        uniform float2 probe;
        uniform float probeScale;
        vec4 main(vec2 coord) {
          float sd = sdRoundedRect(probe, materialSize, cornerRadii);
          float field = opticalFieldWeight();
          float distance = opticalDistanceFromSignedDistance(probe, sd, field);
          float height = surfaceHeightNormFromOpticalDistance(distance);
          vec2 value = refractionDisplacement(probe, height, max(-sd, 0.0), 1.0, field);
          return vec4(vec2(0.5) + value / (128.0 * probeScale), 0.0, 1.0);
        }
      """.trimIndent(),
    )
  }
}
