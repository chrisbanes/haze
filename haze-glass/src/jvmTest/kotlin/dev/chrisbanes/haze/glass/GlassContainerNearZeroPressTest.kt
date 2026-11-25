// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isCloseTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.RuntimeShaderRenderEffectException
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/**
 * A merged member pressed to a near-zero scale rebases into the cluster's field frame. Rounding
 * can collapse its size there even though cluster admission accepted it in owner space.
 */
class GlassContainerNearZeroPressTest : ContextTest() {
  @Test
  fun authoredNearZeroPress_mergedFieldKeepsPositiveRect_runtime() = runComposeUiTest { authoredNearZeroPress(unavailable = false) }

  @Test
  fun authoredNearZeroPress_mergedFieldKeepsPositiveRect_fallback() = runComposeUiTest { authoredNearZeroPress(unavailable = true) }

  private fun ComposeUiTest.authoredNearZeroPress(unavailable: Boolean) {
    val fixture = nearZeroCluster(GlassStyle { pressed { animate(snap(), snap()) { scale(2e-8f) } } }, unavailable)
    assertThat(fixture.owner().model.surfaces.single().members).hasSize(2)
    val before = fixture.capture()
    val press = PressInteraction.Press(Offset(10f, 10f))
    runBlocking { fixture.firstSource.emit(press) }
    waitForIdle()
    val pressed = fixture.capture()

    assertThat(fixture.owner().model.surfaces.single().members).hasSize(2)
    fixture.assertFieldPositive()
    (0..2).forEach { channel -> assertThat(fixture.channel(pressed, channel)).isCloseTo(fixture.channel(before, channel), 4f / 255f) }

    runBlocking { fixture.firstSource.emit(PressInteraction.Release(press)) }
    waitForIdle()
    fixture.capture()
    val rect = fixture.field().members[0].rect
    assertThat(rect.left).isCloseTo(100f, 0.01f)
    assertThat(rect.top).isCloseTo(100f, 0.01f)
    assertThat(rect.right).isCloseTo(180f, 0.01f)
    assertThat(rect.bottom).isCloseTo(180f, 0.01f)
  }

  /** A negative spring sample floors to `Float.MIN_VALUE`; `isValidGlassMember` then drops the member for that frame. */
  @Test
  fun springUndershootPress_mergedMemberLeavesFieldWhileFloored_runtime() = runComposeUiTest {
    val spring = spring<Float>(dampingRatio = 0.5f, stiffness = 200f)
    val fixture = nearZeroCluster(GlassStyle { pressed { animate(spring, spring) { scale(0.1f) } } }, unavailable = false)
    val node = checkNotNull(fixture.first.memberBindingForTest).node
    mainClock.autoAdvance = false
    runBlocking { fixture.firstSource.emit(PressInteraction.Press(Offset(10f, 10f))) }
    var floored = false
    repeat(120) {
      mainClock.advanceTimeByFrame()
      fixture.capture()
      fixture.owner().renderer.runtimeSurfaces.singleOrNull()?.mergedField?.let { fixture.assertFieldPositive() }
      val size = checkNotNull(fixture.first.attachedContextForTest).modifierSize
      if (fixture.first.currentMaterialTransform(size).scaleX == Float.MIN_VALUE) {
        floored = true
        assertThat(fixture.owner().model.surfaces.none { surface -> surface.members.any { it.identity === node } }).isTrue()
      }
    }
    assertThat(floored).isTrue()
    mainClock.autoAdvance = true
    waitForIdle()
    assertThat(fixture.owner().model.surfaces.single().members).hasSize(2)
  }

  private class Fixture(
    val test: ComposeUiTest,
    val first: GlassRuntimeEffect,
    val firstSource: MutableInteractionSource,
  ) {
    fun owner() = checkNotNull(first.memberBindingForTest?.node?.container)
    fun field() = checkNotNull(owner().renderer.runtimeSurfaces.single().mergedField)
    fun capture() = test.onNodeWithTag("root").captureToImage().toPixelMap()
    fun channel(map: androidx.compose.ui.graphics.PixelMap, channel: Int): Float = map[130, 40].let { listOf(it.red, it.green, it.blue)[channel] }

    fun assertFieldPositive() {
      field().members.forEach { member ->
        assertThat(member.rect.width).isGreaterThan(0f)
        assertThat(member.rect.height).isGreaterThan(0f)
        assertThat((member.layoutRect.width / member.rect.width).isFinite()).isTrue()
        assertThat((member.layoutRect.height / member.rect.height).isFinite()).isTrue()
      }
    }
  }

  private fun ComposeUiTest.nearZeroCluster(pressed: GlassStyle, unavailable: Boolean): Fixture {
    val first = GlassRuntimeEffect()
    val second = GlassRuntimeEffect()
    if (unavailable) {
      listOf(first, second).forEach { effect ->
        effect.runtimeEffectFactory = GlassRuntimeEffectFactory { throw RuntimeShaderRenderEffectException(IllegalStateException("test unavailable runtime")) }
      }
    }
    val firstSource = MutableInteractionSource()
    val state = HazeState()
    setContent {
      Box(Modifier.size(360.dp, 240.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(state).background(Color.White))
        Box(Modifier.size(360.dp, 240.dp).hazeGlassContainer(100.dp)) {
          Box(
            Modifier.size(80.dp).hazeGlass(
              HazeEffectFactory { first }, HazeInput.Sources(state), containerTestStyle(Color.Red).then(pressed),
              HazePerformanceMode.Quality, true, firstSource, GlassTransformTarget.MaterialOnly, GlassTransformPivot.Pointer,
              GlassReducedMotionPolicy.Full,
            ),
          )
          Box(
            Modifier.offset(90.dp, 0.dp).size(80.dp).hazeGlass(
              HazeEffectFactory { second }, HazeInput.Sources(state), containerTestStyle(Color.Blue),
              HazePerformanceMode.Quality, true, MutableInteractionSource(), GlassTransformTarget.MaterialOnly, GlassTransformPivot.Pointer,
              GlassReducedMotionPolicy.Full,
            ),
          )
        }
      }
    }
    waitForIdle()
    return Fixture(this, first, firstSource)
  }
}
