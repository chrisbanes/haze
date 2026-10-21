// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

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
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

class GlassContainerOpticalBoundaryTest {
  @Test
  fun approachingMembers_matchStandaloneRefractionAcrossProfilesAndSizes() {
    for (scenario in opticalScenarios) {
      val standalone = probe(gap = 40.1f, lighting = false, scenario = scenario)
      val merged = probe(gap = 39.9f, lighting = false, scenario = scenario)
      assertThat(
        standalone.getDistance(),
        "Standalone refraction must be visible for $scenario",
      ).isGreaterThan(0.25f)
      assertThat(
        (merged - standalone).getDistance(),
        "Refraction across the outer cluster boundary for $scenario",
      ).isLessThan(0.6f)
    }
  }

  @Test
  fun approachingMembers_matchStandaloneLightingAcrossProfilesAndSizes() {
    for (scenario in opticalScenarios) {
      val standalone = probe(gap = 40.1f, lighting = true, scenario = scenario)
      val merged = probe(gap = 39.9f, lighting = true, scenario = scenario)
      assertThat(
        standalone.getDistance(),
        "Standalone lighting must be visible for $scenario",
      ).isGreaterThan(0.01f)
      assertThat(
        (merged - standalone).getDistance(),
        "Lighting across the outer cluster boundary for $scenario",
      ).isLessThan(0.03f)
    }
  }

  @Test
  fun halfMergeDistanceBoundary_isContinuous() {
    for (lighting in listOf(false, true)) {
      val before = probe(gap = 19.9f, lighting = lighting)
      val after = probe(gap = 20.1f, lighting = lighting)
      assertThat((after - before).getDistance(), "Half merge distance boundary, lighting=$lighting")
        .isLessThan(if (lighting) 0.03f else 0.6f)
    }
  }

  @Test
  fun approachingThirdMember_doesNotChangeClosePairResponse() {
    for (lighting in listOf(false, true)) {
      val pair = probe(gap = 16f, lighting = lighting)
      val withDistantThird = probe(gap = 16f, lighting = lighting, thirdGapAfterSecond = 300f)
      val tolerance = if (lighting) 0.03f else 0.6f
      assertThat((withDistantThird - pair).getDistance(), "Adding a distant third member")
        .isLessThan(tolerance)
      for (thirdGap in listOf(30f, 20f)) {
        val withThird = probe(gap = 16f, lighting = lighting, thirdGapAfterSecond = thirdGap)
        assertThat((withThird - pair).getDistance(), "Third member approaching at gap=$thirdGap")
          .isLessThan(tolerance)
      }
    }
  }

  @Test
  fun threeMemberBridge_nearestOpticalProfileSwitchIsContinuous() {
    for (lighting in listOf(false, true)) {
      val before = probeThreeMemberBridge(49.99f, lighting)
      val after = probeThreeMemberBridge(50.01f, lighting)
      assertThat(before.signedDistance, "Three-member bridge remains inside the production shape")
        .isLessThan(-0.5f)
      assertThat(before.response.getDistance(), "Bridge response is non-zero, lighting=$lighting")
        .isGreaterThan(if (lighting) 0.01f else 0.25f)
      assertThat(
        (after.response - before.response).getDistance(),
        "Nearest-member optical switch through the three-member bridge, lighting=$lighting",
      ).isLessThan(if (lighting) 0.03f else 0.6f)
    }
  }

  @Test
  fun mergedInteractiveOpticalConsumers_compile() {
    listOf(
      GlassShaders.buildFused(interactionOptics = true, merged = true),
      GlassShaders.buildFused(interactionOptics = true, sharpDetail = false, merged = true),
      GlassShaders.buildOptical(interactive = true, merged = true),
      GlassShaders.buildRefractionDetail(interactive = true, merged = true),
      GlassShaders.buildRefractionDetail(interactive = true, coverageOnly = true, merged = true),
    ).forEach { RuntimeEffect.makeForShader(it).close() }
  }

