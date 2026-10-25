// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.prop
import dev.chrisbanes.haze.HazeInput
import kotlin.test.Test

class GlassContainerTransitionsTest {
  @Test
  fun glassTransitionSpec_reduced_isCriticallyDampedSpring() {
    assertThat(glassTransitionSpec(spring(dampingRatio = 0.3f, stiffness = 200f), reduced = true)).isInstanceOf<SpringSpec<Float>>().given {
      assertThat(it.dampingRatio).isEqualTo(1f)
      assertThat(it.stiffness).isEqualTo(200f)
    }
    assertThat(glassTransitionSpec(tween(300), reduced = true)).isInstanceOf<SpringSpec<Float>>().given {
      assertThat(it.dampingRatio).isEqualTo(1f)
      assertThat(it.stiffness).isEqualTo(Spring.StiffnessMedium)
    }
    val spec = tween<Float>(300)
    assertThat(glassTransitionSpec(spec, reduced = false)).isSameInstanceAs(spec)
  }

  @Test
  fun glassTransitionSpec_reduced_neverOvershoots() {
    val bouncy = spring<Float>(dampingRatio = 0.3f, stiffness = 200f)
    assertThat(peak(glassTransitionSpec(bouncy, reduced = true))).isLessThanOrEqualTo(1f)
    // Sensitivity: the authored spring does overshoot.
    assertThat(peak(glassTransitionSpec(bouncy, reduced = false))).isGreaterThan(1f)
  }

  @Test
  fun lerpGlassMergedMember_endpointsAndMidpoint() {
    val from = GlassMergedMember(Rect(0f, 0f, 100f, 80f), CornerRadii(12f, 12f, 12f, 12f), Color.Red, Color.Transparent)
    val to = GlassMergedMember(Rect(110f, 0f, 210f, 80f), CornerRadii(4f, 4f, 4f, 4f), Color.Blue, Color.Transparent)
    assertThat(lerpGlassMergedMember(from, to, 0f)).isEqualTo(from)
    assertThat(lerpGlassMergedMember(from, to, 1f)).isEqualTo(to)
    val half = lerpGlassMergedMember(from, to, 0.5f)
    assertThat(half.rect).isEqualTo(Rect(55f, 0f, 155f, 80f))
    assertThat(half.cornerRadii).isEqualTo(CornerRadii(8f, 8f, 8f, 8f))
    assertThat(half.tint).isEqualTo(lerp(Color.Red, Color.Blue, 0.5f))
  }

  @Test
  fun lerpGlassMergedMember_overshoot_keepsPositiveSize() {
    val from = GlassMergedMember(Rect(0f, 0f, 10f, 10f), CornerRadii.zero, Color.Red, Color.Green)
    val to = GlassMergedMember(Rect(100f, 0f, 101f, 10f), CornerRadii.zero, Color.Blue, Color.White)
    val overshoot = lerpGlassMergedMember(from, to, 1.5f)
    assertThat(overshoot).prop(GlassMergedMember::rect).prop(Rect::width).isGreaterThanOrEqualTo(1f)
    assertThat(overshoot.tint).isEqualTo(Color.Blue)
    assertThat(overshoot.backgroundColor).isEqualTo(Color.White)
  }

  @Test
  fun morphedFrom_atStart_isNeighboursDrawnOutline() {
    val effect = GlassRuntimeEffect()
    val material = resolveGlassStyle(effect, Size(100f, 80f), Density(1f), LayoutDirection.Ltr).copy(cornerRadii = CornerRadii(20f, 20f, 20f, 20f))
    val key = glassMergeKey(HazeInput.Content, true, 0.5f, effect, material, LayoutDirection.Ltr)
    // A neighbour pressed to 0.94 about its centre.
    val neighbour = GlassMemberSnapshot(0, Rect(43f, 62.4f, 137f, 137.6f), key, material, translationOnly = true, layoutRect = Rect(40f, 60f, 140f, 140f))
    val member = GlassMemberSnapshot(1, Rect(150f, 60f, 250f, 140f), key, material, translationOnly = true, layoutRect = Rect(150f, 60f, 250f, 140f))
    val (rect, morphed) = member.morphedFrom(neighbour, 0f)
    assertThat(rect).isEqualTo(Rect(43f, 62.4f, 137f, 137.6f))
    assertThat(morphed.cornerRadii.topLeft).isCloseTo(18.8f, 0.001f)
  }

  @Test
  fun resolveGlassNeighbour_appearingGroupMember_doesNotStretchGroupBounds() {
    val effect = GlassRuntimeEffect()
    val material = resolveGlassStyle(effect, Size(100f, 80f), Density(1f), LayoutDirection.Ltr)
    val key = glassMergeKey(HazeInput.Content, true, 0.5f, effect, material, LayoutDirection.Ltr)
    fun node(groupKey: Any?) = GlassMemberNode(GlassMemberBinding(HazeInput.Content, true), GlassMemberTransition.MatchedGeometry, groupKey)
    fun snapshot(identity: Any, left: Float, groupKey: Any?) = Rect(left, 60f, left + 100f, 140f).let {
      GlassMemberSnapshot(identity, it, key, material, translationOnly = true, layoutRect = it, groupKey = groupKey)
    }
    val a = snapshot(node("group"), 40f, "group")
    // B joins A's group 200px away, and C appears 20px past B in the same frame, 320px from A.
    val b = snapshot(node("group"), 340f, "group")
    val c = snapshot(node(null), 460f, null)
    val ordered = listOf(a, b, c)

    // B is still growing out of A, so the group does not span B yet: C has nothing within 40px.
    assertThat(resolveGlassNeighbour(c, ordered, 40f, excluded = setOf(b.identity, c.identity))).isNull()
    // Once B has appeared, C is 20px from the group and from B itself.
    assertThat(resolveGlassNeighbour(c, ordered, 40f, excluded = setOf(c.identity))).isNotNull()
  }

  private fun peak(spec: FiniteAnimationSpec<Float>): Float {
    val animation = TargetBasedAnimation(spec, Float.VectorConverter, 0f, 1f)
    return (0L..animation.durationNanos step 16_000_000L).maxOf { animation.getValueFromNanos(it) }
  }
}
