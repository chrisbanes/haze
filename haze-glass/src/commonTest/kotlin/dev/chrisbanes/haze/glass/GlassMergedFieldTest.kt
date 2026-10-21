// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import kotlin.test.Test

internal fun mergedMember(
  left: Float,
  top: Float,
  width: Float,
  height: Float,
  radius: Float = 0f,
  tint: Color = Color.Transparent,
  backgroundColor: Color = Color.Transparent,
  lightPosition: Offset = Offset.Zero,
  lightRadius: Float = 0f,
  lightIntensity: Float = 0f,
  opticalPosition: Offset = Offset.Zero,
  opticalRadius: Float = 0f,
  refractionMultiplier: Float = 1f,
  whitePointDelta: Float = 0f,
  layoutRect: Rect? = null,
) = GlassMergedMember(
  rect = Rect(left, top, left + width, top + height),
  cornerRadii = CornerRadii(radius, radius, radius, radius),
  tint = tint,
  backgroundColor = backgroundColor,
  lightPosition = lightPosition,
  lightRadius = lightRadius,
  lightIntensity = lightIntensity,
  opticalPosition = opticalPosition,
  opticalRadius = opticalRadius,
  refractionMultiplier = refractionMultiplier,
  whitePointDelta = whitePointDelta,
  layoutRect = layoutRect ?: Rect(left, top, left + width, top + height),
)

internal fun mergedField(
  vararg members: GlassMergedMember,
  mergeDistancePx: Float = 40f,
  opticalSize: Size = members.first().rect.size,
) = GlassMergedField(members.toList(), mergeDistancePx, opticalSize)

class GlassMergedFieldTest {
  @Test
  fun fallbackBase_survivesMaterialProjectionsButIsAbsentFromFullRenderer() {
    val member = GlassMergedMember(
      Rect(0f, 0f, 100f, 80f), CornerRadii.zero,
      Color.Transparent, Color.Transparent, lightPosition = Offset(20f, 20f), lightRadius = 30f,
      lightIntensity = 1f, opticalPosition = Offset(30f, 30f), opticalRadius = 40f,
      refractionMultiplier = 1.5f, fallbackBaseColor = Color.Green,
    )
    val field = mergedField(member)
    for (projection in listOf(field.scaled(0.5f), field.withoutLights, field.withoutOptics, field.withoutInteractions)) {
      assertThat(projection.members.single().fallbackBaseColor).isEqualTo(Color.Green)
    }
    val resolved = resolveGlassStyle(GlassRuntimeEffect(), Size(100f, 80f), Density(1f), LayoutDirection.Ltr)
    val coordinates = GlassCoordinates(Size(100f, 80f), Offset.Zero, Size(100f, 80f), 1f)
    val withBase = buildGlassRenderParams(resolved.copy(mergedField = field), coordinates)
    val withoutBase = buildGlassRenderParams(resolved.copy(mergedField = field.withoutFallbackBase), coordinates)
    assertThat(withBase).isEqualTo(withoutBase)
    assertThat(withBase.mergedField?.members?.single()?.fallbackBaseColor).isNull()
    assertThat(withBase.mergedField?.members?.single()?.refractionMultiplier).isEqualTo(1.5f)
  }

