// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import kotlin.test.Test

class GlassClustersTest {
  private val effect = GlassRuntimeEffect()
  private val material = resolveGlassStyle(effect, Size(100f, 40f), Density(1f), LayoutDirection.Ltr)
  private val key = glassMergeKey(HazeInput.Content, true, 0.5f, effect, material, LayoutDirection.Ltr)

  private fun member(index: Int, left: Float = index * 110f, top: Float = 0f) = member(index, Rect(left, top, left + 100f, top + 40f))

  private fun member(index: Int, rect: Rect) = GlassMemberSnapshot(index, rect, key, material, translationOnly = true, layoutRect = rect)

  @Test
  fun cluster_strictThresholdAndDiagonalGap() {
    for ((gap, count) in listOf(19f to 1, 20f to 2, 21f to 2)) {
      assertThat(clusterGlassMembers(listOf(member(0), member(1, 100f + gap)), 40f).surfaces.size)
        .isEqualTo(count)
    }
    assertThat(clusterGlassMembers(listOf(member(0), member(1, 112f, 52f)), 40f).surfaces.size)
      .isEqualTo(1)
    assertThat(clusterGlassMembers(listOf(member(0), member(1, 115f, 55f)), 40f).surfaces.size)
      .isEqualTo(2)
    assertThat(clusterGlassMembers(listOf(member(0), member(1, 30f)), 40f).surfaces.size).isEqualTo(1)
    assertThat(clusterGlassMembers(listOf(member(0), member(1, 30f)), 0f).surfaces.size).isEqualTo(2)
  }

  @Test
  fun cluster_nonTranslationTransformKeepsMemberSeparate() {
    val rotated = member(1, 100f).copy(translationOnly = false)
    val result = clusterGlassMembers(listOf(member(0), rotated, member(2, 200f)), 40f)
    assertThat(result.surfaces.map { surface -> surface.members.map { it.identity } })
      .containsExactly(listOf(0), listOf(1), listOf(2))
  }

  @Test
  fun cluster_interveningSurfaceSplitsClusterToPreserveOrder() {
    val other = member(1, 0f, 100f).copy(key = key.copy(alpha = 0.4f))
    val result = clusterGlassMembers(listOf(member(0, 0f), other, member(2, 100f)), 40f)
    assertThat(result.surfaces.map { surface -> surface.members.map { it.identity } })
      .containsExactly(listOf(0), listOf(1), listOf(2))
    val capped = listOf(member(0), member(1), member(2), other.copy(identity = 3), member(4, 330f), member(5, 440f))
    assertThat(clusterGlassMembers(capped, 40f).surfaces.map { surface -> surface.members.map { it.identity } })
      .containsExactly(listOf(0, 1, 2), listOf(3), listOf(4), listOf(5))
  }

  @Test
  fun cluster_chainUsesConnectedComponents() {
    val result = clusterGlassMembers(List(3) { member(it) }, 40f)
    assertThat(result.surfaces.single().members.map { it.identity }).containsExactly(0, 1, 2)
  }

  @Test
  fun cluster_eachMaterialKeyFieldSeparatesMembers() {
    val alternatives = listOf(
      key.copy(input = HazeInput.Sources(HazeState())),
      key.copy(expandLayerBounds = false),
      key.copy(qualityFraction = 1f),
      key.copy(optics = key.optics.copy(depth = OpticalSizeValue.Fixed(0.3f))),
      key.copy(resolvedOptics = key.resolvedOptics.copy(depth = 0.3f)),
      key.copy(surfaceProfile = 2f),
      key.copy(specularIntensity = 0.123f),
      key.copy(specularExponent = 3f),
      key.copy(edgeShadow = Color.Red),
      key.copy(edgeSoftnessPx = 12f),
      key.copy(ambientResponse = 0.123f),
      key.copy(chromaticAberrationStrength = 0.123f),
      key.copy(chromaticAberrationMode = 2f),
      key.copy(contrast = 0.123f),
      key.copy(whitePoint = 0.123f),
      key.copy(chromaMultiplier = 0.123f),
      key.copy(contentNormalBlend = 0.123f),
      key.copy(fresnelExponent = key.fresnelExponent + 1f),
      key.copy(lightAlignment = Alignment.BottomEnd),
      key.copy(layoutDirection = LayoutDirection.Rtl),
      key.copy(alpha = 0.4f),
    )
    alternatives.forEachIndexed { index, alternative ->
      assertThat(
        clusterGlassMembers(listOf(member(0), member(1).copy(key = alternative)), 40f).surfaces.size,
        "key field $index",
      ).isEqualTo(2)
    }
  }