  private fun probe(
    gap: Float,
    lighting: Boolean,
    scenario: OpticalScenario = OpticalScenario(),
    thirdGapAfterSecond: Float? = null,
  ): Offset {
    val merged = gap < 40f
    val layoutSizes = buildList {
      add(scenario.firstLayoutSize)
      add(scenario.secondLayoutSize)
      if (thirdGapAfterSecond != null) add(scenario.secondLayoutSize)
    }
    val rects = mutableListOf<Rect>()
    layoutSizes.forEachIndexed { index, layoutSize ->
      val rectSize = layoutSize * scenario.interactionScale
      val left = when (index) {
        0 -> 0f
        1 -> rects.last().right + gap
        else -> rects.last().right + checkNotNull(thirdGapAfterSecond)
      }
      rects += Rect(left, 0f, left + rectSize.width, rectSize.height)
    }
    val opticalSide = layoutSizes.maxOf { it.minDimension }
    val opticalSize = Size(opticalSide, opticalSide)
    val field = GlassMergedField(
      members = rects.mapIndexed { index, rect ->
        val radius = scenario.cornerRadius * scenario.interactionScale
        GlassMergedMember(
          rect = rect,
          cornerRadii = CornerRadii(radius, radius, radius, radius),
          tint = Color.Transparent,
          backgroundColor = Color.Transparent,
          layoutSize = layoutSizes[index],
        )
      },
      mergeDistancePx = 40f,
      opticalSize = opticalSize,
    ).scaled(scenario.inputScale)

    val targetRect = rects[scenario.memberIndex]
    val targetSize = targetRect.size
    val radius = scenario.cornerRadius * scenario.interactionScale
    val localProbe = if (scenario.probeRoundedCorner) {
      Offset(radius * 1.35f, radius * 1.35f)
    } else {
      Offset(targetSize.width * 0.25f, targetSize.height * 0.25f)
    }
    val standaloneProbe = Offset(
      localProbe.x / scenario.interactionScale,
      localProbe.y / scenario.interactionScale,
    )
    val probePoint = (if (merged) targetRect.topLeft + localProbe else standaloneProbe) * scenario.inputScale
    val bounds = rects.reduce { a, b ->
      Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
    }
    val renderSize = bounds.size
    val effect = GlassRuntimeEffect().apply {
      opticalSizeOverride = opticalSize
      style = GlassStyle {
        optics(
          GlassOptics(
            refractionStrength = 0.85f,
            refractionDisplacement = 56.dp,
            refractionHeightFraction = scenario.heightFraction,
            refractionProfile = scenario.refractionProfile,
          ),
        )
        surfaceProfile(scenario.surfaceProfile)
        shape(RoundedCornerShape((if (merged) radius else scenario.cornerRadius).dp))
      }
    }
    val materialSize = if (merged) renderSize else layoutSizes[scenario.memberIndex]
    val resolved = resolveGlassStyle(effect, materialSize, Density(1f), LayoutDirection.Ltr)
    val scaledSize = materialSize * scenario.inputScale
    val params = buildGlassRenderParams(
      resolved,
      GlassCoordinates(scaledSize, Offset.Zero, scaledSize, scenario.inputScale),
    )
    val shader = probeShader(merged, lighting)
    val filter = createRuntimeShaderRenderEffect(shader, arrayOf("content"), arrayOf(null)) {
      setOpticalUniforms(params.opticalEffectKey())
      if (merged) setMergedFieldUniforms(field, includeTint = true, includeOptics = true)
      setFloatUniform("probe", probePoint.x, probePoint.y)
    }
    val image = ImageBitmap(1, 1)
    with(Canvas(image)) {
      val pixelBounds = Rect(0f, 0f, 1f, 1f)
      saveLayer(pixelBounds, Paint().apply { skiaPaint.imageFilter = filter })
      drawRect(pixelBounds, Paint().apply { color = Color.Black })
      restore()
    }
    val color = image.toPixelMap()[0, 0]
    val encodingScale = if (lighting) 4f else 128f
    val scale = encodingScale * if (!merged && !lighting) scenario.interactionScale else 1f
    return Offset((color.red - 0.5f) * scale, (color.green - 0.5f) * scale)
  }

