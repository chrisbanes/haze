// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

class GlassContainerMembershipTest : ContextTest() {
  @Composable
  private fun Member(effect: GlassRuntimeEffect, modifier: Modifier = Modifier.size(100.dp, 40.dp), style: GlassStyle = GlassStyle) {
    Box(modifier.hazeGlass(HazeEffectFactory { effect }, HazeInput.Content, style, null, true, null))
  }

  private fun container(effect: GlassRuntimeEffect) = checkNotNull(effect.memberBindingForTest?.node?.container)

  @Test
  fun spacing_rejectsInvalidValuesAndStoresTimingWithoutAnimation() {
    for (value in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY)) {
      assertFailure { Modifier.hazeGlassContainer(value.dp) }.isInstanceOf<IllegalArgumentException>()
    }
    val spec = tween<Float>(321)
    val element = Modifier.hazeGlassContainer(0.dp, spec, GlassReducedMotionPolicy.Reduced)
      .foldIn<GlassContainerElement?>(null) { _, item -> item as? GlassContainerElement }
    assertThat(checkNotNull(element).spacing).isEqualTo(0.dp)
    assertThat(element.transitionSpec).isSameInstanceAs(spec)
    assertThat(element.reducedMotionPolicy).isEqualTo(GlassReducedMotionPolicy.Reduced)
  }

  @Test
  fun membership_nearestNestedAndSiblingContainersStaySeparate() = runComposeUiTest {
    val outer = GlassRuntimeEffect()
    val inner = GlassRuntimeEffect()
    val sibling = GlassRuntimeEffect()
    setContent {
      Row {
        Box(Modifier.hazeGlassContainer(40.dp)) {
          Member(outer)
          Box(Modifier.hazeGlassContainer(40.dp)) { Member(inner) }
        }
        Box(Modifier.hazeGlassContainer(40.dp)) { Member(sibling) }
      }
    }
    waitForIdle()
    assertThat(container(outer)).isNotSameInstanceAs(container(inner))
    assertThat(container(outer)).isNotSameInstanceAs(container(sibling))
    for (effect in listOf(outer, inner, sibling)) {
      assertThat(container(effect).model.surfaces.single().members.single().identity)
        .isSameInstanceAs(effect.memberBindingForTest?.node)
    }
  }

  @Test
  fun membership_resizeRemoveAndAncestorReplacementRestoreOpticalSize() = runComposeUiTest {
    val card = GlassRuntimeEffect()
    val pill = GlassRuntimeEffect()
    val showCard = mutableStateOf(true)
    val width = mutableStateOf(180)
    val enabled = mutableStateOf(true)
    setContent {
      Row(if (enabled.value) Modifier.hazeGlassContainer(40.dp) else Modifier) {
        if (showCard.value) Member(card, Modifier.size(300.dp, width.value.dp))
        Member(pill, Modifier.size(140.dp, 64.dp))
      }
    }
    waitForIdle()
    assertThat(pill.opticalSizeOverride).isEqualTo(Size(180f, 180f))
    runOnIdle { width.value = 100 }
    waitForIdle()
    assertThat(pill.opticalSizeOverride).isEqualTo(Size(100f, 100f))
    runOnIdle { showCard.value = false }
    waitForIdle()
    assertThat(pill.opticalSizeOverride).isEqualTo(Size(64f, 64f))
    runOnIdle { enabled.value = false }
    waitForIdle()
    assertThat(pill.memberBindingForTest?.node?.container).isNull()
    assertThat(pill.opticalSizeOverride).isNull()
    runOnIdle { enabled.value = true }
    waitForIdle()
    assertThat(pill.opticalSizeOverride).isEqualTo(Size(64f, 64f))
  }

  @Test
  fun style_snapshotUpdatesReclusterWithoutRecompositionAndKeepMemberLocals() = runComposeUiTest {
    val first = GlassRuntimeEffect()
    val second = GlassRuntimeEffect()
    val alpha = mutableStateOf(1f)
    val radius = mutableStateOf(4)
    val style = GlassStyle {
      alpha(alpha.value)
      shape(RoundedCornerShape(radius.value.dp))
    }
    var compositions = 0
    setContent {
      SideEffect { compositions++ }
      Row(Modifier.hazeGlassContainer(40.dp)) {
        CompositionLocalProvider(LocalGlassStyle provides GlassStyle { tint(Color.Red) }) { Member(first, style = style) }
        CompositionLocalProvider(LocalGlassStyle provides GlassStyle { tint(Color.Blue) }) { Member(second) }
      }
    }
    waitForIdle()
    val before = compositions
    assertThat(container(first).model.surfaces.size).isEqualTo(1)
    assertThat(first.memberBindingForTest?.material?.tint).isEqualTo(Color.Red)
    assertThat(second.memberBindingForTest?.material?.tint).isEqualTo(Color.Blue)
    runOnIdle {
      alpha.value = 0.5f
      radius.value = 12
    }
    waitForIdle()
    assertThat(compositions).isEqualTo(before)
    assertThat(first.memberBindingForTest?.material?.alpha).isEqualTo(0.5f)
    assertThat(first.memberBindingForTest?.material?.cornerRadii).isEqualTo(CornerRadii(12f, 12f, 12f, 12f))
    assertThat(container(first).model.surfaces.size).isEqualTo(2)
  }

  @Test
  fun membership_rotatedOrScaledMembersStaySeparate() = runComposeUiTest {
    val first = GlassRuntimeEffect()
    val second = GlassRuntimeEffect()
    val rotation = mutableStateOf(0f)
    val scale = mutableStateOf(1f)
    setContent {
      Row(Modifier.hazeGlassContainer(40.dp)) {
        Member(first)
        Member(
          second,
          Modifier.graphicsLayer {
            rotationZ = rotation.value
            scaleX = scale.value
          }.size(100.dp, 40.dp),
        )
      }
    }
    waitForIdle()
    assertThat(container(first).model.surfaces.size).isEqualTo(1)
    runOnIdle { rotation.value = 10f }
    waitForIdle()
    assertThat(container(first).model.surfaces.size).isEqualTo(2)
    runOnIdle {
      rotation.value = 0f
      scale.value = 0.5f
    }
    waitForIdle()
    assertThat(container(first).model.surfaces.size).isEqualTo(2)
    runOnIdle { scale.value = 1f }
    waitForIdle()
    assertThat(container(first).model.surfaces.size).isEqualTo(1)
  }

  @Test
  fun membership_unrelatedRecompositionKeepsMemberBinding() = runComposeUiTest {
    val effect = GlassRuntimeEffect()
    val factory = HazeEffectFactory { effect }
    val counter = mutableStateOf(0)
    var compositions = 0
    setContent {
      Box(Modifier.hazeGlassContainer(40.dp)) {
        SideEffect { compositions++ }
        counter.value
        Box(Modifier.size(100.dp, 40.dp).hazeGlass(factory, HazeInput.Content, GlassStyle, null, true, null))
      }
    }
    waitForIdle()
    val binding = checkNotNull(effect.memberBindingForTest)
    val before = compositions
    runOnIdle { counter.value++ }
    waitForIdle()
    assertThat(compositions).isEqualTo(before + 1)
    assertThat(effect.memberBindingForTest).isSameInstanceAs(binding)
    assertThat(binding.node?.container).isSameInstanceAs(container(effect))
  }

  @Test
  fun membership_directionalLightUnderDifferentLayoutDirectionsStaysSeparate() = runComposeUiTest {
    val first = GlassRuntimeEffect()
    val second = GlassRuntimeEffect()
    val style = GlassStyle { lightPosition(Alignment.TopStart) }
    setContent {
      Row(Modifier.hazeGlassContainer(40.dp)) {
        Member(first, style = style)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Member(second, style = style) }
      }
    }
    waitForIdle()
    assertThat(container(first).model.surfaces.size).isEqualTo(2)
  }

  @Test
  fun membership_styleChangeThenReactivationKeepsMemberConnected() = runComposeUiTest {
    val runtimes = mutableListOf<GlassRuntimeEffect>()
    val factory = HazeEffectFactory { GlassRuntimeEffect().also { runtimes += it } }
    val style = mutableStateOf(GlassStyle { alpha(1f) })
    val active = mutableStateOf(true)
    setContent {
      Box(Modifier.hazeGlassContainer(40.dp)) {
        ReusableContentHost(active.value) {
          Box(Modifier.size(100.dp, 40.dp).hazeGlass(factory, HazeInput.Content, style.value, null, true, null))
        }
      }
    }
    waitForIdle()
    runOnIdle { style.value = GlassStyle { alpha(0.5f) } }
    waitForIdle()
    runOnIdle { active.value = false }
    waitForIdle()
    runOnIdle { active.value = true }
    waitForIdle()
    val binding = checkNotNull(runtimes.last().memberBindingForTest)
    assertThat(checkNotNull(binding.node).binding).isSameInstanceAs(binding)
    assertThat(binding.node?.container).isSameInstanceAs(container(runtimes.last()))
  }

  @Test
  fun membership_opticalSizeChangeRepublishesMaterialBeforeReclustering() = runComposeUiTest {
    val card = GlassRuntimeEffect()
    val pill = GlassRuntimeEffect()
    setContent {
      Row(Modifier.hazeGlassContainer(40.dp)) {
        Member(card, Modifier.size(300.dp, 180.dp))
        Member(pill, Modifier.size(140.dp, 64.dp))
      }
    }
    waitForIdle()
    runOnIdle {
      val container = container(pill)
      val before = checkNotNull(pill.memberBindingForTest?.material).resolvedOptics
      container.remove(checkNotNull(card.memberBindingForTest?.node))
      val context = checkNotNull(pill.attachedContextForTest)
      val expected = resolveGlassStyle(pill, context.modifierSize, context.requireDensity(), LayoutDirection.Ltr).resolvedOptics
      assertThat(expected).isNotEqualTo(before)
      // The removed card's glass may still retract into the pill, so find the pill's own snapshot.
      val snapshot = container.model.surfaces.flatMap { it.members }.single { it.identity === pill.memberBindingForTest?.node }
      assertThat(snapshot.material.resolvedOptics).isEqualTo(expected)
    }
  }

  @Test
  fun memberTransition_defaultsToMatchedGeometryAndUpdatesInPlace() = runComposeUiTest {
    val effect = GlassRuntimeEffect()
    val factory = HazeEffectFactory { effect }
    val transition = mutableStateOf<GlassMemberTransition?>(null)
    setContent {
      Box(Modifier.hazeGlassContainer(40.dp)) {
        val modifier = Modifier.size(100.dp, 40.dp)
        Box(
          transition.value?.let { modifier.hazeGlass(factory, HazeInput.Content, GlassStyle, null, true, null, memberTransition = it) }
            ?: modifier.hazeGlass(factory, HazeInput.Content, GlassStyle, null, true, null),
        )
      }
    }
    waitForIdle()
    val element = Modifier.hazeGlass(HazeInput.Content)
      .foldIn<GlassMemberElement?>(null) { found, item -> found ?: item as? GlassMemberElement }
    assertThat(checkNotNull(element).configuration.memberTransition).isEqualTo(GlassMemberTransition.MatchedGeometry)
    val node = checkNotNull(effect.memberBindingForTest?.node)
    val binding = node.binding
    assertThat(node.transition).isEqualTo(GlassMemberTransition.MatchedGeometry)
    runOnIdle { transition.value = GlassMemberTransition.None }
    waitForIdle()
    assertThat(effect.memberBindingForTest?.node).isSameInstanceAs(node)
    assertThat(node.transition).isEqualTo(GlassMemberTransition.None)
    // An equal binding hands over the live connection rather than reconnecting.
    assertThat(node.binding).isEqualTo(binding)
    assertThat(effect.memberBindingForTest).isSameInstanceAs(node.binding)
  }
}
