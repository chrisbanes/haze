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
) = GlassMergedMember(
  rect = Rect(left, top, left + width, top + height),
  cornerRadii = CornerRadii(radius, radius, radius, radius),
  tint = tint,
  backgroundColor = backgroundColor,
  lightPosition = lightPosition,
  lightRadius = lightRadius,
  lightIntensity = lightIntensity,
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
  fun scaled_scalesLightPositionAndRadius() {
    val member = mergedField(
      mergedMember(0f, 0f, 10f, 10f, lightPosition = Offset(4f, 6f), lightRadius = 8f, lightIntensity = 0.5f),
    ).scaled(0.5f).members.single()
    assertThat(member.lightPosition).isEqualTo(Offset(2f, 3f))
    assertThat(member.lightRadius).isEqualTo(4f)
    assertThat(member.lightIntensity).isEqualTo(0.5f)
  }

  @Test
  fun buildGlassPreparedRender_lightOnlyChangeKeepsStageKeys() {
    val size = Size(240f, 80f)
    val style = resolveGlassStyle(GlassRuntimeEffect(), size, Density(1f), LayoutDirection.Ltr)
    val coordinates = GlassCoordinates(size, Offset.Zero, size, 1f)
    fun prepare(intensity: Float, previous: GlassPreparedRender? = null): GlassPreparedRender {
      val field = mergedField(
        mergedMember(20f, 20f, 100f, 40f, backgroundColor = Color.White, lightPosition = Offset(70f, 40f), lightRadius = 20f, lightIntensity = intensity),
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
    val dim = prepare(0.5f)
    val bright = prepare(1f, previous = dim)
    assertThat(bright.opticalKey).isSameInstanceAs(dim.opticalKey)
    assertThat(bright.refractionDetailKey).isSameInstanceAs(dim.refractionDetailKey)
    assertThat(bright.rimKey).isSameInstanceAs(dim.rimKey)
    assertThat(bright.params.mergedBackground).isEqualTo(dim.params.mergedBackground)
    assertThat(bright.params.mergedField).isNotEqualTo(dim.params.mergedField)
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