  private fun probeThreeMemberBridge(probeX: Float, lighting: Boolean): SeamProbeResult {
    val materialCenter = Offset(50f, 50f)
    val memberCenters = listOf(Offset(0f, -44f), Offset(38f, 22f), Offset(-38f, 22f))
    val field = GlassMergedField(
      members = memberCenters.map { center ->
        val translatedCenter = center + materialCenter
        GlassMergedMember(
          rect = Rect(
            translatedCenter.x - 5f,
            translatedCenter.y - 5f,
            translatedCenter.x + 5f,
            translatedCenter.y + 5f,
          ),
          cornerRadii = CornerRadii.zero,
          tint = Color.Transparent,
          backgroundColor = Color.Transparent,
          layoutSize = Size(10f, 10f),
        )
      },
      mergeDistancePx = 100f,
      opticalSize = Size(10f, 10f),
    )
    val effect = GlassRuntimeEffect().apply {
      opticalSizeOverride = field.opticalSize
      style = GlassStyle {
        optics(
          GlassOptics(
            refractionStrength = 0.7f,
            refractionDisplacement = 48.dp,
            refractionHeightFraction = 0.25f,
          ),
        )
        surfaceProfile(SurfaceProfile.Circle)
      }
    }
    val materialSize = Size(100f, 100f)
    val params = buildGlassRenderParams(
      resolveGlassStyle(effect, materialSize, Density(1f), LayoutDirection.Ltr),
      GlassCoordinates(materialSize, Offset.Zero, materialSize, 1f),
    )
    val shader = createThreeMemberBridgeProbeShader(lighting)
    val filter = createRuntimeShaderRenderEffect(shader, arrayOf("content"), arrayOf(null)) {
      setOpticalUniforms(params.opticalEffectKey())
      setMergedFieldUniforms(field, includeTint = true, includeOptics = true)
      setFloatUniform("probe", probeX, materialCenter.y)
    }
    val image = ImageBitmap(1, 1)
    with(Canvas(image)) {
      val pixelBounds = Rect(0f, 0f, 1f, 1f)
      saveLayer(pixelBounds, Paint().apply { skiaPaint.imageFilter = filter })
      drawRect(pixelBounds, Paint().apply { color = Color.Black })
      restore()
    }
    val color = image.toPixelMap()[0, 0]
    val encodingScale = if (lighting) 4f else 128f
    return SeamProbeResult(
      response = Offset((color.red - 0.5f) * encodingScale, (color.green - 0.5f) * encodingScale),
      signedDistance = (color.blue - 0.5f) * 128f,
    )
  }

  private fun createThreeMemberBridgeProbeShader(lighting: Boolean) = createRuntimeEffect(
    GlassShaders.buildOptical(merged = true).let { source ->
      val fieldWeight = Regex("float fieldWeight = ([^;]+);").find(source)!!.groupValues[1]
      val output = if (lighting) {
        "surfaceLightingGradient(probe, sd, fieldWeight)"
      } else {
        """
          refractionDisplacement(
            probe,
            height,
            edgeRefractionWidth >= 0.0 ? max(-sd, 0.0) : distance,
            1.0,
            fieldWeight
          )
        """.trimIndent()
      }
      val encodingScale = if (lighting) 4f else 128f
      source.substringBefore("vec4 main(vec2 coord)") + """
        uniform float2 probe;
        vec4 main(vec2 coord) {
          float sd = shapeSd(probe);
          float fieldWeight = $fieldWeight;
          float distance = opticalDistanceFromSignedDistance(probe, sd, fieldWeight);
          float height = surfaceHeightNormFromOpticalDistance(distance);
          vec2 result = $output;
          return vec4(vec2(0.5) + result / $encodingScale, 0.5 + sd / 128.0, 1.0);
        }
      """.trimIndent()
    },
  )

  private fun probeShader(merged: Boolean, lighting: Boolean) = when {
    merged && lighting -> mergedLightingProbeShader
    merged -> mergedRefractionProbeShader
    lighting -> standaloneLightingProbeShader
    else -> standaloneRefractionProbeShader
  }

