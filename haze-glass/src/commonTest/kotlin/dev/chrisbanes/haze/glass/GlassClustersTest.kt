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
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceMetadata
import dev.chrisbanes.haze.HazeSourceSelection
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
    for ((gap, count) in listOf(19f to 1, 20f to 1, 21f to 1, 39f to 1, 40f to 2, 41f to 2)) {
      assertThat(clusterGlassMembers(listOf(member(0), member(1, 100f + gap)), 40f).surfaces.size)
        .isEqualTo(count)
    }
    assertThat(clusterGlassMembers(listOf(member(0), member(1, 112f, 52f)), 40f).surfaces.size)
      .isEqualTo(1)
    assertThat(clusterGlassMembers(listOf(member(0), member(1, 128f, 68f)), 40f).surfaces.size)
      .isEqualTo(1)
    assertThat(clusterGlassMembers(listOf(member(0), member(1, 129f, 69f)), 40f).surfaces.size)
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
  fun cluster_linkedOrNearMembersMergeOnlyWithEqualKeys() {
    val first = member(0, 0f).copy(identity = "first")
    // 300 px away, well beyond the 40 px merge distance: only the link can merge it.
    val linked = member(1, 400f).copy(identity = "linked", link = first.identity)
    assertThat(clusterGlassMembers(listOf(first, linked), 40f).surfaces.map { it.members.size })
      .containsExactly(2)

    val differentKey = linked.copy(key = key.copy(alpha = 0.4f))
    assertThat(clusterGlassMembers(listOf(first, differentKey), 40f).surfaces.map { it.members.size })
      .containsExactly(1, 1)

    // 10 px apart, within merge distance: proximity alone does not override a different key.
    val near = member(2, 110f).copy(key = key.copy(alpha = 0.4f))
    assertThat(clusterGlassMembers(listOf(first, near), 40f).surfaces.map { it.members.size })
      .containsExactly(1, 1)
  }

  @Test
  fun cluster_membersSharingRefinedSelectionMerge() {
    val state = HazeState()
    val shared: (HazeSourceMetadata) -> Boolean = { it.key != "x" }
    // Same body as `shared`, but a different instance: predicates compare by identity.
    val separate: (HazeSourceMetadata) -> Boolean = { it.key != "x" }

    fun keyFor(selection: HazeSourceSelection) = glassMergeKey(
      HazeInput.Sources(state, selection),
      true,
      0.5f,
      effect,
      material,
      LayoutDirection.Ltr,
    )

    val a = member(0).copy(key = keyFor(HazeSourceSelection.Behind.where(shared)))
    val b = member(1).copy(key = keyFor(HazeSourceSelection.Behind.where(shared)))
    assertThat(clusterGlassMembers(listOf(a, b), 40f).surfaces.map { it.members.size })
      .containsExactly(2)

    val c = member(1).copy(key = keyFor(HazeSourceSelection.Behind.where(separate)))
    assertThat(clusterGlassMembers(listOf(a, c), 40f).surfaces.map { it.members.size })
      .containsExactly(1, 1)
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

  @Test
  fun translatedTo_uniformTranslationMatchesClustering() {
    // Over the cap, with a group, an invalid member and an intervening key.
    val members = List(6) { member(it) } + grouped(6, 700f) + grouped(7, 900f) +
      member(8, Rect.Zero) + member(9, 1000f).copy(key = key.copy(alpha = 0.4f))
    val model = clusterGlassMembers(members, 40f)
    val moved = members.map {
      it.copy(rect = it.rect.translate(-3f, 57f), layoutRect = it.layoutRect.translate(-3f, 57f), sampleLayoutRect = it.sampleLayoutRect.translate(-3f, 57f))
    }
    val opticalSize = Size(12f, 12f)

    assertThat(model.translatedTo(members, moved, opticalSize)).isEqualTo(clusterGlassMembers(moved, 40f, opticalSize))
  }

  @Test
  fun translatedTo_otherChangesNeedClustering() {
    val members = List(3) { member(it) }
    val model = clusterGlassMembers(members, 40f)
    fun GlassMemberSnapshot.moved(dx: Float) =
      copy(rect = rect.translate(dx, 0f), layoutRect = layoutRect.translate(dx, 0f), sampleLayoutRect = sampleLayoutRect.translate(dx, 0f))
    fun moved(dx: Float) = members.map { it.moved(dx) }

    // The control: a uniform move alone reuses the partition, so each case below fails on its change.
    assertThat(model.translatedTo(members, moved(10f), null)).isNotNull()
    // One member moved further than the others, so gaps changed.
    assertThat(model.translatedTo(members, moved(10f).mapIndexed { i, it -> if (i == 2) it.moved(1f) else it }, null)).isNull()
    // The interaction rect moved without the layout rect.
    assertThat(model.translatedTo(members, members.map { it.copy(rect = it.rect.translate(5f, 0f)) }, null)).isNull()
    assertThat(model.translatedTo(members, moved(10f).reversed(), null)).isNull()
    assertThat(model.translatedTo(members, moved(10f).drop(1), null)).isNull()
    assertThat(model.translatedTo(members, moved(10f).map { it.copy(identity = it.identity.toString()) }, null)).isNull()
    assertThat(model.translatedTo(members, moved(10f).mapIndexed { i, it -> if (i == 1) it.copy(fading = true) else it }, null)).isNull()
    assertThat(model.translatedTo(emptyList(), emptyList(), null)).isNull()
  }

  @Test
  fun translatedTo_fractionalTranslationNeedsClustering() {
    fun GlassMemberSnapshot.moved(dx: Float) =
      copy(rect = rect.translate(dx, 0f), layoutRect = layoutRect.translate(dx, 0f), sampleLayoutRect = sampleLayoutRect.translate(dx, 0f))
    // A gap of exactly the merge distance stays separate; moved by -0.1 it rounds to about 39.999992.
    val members = listOf(member(0, Rect(0f, 0f, 100f, 100f)), member(1, Rect(140f, 0f, 240f, 100f)))
    val moved = members.map { it.moved(-0.1f) }
    assertThat(clusterGlassMembers(members, 40f).surfaces.size).isEqualTo(2)
    assertThat(clusterGlassMembers(moved, 40f).surfaces.size).isEqualTo(1)

    assertThat(clusterGlassMembers(members, 40f).translatedTo(members, moved, null)).isNull()
    // Fractional rects stay ineligible even when moved by whole pixels.
    assertThat(clusterGlassMembers(moved, 40f).translatedTo(moved, moved.map { it.moved(1f) }, null)).isNull()
  }

  private fun grouped(index: Int, left: Float, groupKey: Any? = "group") = member(index, left).copy(groupKey = groupKey)

  private fun GlassClusterModel.units() = surfaces.map { surface -> surface.groups.map { unit -> unit.map { it.identity } } }

  @Test
  fun cluster_ungroupedMembersFormSingletonUnits() {
    val result = clusterGlassMembers(List(3) { member(it) }, 40f)
    assertThat(result.units()).containsExactly(listOf(listOf(0), listOf(1), listOf(2)))
  }

  @Test
  fun cluster_groupedMembersBeyondMergeDistanceFormOneUnit() {
    // 200 px apart, well beyond the 40 px merge distance: only the shared group key joins them.
    val result = clusterGlassMembers(listOf(grouped(0, 0f), grouped(1, 300f)), 40f)
    assertThat(result.units()).containsExactly(listOf(listOf(0, 1)))
    assertThat(result.surfaces.single().members.map { it.identity }).containsExactly(0, 1)
  }

  @Test
  fun cluster_groupMemberWithDifferentKeyIsLeftOut() {
    val other = grouped(1, 110f).copy(key = key.copy(alpha = 0.4f))
    val result = clusterGlassMembers(listOf(grouped(0, 0f), other, grouped(2, 220f)), 40f)
    assertThat(result.units()).containsExactly(listOf(listOf(0)), listOf(listOf(1)), listOf(listOf(2)))
  }

  @Test
  fun cluster_nonConsecutiveGroupMembersSplitIntoRuns() {
    val far = member(1, 600f)
    val result = clusterGlassMembers(listOf(grouped(0, 0f), far, grouped(2, 300f)), 40f)
    assertThat(result.units()).containsExactly(listOf(listOf(0)), listOf(listOf(1)), listOf(listOf(2)))
  }

  @Test
  fun cluster_groupBridgesWithNeighbourByUnitBounds() {
    // The neighbour sits under the gap between the members: 20 px from the group's bounds, but
    // about 54 px from either member alone.
    val neighbour = member(2, 150f, 60f)
    assertThat(clusterGlassMembers(listOf(member(0, 0f), member(1, 300f), neighbour), 40f).surfaces.size)
      .isEqualTo(3)
    assertThat(clusterGlassMembers(listOf(grouped(0, 0f), grouped(1, 300f), neighbour), 40f).units())
      .containsExactly(listOf(listOf(0, 1), listOf(2)))
  }

  @Test
  fun cluster_groupCountsAsOneTowardCap() {
    // A 3-member group plus 3 near members is 4 units: one cluster, no overflow.
    val group = List(3) { grouped(it, it * 110f) }
    val near = List(3) { member(3 + it, 330f + it * 110f) }
    val fits = clusterGlassMembers(group + near, 40f)
    assertThat(fits.units()).containsExactly(listOf(listOf(0, 1, 2), listOf(3), listOf(4), listOf(5)))
    assertThat(fits.overflow).isEmpty()

    // A fifth unit overflows and stays a singleton cluster.
    val overflowing = clusterGlassMembers(group + near + member(6, 660f), 40f)
    assertThat(overflowing.units())
      .containsExactly(listOf(listOf(0, 1, 2), listOf(3), listOf(4), listOf(5)), listOf(listOf(6)))
    assertThat(overflowing.overflow.single().excessCount).isEqualTo(1)
  }

  @Test
  fun cluster_fadingOrNonTranslationMemberLeavesGroup() {
    for (excluded in listOf(grouped(1, 110f).copy(fading = true), grouped(1, 110f).copy(translationOnly = false))) {
      val result = clusterGlassMembers(listOf(grouped(0, 0f), excluded, grouped(2, 220f)), 40f)
      assertThat(result.surfaces.flatMap { it.groups }.map { unit -> unit.map { it.identity } })
        .containsExactly(listOf(0), listOf(1), listOf(2))
    }
  }
}