  @Test
  fun init_rejectsZeroOrFiveMembers() {
    assertFailure { GlassMergedField(emptyList(), 40f, Size(10f, 10f)) }
      .isInstanceOf<IllegalArgumentException>()
    val five = List(5) { mergedMember(it * 20f, 0f, 10f, 10f) }
    assertFailure { GlassMergedField(five, 40f, Size(10f, 10f)) }
      .isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun init_rejectsNonFiniteValues() {
    val member = mergedMember(0f, 0f, 10f, 10f)
    assertFailure { mergedField(member, mergeDistancePx = Float.NaN) }.isInstanceOf<IllegalArgumentException>()
    assertFailure { mergedField(member, mergeDistancePx = -1f) }.isInstanceOf<IllegalArgumentException>()
    assertFailure { mergedField(member, opticalSize = Size(Float.POSITIVE_INFINITY, 1f)) }
      .isInstanceOf<IllegalArgumentException>()
    assertFailure { mergedMember(Float.NaN, 0f, 10f, 10f) }.isInstanceOf<IllegalArgumentException>()
    assertFailure { mergedMember(0f, 0f, 10f, 10f, radius = Float.POSITIVE_INFINITY) }
      .isInstanceOf<IllegalArgumentException>()
    assertFailure { mergedMember(0f, 0f, 10f, 10f, layoutRect = Rect(0f, 0f, Float.NaN, 10f)) }
      .isInstanceOf<IllegalArgumentException>()
    assertFailure { mergedMember(0f, 0f, 10f, 10f, layoutRect = Rect(0f, 0f, 0f, 10f)) }
      .isInstanceOf<IllegalArgumentException>()
    assertFailure { mergedMember(0f, 0f, 10f, 10f, opticalPosition = Offset(Float.NaN, 0f)) }
      .isInstanceOf<IllegalArgumentException>()
    assertFailure { mergedMember(0f, 0f, 10f, 10f, opticalRadius = Float.POSITIVE_INFINITY) }
      .isInstanceOf<IllegalArgumentException>()
    assertFailure { mergedMember(0f, 0f, 10f, 10f, refractionMultiplier = 2.1f) }
      .isInstanceOf<IllegalArgumentException>()
    assertFailure { mergedMember(0f, 0f, 10f, 10f, whitePointDelta = -1.1f) }
      .isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun init_rejectsZeroSizeMembers() {
    assertFailure { mergedMember(0f, 0f, 0f, 10f) }.isInstanceOf<IllegalArgumentException>()
    assertFailure { mergedMember(0f, 0f, 10f, 0f) }.isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun memberSmoothingPx_disjointMembersUseMergeDistance() {
    val field = mergedField(
      mergedMember(0f, 0f, 100f, 40f),
      mergedMember(110f, 0f, 100f, 40f),
    )
    assertThat(field.memberSmoothingPx().toList()).containsExactly(40f, 40f)
  }

  @Test
  fun memberSmoothingPx_fullOverlapIsZero() {
    val field = mergedField(
      mergedMember(0f, 0f, 100f, 40f),
      mergedMember(10f, 5f, 40f, 30f),
    )
    assertThat(field.memberSmoothingPx()[1]).isEqualTo(0f)
  }

  @Test
  fun memberSmoothingPx_partialOverlapIsBetween() {
    val field = mergedField(
      mergedMember(0f, 0f, 100f, 40f),
      mergedMember(90f, 0f, 100f, 40f),
    )
    val smoothing = field.memberSmoothingPx()[1]
    assertThat(smoothing).isGreaterThan(0f)
    assertThat(smoothing).isLessThan(40f)
  }

  @Test
  fun memberSmoothingPx_overlappingPairKeepsUnrelatedMemberBridge() {
    val field = mergedField(
      mergedMember(0f, 0f, 100f, 40f),
      mergedMember(0f, 0f, 100f, 40f),
      mergedMember(110f, 0f, 100f, 40f),
    )
    assertThat(field.memberSmoothingPx().toList()).containsExactly(40f, 0f, 40f)
  }

  @Test
  fun memberStandaloneWeights_smoothsFromHalfMergeDistanceToFullMergeDistance() {
    val belowHalf = mergedField(
      mergedMember(0f, 0f, 10f, 10f),
      mergedMember(29f, 0f, 10f, 10f),
    ).memberStandaloneWeights()
    val atHalf = mergedField(
      mergedMember(0f, 0f, 10f, 10f),
      mergedMember(30f, 0f, 10f, 10f),
    ).memberStandaloneWeights()
    val halfway = mergedField(
      mergedMember(0f, 0f, 10f, 10f),
      mergedMember(40f, 0f, 10f, 10f),
    ).memberStandaloneWeights()
    val atFull = mergedField(
      mergedMember(0f, 0f, 10f, 10f),
      mergedMember(50f, 0f, 10f, 10f),
    ).memberStandaloneWeights()

    assertThat(belowHalf.toList()).containsExactly(0f, 0f)
    assertThat(atHalf.toList()).containsExactly(0f, 0f)
    assertThat(halfway.toList()).containsExactly(0.5f, 0.5f)
    assertThat(atFull.toList()).containsExactly(1f, 1f)
    assertThat(mergedField(mergedMember(0f, 0f, 10f, 10f)).memberStandaloneWeights().toList())
      .containsExactly(1f)
    assertThat(
      mergedField(
        mergedMember(0f, 0f, 10f, 10f),
        mergedMember(20f, 0f, 10f, 10f),
        mergeDistancePx = 0f,
      ).memberStandaloneWeights().toList(),
    ).containsExactly(0f, 0f)
  }

  @Test
  fun memberStandaloneWeights_usesNearestGapWithoutClusterGlobalInfluence() {
    val pair = mergedField(
      mergedMember(0f, 0f, 10f, 10f),
      mergedMember(30f, 0f, 10f, 10f),
    ).memberStandaloneWeights()
    val withDistantThird = mergedField(
      mergedMember(0f, 0f, 10f, 10f),
      mergedMember(30f, 0f, 10f, 10f),
      mergedMember(1000f, 1000f, 10f, 10f),
    ).memberStandaloneWeights()
    assertThat(pair.toList()).containsExactly(0f, 0f)
    assertThat(withDistantThird.take(2).toList()).containsExactly(0f, 0f)
    assertThat(withDistantThird.last()).isEqualTo(1f)
  }

  @Test
  fun scaled_halvesGeometry() {
    val field = mergedField(
      mergedMember(10f, 20f, 100f, 40f, radius = 8f, tint = Color.Red),
      mergeDistancePx = 40f,
      opticalSize = Size(100f, 40f),
    ).scaled(0.5f)
    assertThat(field).isEqualTo(
      mergedField(
        mergedMember(5f, 10f, 50f, 20f, radius = 4f, tint = Color.Red),
        mergeDistancePx = 20f,
        opticalSize = Size(50f, 20f),
      ),
    )
  }

  @Test
  fun scaled_scalesPreInteractionLayoutRect() {
    val member = mergedField(
      mergedMember(10f, 20f, 50f, 20f, layoutRect = Rect(-10f, 4f, 90f, 44f)),
    ).scaled(0.5f).members.single()
    assertThat(member.layoutRect).isEqualTo(Rect(-5f, 2f, 45f, 22f))
  }

  @Test
  fun isBackgroundOpaque_requiresEveryMemberOpaque() {
    val opaque = mergedMember(0f, 0f, 10f, 10f, backgroundColor = Color.White)
    val translucent = mergedMember(20f, 0f, 10f, 10f, backgroundColor = Color.White.copy(alpha = 0.5f))
    assertThat(mergedField(opaque, opaque).isBackgroundOpaque).isTrue()
    assertThat(mergedField(opaque, translucent).isBackgroundOpaque).isFalse()
  }

  @Test
  fun setMergedFieldUniforms_padsUnusedSlots() {
    val recorded = mutableMapOf<String, List<Float>>()
    val provider = recordingUniforms(recorded)
    provider.setMergedFieldUniforms(
      mergedField(mergedMember(1f, 2f, 3f, 4f, radius = 5f, tint = Color.Red)),
      includeTint = true,
      includeBackground = true,
    )
    assertThat(recorded.getValue("memberRects")).isEqualTo(listOf(1f, 2f, 3f, 4f) + List(12) { 0f })
    assertThat(recorded.getValue("memberRadii")).isEqualTo(List(4) { 5f } + List(12) { 0f })
    assertThat(recorded.getValue("memberSmoothing")).isEqualTo(listOf(40f, 0f, 0f, 0f))
    assertThat(recorded.getValue("memberCount")).containsExactly(1f)
    assertThat(recorded.getValue("memberTint0")).containsExactly(1f, 0f, 0f, 1f)
    assertThat(recorded.getValue("memberTint3")).containsExactly(0f, 0f, 0f, 0f)
    assertThat(recorded.getValue("memberBackground1")).containsExactly(0f, 0f, 0f, 0f)
  }

  @Test
  fun setMergedFieldUniforms_padsLightingArrays() {
    val recorded = mutableMapOf<String, List<Float>>()
    recordingUniforms(recorded).setMergedFieldUniforms(
      mergedField(
        mergedMember(0f, 0f, 10f, 10f, lightPosition = Offset(3f, 4f), lightRadius = 5f, lightIntensity = 1f),
        mergedMember(20f, 0f, 10f, 10f),
      ),
      includeLighting = true,
    )
    assertThat(recorded.getValue("memberLightPositions")).isEqualTo(listOf(3f, 4f) + List(6) { 0f })
    assertThat(recorded.getValue("memberLightRadii")).isEqualTo(listOf(5f, 0f, 0f, 0f))
    assertThat(recorded.getValue("memberLightIntensities")).isEqualTo(listOf(1f, 0f, 0f, 0f))
  }

  @Test
  fun setMergedFieldUniforms_padsOpticalArraysWithNeutralResponses() {
    val recorded = mutableMapOf<String, List<Float>>()
    recordingUniforms(recorded).setMergedFieldUniforms(
      mergedField(
        mergedMember(
          0f,
          0f,
          10f,
          10f,
          opticalPosition = Offset(3f, 4f),
          opticalRadius = 5f,
          refractionMultiplier = 1.8f,
          whitePointDelta = 0.3f,
        ),
      ),
      includeOptics = true,
    )
    assertThat(recorded.getValue("memberOpticalPositions")).isEqualTo(listOf(3f, 4f) + List(6) { 0f })
    assertThat(recorded.getValue("memberOpticalRadii")).isEqualTo(listOf(5f, 0f, 0f, 0f))
    assertThat(recorded.getValue("memberRefractionMultipliers")).isEqualTo(listOf(1.8f, 1f, 1f, 1f))
    assertThat(recorded.getValue("memberWhitePointDeltas")).isEqualTo(listOf(0.3f, 0f, 0f, 0f))
    assertThat(recorded.getValue("memberStandaloneWeights")).isEqualTo(listOf(1f, 0f, 0f, 0f))
  }

  @Test
  fun scaled_scalesLightPositionAndRadius() {
    val member = mergedField(
      mergedMember(0f, 0f, 10f, 10f, lightPosition = Offset(4f, 6f), lightRadius = 8f, lightIntensity = 0.5f),
    ).scaled(0.5f).members.single()
    assertThat(member.lightPosition).isEqualTo(Offset(2f, 3f))
    assertThat(member.lightRadius).isEqualTo(4f)
    assertThat(member.lightIntensity).isEqualTo(0.5f)
  }

  @Test
  fun scaled_scalesOpticalPositionAndRadius_butKeepsResponsesDimensionless() {
    val member = mergedField(
      mergedMember(
        0f,
        0f,
        10f,
        10f,
        opticalPosition = Offset(4f, 6f),
        opticalRadius = 8f,
        refractionMultiplier = 1.8f,
        whitePointDelta = 0.3f,
      ),
    ).scaled(0.5f).members.single()
    assertThat(member.opticalPosition).isEqualTo(Offset(2f, 3f))
    assertThat(member.opticalRadius).isEqualTo(4f)
    assertThat(member.refractionMultiplier).isEqualTo(1.8f)
    assertThat(member.whitePointDelta).isEqualTo(0.3f)
  }

  @Test
  fun withoutInteractions_stripsNeutralOpticalPositionAndRadius() {
    val active = mergedField(
      mergedMember(
        0f,
        0f,
        10f,
        10f,
        lightPosition = Offset(3f, 4f),
        lightRadius = 5f,
        lightIntensity = 1f,
        opticalPosition = Offset(8f, 9f),
        opticalRadius = 12f,
        refractionMultiplier = 1.8f,
        whitePointDelta = 0.3f,
      ),
    ).withoutLights.members.single()
    assertThat(active.lightIntensity).isEqualTo(0f)
    assertThat(active.opticalPosition).isEqualTo(Offset(8f, 9f))
    assertThat(active.opticalRadius).isEqualTo(12f)
    assertThat(active.refractionMultiplier).isEqualTo(1.8f)
    assertThat(active.whitePointDelta).isEqualTo(0.3f)

    val field = mergedField(
      mergedMember(0f, 0f, 10f, 10f, opticalPosition = Offset(8f, 9f), opticalRadius = 12f),
    )
    val projected = field.withoutInteractions
    assertThat(projected).isNotEqualTo(field)
    assertThat(projected.members.single().opticalPosition).isEqualTo(Offset.Zero)
    assertThat(projected.members.single().opticalRadius).isEqualTo(0f)
  }

  @Test
  fun projections_keepUnpressedLayoutRect() {
    val layoutRect = Rect(-25f, -10f, 75f, 30f)
    val field = mergedField(
      mergedMember(
        0f,
        0f,
        50f,
        20f,
        layoutRect = layoutRect,
        lightPosition = Offset(4f, 5f),
        lightRadius = 6f,
        lightIntensity = 0.7f,
        opticalPosition = Offset(20f, 10f),
        opticalRadius = 12f,
      ),
    )
    assertThat(field.withoutLights.members.single().layoutRect).isEqualTo(layoutRect)
    assertThat(field.withoutFallbackBase.members.single().layoutRect).isEqualTo(layoutRect)
    assertThat(field.withoutOptics.members.single().layoutRect).isEqualTo(layoutRect)
    assertThat(field.withoutInteractions.members.single().layoutRect).isEqualTo(layoutRect)

    val size = Size(100f, 40f)
    val style = resolveGlassStyle(GlassRuntimeEffect(), size, Density(1f), LayoutDirection.Ltr)
    val coordinates = GlassCoordinates(size, Offset.Zero, size, 1f)
    val unpressed = mergedField(mergedMember(0f, 0f, 50f, 20f))
    val pressed = mergedField(mergedMember(0f, 0f, 50f, 20f, layoutRect = layoutRect))
    val baseParams = buildGlassRenderParams(style.copy(mergedField = unpressed), coordinates)
    val pressedParams = buildGlassRenderParams(style.copy(mergedField = pressed), coordinates)
    assertThat(pressedParams.opticalEffectKey()).isNotEqualTo(baseParams.opticalEffectKey())
  }

  @Test
  fun setMergedFieldUniforms_uploadsLayoutRectsWithAndWithoutOptics() {
    val field = mergedField(mergedMember(10f, 20f, 40f, 16f, layoutRect = Rect(5f, 18f, 55f, 38f)))
    for (includeOptics in listOf(false, true)) {
      val recorded = mutableMapOf<String, List<Float>>()
      recordingUniforms(recorded).setMergedFieldUniforms(field, includeOptics = includeOptics)
      assertThat(recorded.getValue("memberLayoutRects"), "includeOptics=$includeOptics")
        .isEqualTo(listOf(5f, 18f, 50f, 20f) + List(12) { 0f })
    }
    val recorded = mutableMapOf<String, List<Float>>()
    recordingUniforms(recorded).setMergedFieldUniforms(field.scaled(0.5f))
    assertThat(recorded.getValue("memberLayoutRects")).isEqualTo(listOf(2.5f, 9f, 25f, 10f) + List(12) { 0f })
  }

  @Test
  fun withoutOptics_preservesLightingAndGeometry() {
    val field = mergedField(
      mergedMember(
        1f,
        2f,
        10f,
        12f,
        radius = 3f,
        lightPosition = Offset(4f, 5f),
        lightRadius = 6f,
        lightIntensity = 0.7f,
        opticalPosition = Offset(7f, 8f),
        opticalRadius = 9f,
        refractionMultiplier = 1.6f,
        whitePointDelta = 0.2f,
      ),
    )

    val projected = field.withoutOptics
    val member = projected.members.single()

    assertThat(projected).isNotEqualTo(field)
    assertThat(member.rect).isEqualTo(field.members.single().rect)
    assertThat(member.cornerRadii).isEqualTo(field.members.single().cornerRadii)
    assertThat(member.lightPosition).isEqualTo(Offset(4f, 5f))
    assertThat(member.lightRadius).isEqualTo(6f)
    assertThat(member.lightIntensity).isEqualTo(0.7f)
    assertThat(member.opticalPosition).isEqualTo(Offset.Zero)
    assertThat(member.opticalRadius).isEqualTo(0f)
    assertThat(member.refractionMultiplier).isEqualTo(1f)
    assertThat(member.whitePointDelta).isEqualTo(0f)
  }

  @Test
  fun buildGlassPreparedRender_lightOnlyChangeKeepsStageKeys() {
    val size = Size(240f, 80f)
    val style = resolveGlassStyle(GlassRuntimeEffect(), size, Density(1f), LayoutDirection.Ltr)
    val coordinates = GlassCoordinates(size, Offset.Zero, size, 1f)
    fun prepare(
      intensity: Float,
      lightPosition: Offset,
      lightRadius: Float,
      previous: GlassPreparedRender? = null,
    ): GlassPreparedRender {
      val field = mergedField(
        mergedMember(
          20f,
          20f,
          100f,
          40f,
          backgroundColor = Color.White,
          lightPosition = lightPosition,
          lightRadius = lightRadius,
          lightIntensity = intensity,
        ),
        mergedMember(130f, 20f, 100f, 40f),
      )
      return buildGlassPreparedRender(
        params = buildGlassRenderParams(style.copy(mergedField = field), coordinates),
        interactionUniforms = GlassInteractionUniforms(Offset.Zero, 0f, 0f, 1f, 0f),
        alpha = 1f,
        outputSize = IntSize(240, 80),
        previous = previous,
      )
    }
    val dim = prepare(0.5f, lightPosition = Offset(70f, 40f), lightRadius = 20f)
    val bright = prepare(1f, lightPosition = Offset(94f, 31f), lightRadius = 52f, previous = dim)
    assertThat(bright.opticalKey).isSameInstanceAs(dim.opticalKey)
    assertThat(bright.refractionDetailKey).isSameInstanceAs(dim.refractionDetailKey)
    assertThat(bright.rimKey).isSameInstanceAs(dim.rimKey)
    assertThat(bright.params.mergedBackground).isEqualTo(dim.params.mergedBackground)
    assertThat(bright.params.mergedField).isNotEqualTo(dim.params.mergedField)
    assertThat(bright.params.mergedField?.members?.first()?.opticalPosition).isEqualTo(Offset.Zero)
    assertThat(bright.params.mergedField?.members?.first()?.opticalRadius).isEqualTo(0f)
  }

  @Test
  fun buildGlassPreparedRender_opticalOnlyChangeInvalidatesOpticsAndKeepsOtherStages() {
    val size = Size(240f, 80f)
    val effect = GlassRuntimeEffect().apply {
      style = GlassStyle {
        optics(
          GlassOptics(
            refractionStrength = 0.6f,
            refractionDisplacement = 40.dp,
            refractionHeightFraction = 0.5f,
            refractionDetailIntensity = 0.7f,
          ),
        )
      }
    }
    val style = resolveGlassStyle(effect, size, Density(1f), LayoutDirection.Ltr)
    val coordinates = GlassCoordinates(size, Offset.Zero, size, 1f)
    fun prepare(multiplier: Float, delta: Float, previous: GlassPreparedRender? = null): GlassPreparedRender {
      val field = mergedField(
        mergedMember(
          20f,
          20f,
          100f,
          40f,
          backgroundColor = Color.White,
          opticalPosition = Offset(70f, 40f),
          opticalRadius = 20f,
          refractionMultiplier = multiplier,
          whitePointDelta = delta,
        ),
        mergedMember(130f, 20f, 100f, 40f),
      )
      return buildGlassPreparedRender(
        params = buildGlassRenderParams(style.copy(mergedField = field), coordinates),
        interactionUniforms = GlassInteractionUniforms(Offset.Zero, 0f, 0f, 1f, 0f),
        alpha = 1f,
        outputSize = IntSize(240, 80),
        previous = previous,
      )
    }
    val idle = prepare(1f, 0f)
    val multiplierOnly = prepare(1.8f, 0f, idle)
    val whitePointOnly = prepare(1f, 0.3f, idle)
    val active = prepare(1.8f, 0.3f, idle)
    assertThat(multiplierOnly.opticalKey).isNotEqualTo(idle.opticalKey)
    assertThat(multiplierOnly.refractionDetailKey).isNotEqualTo(idle.refractionDetailKey)
    assertThat(whitePointOnly.opticalKey).isNotEqualTo(idle.opticalKey)
    assertThat(active.opticalKey).isNotEqualTo(idle.opticalKey)
    assertThat(active.refractionDetailKey).isNotEqualTo(idle.refractionDetailKey)
    assertThat(active.blurKey).isSameInstanceAs(idle.blurKey)
    assertThat(active.rimKey).isSameInstanceAs(idle.rimKey)
    assertThat(active.params.mergedBackground).isEqualTo(idle.params.mergedBackground)

    val lit = prepare(1f, 0f, idle).params.copy(
      mergedField = idle.params.mergedField?.let { field ->
        mergedField(
          field.members[0].let {
            mergedMember(
              it.rect.left,
              it.rect.top,
              it.rect.width,
              it.rect.height,
              backgroundColor = it.backgroundColor,
              lightPosition = Offset(70f, 40f),
              lightRadius = 20f,
              lightIntensity = 1f,
              opticalPosition = it.opticalPosition,
              opticalRadius = it.opticalRadius,
              refractionMultiplier = it.refractionMultiplier,
              whitePointDelta = it.whitePointDelta,
            )
          },
          field.members[1],
        )
      },
    )
    assertThat(lit.opticalEffectKey()).isEqualTo(idle.opticalKey)
  }
}

private fun recordingUniforms(recorded: MutableMap<String, List<Float>>) = object : RuntimeShaderUniformProvider {
  override fun setColorUniform(name: String, color: Color) {
    recorded[name] = listOf(color.red, color.green, color.blue, color.alpha)
  }

  override fun setFloatUniform(name: String, value: Float) {
    recorded[name] = listOf(value)
  }

  override fun setFloatUniform(name: String, value1: Float, value2: Float) = error(name)

  override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) =
    error(name)

  override fun setFloatUniform(name: String, values: FloatArray) {
    recorded[name] = values.toList()
  }

  override fun setIntUniform(name: String, value: Int) = error(name)

  override fun setChildShader(name: String, shader: Shader) = error(name)
}
