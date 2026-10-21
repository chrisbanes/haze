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
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

/** Runs the merged field helpers in Skia and encodes their values into a readable pixel. */
class MergedGlassFieldShaderTest {
  @Test
  fun approachingMembers_deformContinuouslyBeforeTheBridgeForms() {
    val effect = GlassRuntimeEffect()
    val material = resolveGlassStyle(effect, Size(100f, 40f), Density(1f), LayoutDirection.Ltr)
    val key = glassMergeKey(HazeInput.Content, true, 1f, effect, material, LayoutDirection.Ltr)
    fun edgeDistance(gap: Float): Float {
      val members = listOf(mergedMember(0f, 0f, 100f, 40f, radius = 20f), mergedMember(100f + gap, 0f, 100f, 40f, radius = 20f))
      val snapshots = members.mapIndexed { index, member ->
        GlassMemberSnapshot(index, member.rect, key, material, translationOnly = true, layoutRect = member.rect)
      }
      val cluster = clusterGlassMembers(snapshots, 40f).surfaces.first()
      val field = mergedField(*cluster.members.map { members[it.identity as Int] }.toTypedArray(), spacingPx = 40f)
      return probe(field, Offset(100f, 20f), "shapeSd(probe)", scale = 16f)
    }
    for (threshold in listOf(40f, 20f)) {
      val before = edgeDistance(threshold + 0.1f)
      val after = edgeDistance(threshold - 0.1f)
      assertThat(abs(after - before), "edge movement across a $threshold px gap").isLessThan(0.2f)
    }
    assertThat(edgeDistance(30f), "surfaces start stretching before the 20 px bridge threshold").isLessThan(-0.5f)
  }

  @Test
  fun bridge_formsBelowHalfSpacingOnly() {
    for ((gap, bridged) in listOf(19f to true, 20f to false, 21f to false)) {
      val field = mergedField(
        mergedMember(0f, 0f, 100f, 40f),
        mergedMember(100f + gap, 0f, 100f, 40f),
        spacingPx = 40f,
      )
      val inside = probeColor(
        field,
        Offset(100f + gap / 2f, 20f),
        "shapeSd(probe) < 0.0 ? vec4(0.0, 1.0, 0.0, 1.0) : vec4(1.0, 0.0, 0.0, 1.0)",
      )
      assertThat(inside, "midpoint inside the field for a $gap px gap").isEqualTo(if (bridged) Color.Green else Color.Red)
    }
  }

  @Test
  fun fullOverlap_endsAtUnion() {
    val member = mergedMember(20f, 10f, 100f, 40f, radius = 12f)
    val field = mergedField(member, member, spacingPx = 40f)
    for (point in listOf(Offset(20f, 30f), Offset(70f, 10f), Offset(70f, 30f), Offset(130f, 60f), Offset(0f, 0f))) {
      val difference = probe(field, point, "shapeSd(probe) - memberSd(probe, memberRects[0], memberRadii[0])")
      assertThat(abs(difference), "difference from the single member at $point").isLessThan(1e-3f)
    }
  }

  @Test
  fun thinBridge_fadesRefraction() {
    // Every probe is about 0.5 px inside the field, below the 14 px refraction width.
    val edge = refractionAt(capsulesWithNeck(2f).first, Offset(50f, 0.5f)).getDistance()
    assertThat(edge, "straight-edge refraction").isGreaterThan(4f)

    val (thinNeck, thinGap) = capsulesWithNeck(2f)
    val midpoint = refractionAt(thinNeck, Offset(100f + thinGap / 2f, 19.5f)).getDistance()
    assertThat(midpoint, "refraction at a 2 px neck").isLessThan(edge * 0.25f)

    // Off the saddle the field gradient is strong, so only the thickness weight fades refraction.
    val (neck, gap) = capsulesWithNeck(6f)
    val shoulder = refractionAt(neck, Offset(100f + gap / 2f - 4f, 16f)).getDistance()
    assertThat(shoulder, "refraction on a 6 px neck's shoulder").isLessThan(edge * 0.25f)
  }

  /**
   * Two 100x40 capsules whose bridge midpoint is [thickness] px thick. The field there is
   * `d - k / 4`, so the neck's half thickness h satisfies `(r + gap / 2)^2 + h^2 = (r + k / 4)^2`.
   */
  private fun capsulesWithNeck(thickness: Float): Pair<GlassMergedField, Float> {
    val radius = 20f
    val spacing = 40f
    val half = thickness / 2f
    val gap = 2f * (sqrt((radius + spacing / 4f) * (radius + spacing / 4f) - half * half) - radius)
    val field = mergedField(
      mergedMember(0f, 0f, 100f, 40f, radius = radius),
      mergedMember(100f + gap, 0f, 100f, 40f, radius = radius),
      spacingPx = spacing,
    )
    return field to gap
  }