  @Test
  fun cluster_coloursRadiiAndResolvedLightPositionDoNotSeparate() {
    val changed = material.copy(
      tint = Color.Red,
      backgroundColor = Color.Blue,
      cornerRadii = CornerRadii(4f, 5f, 6f, 7f),
      lightPosition = androidx.compose.ui.geometry.Offset(200f, 300f),
    )
    val otherKey = glassMergeKey(HazeInput.Content, true, 0.5f, effect, changed, LayoutDirection.Ltr)
    assertThat(
      clusterGlassMembers(listOf(member(0), member(1).copy(key = otherKey, material = changed)), 40f)
        .surfaces.size,
    ).isEqualTo(1)
    val clear = GlassRuntimeEffect().apply { style = GlassStyle.clear }
    val clearMaterial = resolveGlassStyle(clear, Size(100f, 40f), Density(1f), LayoutDirection.Ltr)
    val clearKey = glassMergeKey(HazeInput.Content, true, 0.5f, clear, clearMaterial, LayoutDirection.Ltr)
    assertThat(clusterGlassMembers(listOf(member(0), member(1).copy(key = clearKey)), 40f).surfaces.size)
      .isEqualTo(2)
  }

  @Test
  fun cluster_firstFourAndSingletonOverflowPreserveRegistrationOrder() {
    for (count in 5..6) {
      val members = List(count) { member(it) }
      val result = clusterGlassMembers(members, 40f)
      assertThat(result.surfaces.map { it.members.size }).isEqualTo(listOf(4) + List(count - 4) { 1 })
      assertThat(result.surfaces.first().members.map { it.identity }).containsExactly(0, 1, 2, 3)
      assertThat(result.overflow.single().excessCount).isEqualTo(count - 4)
      val moved = members.map { it.copy(rect = it.rect.translate(10f, 10f)) }
      assertThat(clusterGlassMembers(moved, 40f).surfaces.first().members.map { it.identity })
        .containsExactly(0, 1, 2, 3)
      val reattached = members.drop(1) + members.first()
      assertThat(clusterGlassMembers(reattached, 40f).surfaces.first().members.map { it.identity })
        .containsExactly(1, 2, 3, 4)
    }
  }

  @Test
  fun cluster_mixedKeysShareOpticalSizeAndInvalidMembersAreDeferred() {
    val card = member(0, Rect(0f, 0f, 300f, 180f))
    val pill = member(1, Rect(310f, 0f, 450f, 64f)).copy(key = key.copy(alpha = 0.5f))
    assertThat(clusterGlassMembers(listOf(card, pill), 40f).opticalSize).isEqualTo(Size(180f, 180f))
    assertThat(clusterGlassMembers(listOf(pill), 40f).opticalSize).isEqualTo(Size(64f, 64f))
    val invalid = listOf(Rect.Zero, Rect(Float.NaN, 0f, 10f, 10f), Rect(10f, 0f, 0f, 10f))
      .map { member(0, it) }
    val result = clusterGlassMembers(invalid, 40f)
    assertThat(result.surfaces).isEmpty()
    assertThat(result.opticalSize).isNull()
    assertThat(clusterGlassMembers(emptyList(), 40f).opticalSize).isNull()
  }
}
