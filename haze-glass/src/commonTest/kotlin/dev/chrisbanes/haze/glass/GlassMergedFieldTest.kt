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
)

internal fun mergedField(
  vararg members: GlassMergedMember,
  spacingPx: Float = 40f,
  opticalSize: Size = members.first().rect.size,
) = GlassMergedField(members.toList(), spacingPx, opticalSize)

class GlassMergedFieldTest {
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
    assertFailure { mergedField(member, spacingPx = Float.NaN) }.isInstanceOf<IllegalArgumentException>()
    assertFailure { mergedField(member, spacingPx = -1f) }.isInstanceOf<IllegalArgumentException>()
    assertFailure { mergedField(member, opticalSize = Size(Float.POSITIVE_INFINITY, 1f)) }
      .isInstanceOf<IllegalArgumentException>()
    assertFailure { mergedMember(Float.NaN, 0f, 10f, 10f) }.isInstanceOf<IllegalArgumentException>()
    assertFailure { mergedMember(0f, 0f, 10f, 10f, radius = Float.POSITIVE_INFINITY) }
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
  fun memberSmoothingPx_disjointMembersUseSpacing() {
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
  fun scaled_halvesGeometry() {
    val field = mergedField(
      mergedMember(10f, 20f, 100f, 40f, radius = 8f, tint = Color.Red),
      spacingPx = 40f,
      opticalSize = Size(100f, 40f),
    ).scaled(0.5f)
    assertThat(field).isEqualTo(
      mergedField(
        mergedMember(5f, 10f, 50f, 20f, radius = 4f, tint = Color.Red),
        spacingPx = 20f,
        opticalSize = Size(50f, 20f),
      ),
    )
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