  @Test
  fun refractionCap_usesLocalMemberSize() {
    // A 300x160 panel caps displacement at 80 px and a 40x40 control at 20 px. The 47.6 px
    // displacement at the panel's top edge must not be clamped to the control's cap.
    val panel = mergedMember(0f, 0f, 300f, 160f)
    val control = mergedMember(320f, 10f, 40f, 40f)
    val edge = Offset(100f, 0.5f)
    val merged = refractionAt(mergedField(panel, control), edge).getDistance()
    val alone = refractionAt(mergedField(panel), edge).getDistance()
    assertThat(abs(merged - alone), "panel edge refraction merged vs alone").isLessThan(1f)
    assertThat(merged, "panel edge refraction").isGreaterThan(20f * 1.25f)
  }

  @Test
  fun bridgeColour_blendsByShare() {
    val field = mergedField(
      mergedMember(0f, 0f, 100f, 40f, tint = Color.Red),
      mergedMember(110f, 0f, 100f, 40f, tint = Color.Blue),
      spacingPx = 40f,
    )
    val blend = "blendMemberColors(memberWeights(probe), memberTint0, memberTint1, memberTint2, memberTint3)"
    assertColor(probeColor(field, Offset(105f, 20f), blend), Color(0.5f, 0f, 0.5f))
    assertColor(probeColor(field, Offset(50f, 20f), blend), Color.Red)
    assertColor(probeColor(field, Offset(160f, 20f), blend), Color.Blue)
  }

  @Test
  fun members_oneToFour() {
    // Far apart, so each member's interior field is its own signed distance.
    val all = mergedField(
      mergedMember(0f, 0f, 100f, 40f),
      mergedMember(200f, 0f, 100f, 40f),
      mergedMember(0f, 100f, 100f, 40f),
      mergedMember(200f, 100f, 100f, 40f),
      spacingPx = 40f,
    )
    val centers = all.members.map { it.rect.center }
    for (count in 1..4) {
      centers.forEachIndexed { index, center ->
        // Every slot holds a member, so only the count can hide the later ones.
        val value = fieldAt(all, center, count = count)
        if (index < count) {
          assertThat(abs(value + 20f), "member $index of $count").isLessThan(1.5f)
        } else {
          assertThat(value, "slot $index beyond $count members").isGreaterThan(0f)
        }
      }
    }
  }

  @Test
  fun mergedLighting_staysOnLitMember() {
    val field = mergedField(
      mergedMember(0f, 0f, 100f, 40f, lightPosition = Offset(50f, 20f), lightRadius = 40f, lightIntensity = 1f),
      mergedMember(110f, 0f, 100f, 40f),
    )
    assertThat(lightAt(field, Offset(50f, 20f))).isGreaterThan(0.5f)
    assertThat(lightAt(field, Offset(160f, 20f))).isEqualTo(0f)
  }

  @Test
  fun mergedLighting_twoLightsLightTwoMembers() {
    val field = mergedField(
      mergedMember(0f, 0f, 100f, 40f, lightPosition = Offset(50f, 20f), lightRadius = 40f, lightIntensity = 1f),
      mergedMember(110f, 0f, 100f, 40f, lightPosition = Offset(160f, 20f), lightRadius = 40f, lightIntensity = 1f),
    )
    assertThat(lightAt(field, Offset(50f, 20f))).isGreaterThan(0.5f)
    assertThat(lightAt(field, Offset(160f, 20f))).isGreaterThan(0.5f)
  }

  @Test
  fun mergedSources_compile() {
    listOf(
      GlassShaders.buildFused(merged = true),
      GlassShaders.buildFused(sharpDetail = false, merged = true),
      GlassShaders.buildOptical(merged = true),
      GlassShaders.buildRefractionDetail(merged = true),
      GlassShaders.buildRefractionDetail(coverageOnly = true, merged = true),
      GlassShaders.buildRim(merged = true),
      GlassShaders.buildMergedBackground(),
      GlassShaders.buildInteractionLighting(merged = true),
    ).forEach { RuntimeEffect.makeForShader(it).close() }
  }