  private data class OpticalScenario(
    val firstLayoutSize: Size = Size(120f, 40f),
    val secondLayoutSize: Size = firstLayoutSize,
    val memberIndex: Int = 0,
    val surfaceProfile: SurfaceProfile = SurfaceProfile.Squircle,
    val heightFraction: Float = 0.5f,
    val refractionProfile: RefractionProfile = RefractionProfile.Surface,
    val cornerRadius: Float = 8f,
    val interactionScale: Float = 1f,
    val inputScale: Float = 1f,
    val probeRoundedCorner: Boolean = false,
  )

  private data class SeamProbeResult(
    val response: Offset,
    val signedDistance: Float,
  )

  private companion object {
    val opticalScenarios = listOf(
      OpticalScenario(),
      OpticalScenario(
        firstLayoutSize = Size(64f, 60f),
        surfaceProfile = SurfaceProfile.Circle,
        heightFraction = 0.25f,
        probeRoundedCorner = true,
      ),
      OpticalScenario(
        firstLayoutSize = Size(96f, 60f),
        surfaceProfile = SurfaceProfile.Squircle,
        heightFraction = 0.4f,
      ),
      OpticalScenario(
        firstLayoutSize = Size(160f, 40f),
        surfaceProfile = SurfaceProfile.Concave,
        heightFraction = 0.5f,
      ),
      OpticalScenario(
        firstLayoutSize = Size(120f, 40f),
        secondLayoutSize = Size(160f, 64f),
        memberIndex = 0,
        surfaceProfile = SurfaceProfile.Concave,
        heightFraction = 0.4f,
      ),
      OpticalScenario(
        firstLayoutSize = Size(120f, 40f),
        secondLayoutSize = Size(160f, 64f),
        memberIndex = 1,
        surfaceProfile = SurfaceProfile.Lip,
        heightFraction = 0.4f,
        refractionProfile = RefractionProfile.Edge(28.dp),
      ),
      OpticalScenario(
        firstLayoutSize = Size(120f, 40f),
        secondLayoutSize = Size(160f, 64f),
        memberIndex = 0,
        surfaceProfile = SurfaceProfile.Circle,
        heightFraction = 0.5f,
        interactionScale = 0.8f,
        inputScale = 0.75f,
      ),
    )

    fun createProbeShader(merged: Boolean, lighting: Boolean) =
      createRuntimeEffect(
        GlassShaders.buildOptical(merged = merged).let { source ->
          val fieldWeight = Regex("float fieldWeight = ([^;]+);").find(source)!!.groupValues[1]
          val sd = if (merged) "shapeSd(probe)" else "sdRoundedRect(probe, materialSize, cornerRadii)"
          val output = if (lighting) {
            "surfaceLightingGradient(probe, sd, fieldWeight)"
          } else {
            """
              refractionDisplacement(
                probe,
                height,
                edgeRefractionWidth >= 0.0 ? max(-sd, 0.0) : distance,
                1.0,
                fieldWeight
              )
            """.trimIndent()
          }
          val encodingScale = if (lighting) 4f else 128f
          source.substringBefore("vec4 main(vec2 coord)") + """
          uniform float2 probe;
          vec4 main(vec2 coord) {
            float sd = $sd;
            float fieldWeight = $fieldWeight;
            float distance = opticalDistanceFromSignedDistance(probe, sd, fieldWeight);
            float height = surfaceHeightNormFromOpticalDistance(distance);
            vec2 result = $output;
            return vec4(vec2(0.5) + result / $encodingScale, 0.0, 1.0);
          }
          """.trimIndent()
        },
      )

    val standaloneRefractionProbeShader by lazy { createProbeShader(merged = false, lighting = false) }
    val standaloneLightingProbeShader by lazy { createProbeShader(merged = false, lighting = true) }
    val mergedRefractionProbeShader by lazy { createProbeShader(merged = true, lighting = false) }
    val mergedLightingProbeShader by lazy { createProbeShader(merged = true, lighting = true) }
  }
}