  private fun fieldAt(field: GlassMergedField, point: Offset, count: Int? = null): Float =
    probe(field, point, "shapeSd(probe)", scale = 256f, count = count)

  private fun refractionAt(field: GlassMergedField, point: Offset): Offset {
    val color = probeColor(
      field,
      point,
      """vec4(vec2(0.5) + refraction(probe) / 128.0, 0.0, 1.0)""",
    )
    return Offset((color.red - 0.5f) * 128f, (color.green - 0.5f) * 128f)
  }

  /** Encodes [value] as `0.5 + value / scale` in the red channel and decodes it. */
  private fun probe(
    field: GlassMergedField,
    point: Offset,
    value: String,
    scale: Float = 0.01f,
    count: Int? = null,
  ): Float {
    val color = probeColor(field, point, "vec4(0.5 + ($value) / $scale, 0.0, 0.0, 1.0)", count)
    return (color.red - 0.5f) * scale
  }

  private fun probeColor(field: GlassMergedField, point: Offset, output: String, count: Int? = null): Color {
    val effect = GlassRuntimeEffect().apply {
      style = GlassStyle {
        optics(
          GlassOptics(
            refractionStrength = 0.85f,
            refractionDisplacement = 56.dp,
            refractionHeightFraction = 0.35f,
            refractionProfile = RefractionProfile.Edge(14.dp),
          ),
        )
      }
    }
    val materialSize = Size(400f, 200f)
    val resolved = resolveGlassStyle(effect, materialSize, Density(1f), LayoutDirection.Ltr)
    val params = buildGlassRenderParams(resolved, GlassCoordinates(materialSize, Offset.Zero, materialSize, 1f))
    val filter = createRuntimeShaderRenderEffect(shader(output), arrayOf("content"), arrayOf(null)) {
      setOpticalUniforms(params.opticalEffectKey())
      setMergedFieldUniforms(field, includeTint = true)
      count?.let { setFloatUniform("memberCount", it.toFloat()) }
      setFloatUniform("probe", point.x, point.y)
    }
    val image = ImageBitmap(1, 1)
    with(Canvas(image)) {
      val bounds = Rect(0f, 0f, 1f, 1f)
      saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = filter })
      drawRect(bounds, Paint().apply { color = Color.Black })
      restore()
    }
    return image.toPixelMap()[0, 0]
  }

  /** Evaluates the merged lighting stage's per-member light at [point], encoded in red. */
  private fun lightAt(field: GlassMergedField, point: Offset): Float {
    val prefix = GlassShaders.buildInteractionLighting(merged = true).substringBefore("vec4 main(vec2 coord)")
    val shader = createRuntimeEffect(prefix + "\nuniform float2 probe;\nvec4 main(vec2 coord) { return vec4(memberLight(probe), 0.0, 0.0, 1.0); }")
    val filter = createRuntimeShaderRenderEffect(shader, arrayOf("content"), arrayOf(null)) {
      setMergedFieldUniforms(field, includeLighting = true)
      setFloatUniform("probe", point.x, point.y)
    }
    val image = ImageBitmap(1, 1)
    with(Canvas(image)) {
      val bounds = Rect(0f, 0f, 1f, 1f)
      saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = filter })
      drawRect(bounds, Paint().apply { color = Color.Black })
      restore()
    }
    return image.toPixelMap()[0, 0].red
  }

  private fun assertColor(actual: Color, expected: Color) {
    assertThat(abs(actual.red - expected.red), "red: $actual").isLessThan(2f / 255f)
    assertThat(abs(actual.green - expected.green), "green: $actual").isLessThan(2f / 255f)
    assertThat(abs(actual.blue - expected.blue), "blue: $actual").isLessThan(2f / 255f)
  }

  private companion object {
    // Retain every production helper and uniform. Only the output stage exposes the probed value.
    val prefix = GlassShaders.buildOptical(merged = true).substringBefore("vec4 main(vec2 coord)")

    fun shader(output: String) = createRuntimeEffect(
      prefix + """
        uniform float2 probe;

        vec2 refraction(vec2 p) {
          float sd = shapeSd(p);
          float distance = opticalDistanceFromSignedDistance(p, sd, 0.0);
          float height = surfaceHeightNormFromOpticalDistance(distance);
          vec2 sampleDisplacement;
          return refractionDisplacement(p, height, max(-sd, 0.0), 1.0, 0.0, sampleDisplacement);
        }

        vec4 main(vec2 coord) {
          return $output;
        }
      """.trimIndent(),
    )
  }
}
