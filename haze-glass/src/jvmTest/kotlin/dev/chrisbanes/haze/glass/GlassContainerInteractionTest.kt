// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isCloseTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import assertk.assertions.prop
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.math.abs
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class GlassContainerInteractionTest : ContextTest() {
  @Test
  fun pressMergedMember_doesNotThrowAndStaysOneSurface() = runComposeUiTest {
    val fixture = cluster()
    fixture.press(fixture.firstSource)
    onNodeWithTag("root").captureToImage()
    assertThat(fixture.owner().renderer.runtimeSurfaces).hasSize(1)
    assertThat(fixture.owner().model.surfaces.single().members).hasSize(2)
  }

  @Test
  fun pressedMember_rectTransformedWithinField() = runComposeUiTest {
    val fixture = cluster()
    // Field origin is the members' layout union inflated by the 40px spacing: owner (0, 20).
    val firstLayout = Rect(40f, 40f, 140f, 120f)
    val secondLayout = Rect(150f, 40f, 250f, 120f)
    val press = fixture.press(fixture.firstSource)
    assertThat(fixture.field().members[0].rect).isCloseTo(Rect(45f, 44f, 135f, 116f))
    assertThat(fixture.field().members[1].rect).isCloseTo(secondLayout)
    fixture.release(fixture.firstSource, press)
    assertThat(fixture.field().members[0].rect).isCloseTo(firstLayout)
  }

  @Test
  fun reducedMotionMember_keepsLayoutRect() = runComposeUiTest {
    val fixture = cluster(policy = GlassReducedMotionPolicy.Reduced)
    fixture.press(fixture.firstSource)
    assertThat(fixture.field().members[0].rect).isCloseTo(Rect(40f, 40f, 140f, 120f))
  }

  @Test
  fun pressThatOpensGap_splitsAndReleaseRemerges() = runComposeUiTest {
    val fixture = cluster(gap = 15, pressed = GlassStyle { pressed { scale(0.8f) } })
    assertThat(fixture.owner().model.surfaces).hasSize(1)
    // The 15px gap grows by 10px to 25px, at least half the 40px spacing.
    val press = fixture.press(fixture.firstSource)
    assertThat(fixture.owner().model.surfaces).hasSize(2)
    fixture.release(fixture.firstSource, press)
    assertThat(fixture.owner().model.surfaces).hasSize(1)
  }

  @Test
  fun anisotropicPress_drawsStandaloneAndReleaseRemerges() = runComposeUiTest {
    // A 0.9 horizontal press widens the gap to 15px, still bridged, but a merged rounded rect cannot
    // represent unequal axes, so the member draws standalone while pressed.
    val fixture = cluster(pressed = GlassStyle { pressed { scale(0.9f, 1f) } })
    val press = fixture.press(fixture.firstSource)
    assertThat(fixture.owner().model.surfaces).hasSize(2)
    fixture.release(fixture.firstSource, press)
    assertThat(fixture.owner().model.surfaces).hasSize(1)
  }

  @Test
  fun graphicsLayerScale_stillNeverMerges() = runComposeUiTest {
    val fixture = cluster(firstModifier = Modifier.graphicsLayer { scaleX = 0.9f })
    assertThat(fixture.owner().model.surfaces).hasSize(2)
  }

  @Test
  fun pressOneMember_populatesItsLightOnly() = runComposeUiTest {
    val fixture = cluster(pressed = lit)
    val rest = fixture.capture()
    fixture.press(fixture.firstSource)
    val members = fixture.field().members
    assertThat(members.map { it.lightIntensity }).containsExactly(1f, 0f)
    assertThat(members[0].rect.contains(members[0].lightPosition)).isTrue()
    val pressed = fixture.capture()
    assertThat(pressed[90, 100].luminance()).isGreaterThan(rest[90, 100].luminance())
    assertThat(abs(pressed[200, 100].luminance() - rest[200, 100].luminance())).isLessThan(1f / 255f)
  }

  @Test
  fun pressBothMembers_lightsBoth() = runComposeUiTest {
    val fixture = cluster(pressed = lit)
    val rest = fixture.capture()
    fixture.press(fixture.firstSource)
    fixture.press(fixture.secondSource)
    assertThat(fixture.field().members.map { it.lightIntensity }).containsExactly(1f, 1f)
    val pressed = fixture.capture()
    assertThat(pressed[90, 100].luminance()).isGreaterThan(rest[90, 100].luminance())
    assertThat(pressed[200, 100].luminance()).isGreaterThan(rest[200, 100].luminance())
  }

  @Test
  fun pressedPointerPivot_lightStaysUnderPointer() = runComposeUiTest {
    val fixture = cluster(
      pressed = GlassStyle {
        pressed {
          scale(0.9f)
          lightingIntensity(1f)
        }
      },
      pivot = GlassTransformPivot.Pointer,
    )
    fixture.press(fixture.firstSource, Offset(20f, 30f))
    val member = fixture.field().members[0]
    // A's field-local layout origin is (40, 40); a pointer pivot keeps the pressed point fixed.
    assertThat(member.rect).isCloseTo(Rect(42f, 43f, 132f, 115f))
    assertThat(member.lightPosition.x).isCloseTo(60f, 0.01f)
    assertThat(member.lightPosition.y).isCloseTo(70f, 0.01f)
    assertThat(member.lightRadius).isCloseTo(72f * fixture.first.interactionLightRadiusFraction, 0.01f)
  }

  @Test
  fun releaseOvershoot_staysInsideClusterSurface() = runComposeUiTest {
    // Abutting members with a 2px spacing: a 4% overshoot pushes A's outer edge past the spacing.
    val bouncy = GlassStyle { pressed { animate(spring(dampingRatio = 0.5f, stiffness = 200f), spring(dampingRatio = 0.5f, stiffness = 200f)) { scale(0.5f) } } }
    val fixture = cluster(gap = 0, spacing = 2, pressed = bouncy)
    val press = fixture.press(fixture.firstSource)
    mainClock.autoAdvance = false
    runBlocking { fixture.firstSource.emit(PressInteraction.Release(press)) }
    var overshot = false
    var minLeft = Float.POSITIVE_INFINITY
    repeat(120) {
      mainClock.advanceTimeByFrame()
      waitForIdle()
      overshot = overshot || fixture.first.currentInteractionState.scaleX > 1.04f
      val field = fixture.owner().renderer.runtimeSurfaces.singleOrNull()?.mergedField ?: return@repeat
      minLeft = minOf(minLeft, field.members.minOf { it.rect.left })
    }
    assertThat(overshot).isTrue()
    assertThat(minLeft, "merged field observed").isLessThan(Float.POSITIVE_INFINITY)
    // Field rects are relative to the surface bounds, so a negative edge is clipped.
    assertThat(minLeft).isGreaterThanOrEqualTo(0f)
  }

  private val lit = GlassStyle { pressed { lightingIntensity(1f) } }

  private class ClusterFixture(
    val test: ComposeUiTest,
    val first: GlassRuntimeEffect,
    val firstSource: MutableInteractionSource,
    val secondSource: MutableInteractionSource,
  ) {
    fun owner() = checkNotNull(first.memberBindingForTest?.node?.container)
    fun field() = checkNotNull(owner().renderer.runtimeSurfaces.single().mergedField)
    fun capture() = test.onNodeWithTag("root").captureToImage().toPixelMap()

    fun press(source: MutableInteractionSource, position: Offset = Offset(50f, 40f)): PressInteraction.Press {
      val press = PressInteraction.Press(position)
      runBlocking { source.emit(press) }
      test.waitForIdle()
      return press
    }

    fun release(source: MutableInteractionSource, press: PressInteraction.Press) {
      runBlocking { source.emit(PressInteraction.Release(press)) }
      test.waitForIdle()
    }
  }

  private fun ComposeUiTest.cluster(
    gap: Int = 10,
    spacing: Int = 40,
    pressed: GlassStyle = GlassStyle { pressed { scale(0.9f) } },
    policy: GlassReducedMotionPolicy = GlassReducedMotionPolicy.Full,
    pivot: GlassTransformPivot = GlassTransformPivot.Center,
    firstModifier: Modifier = Modifier,
  ): ClusterFixture {
    val first = GlassRuntimeEffect()
    val firstSource = MutableInteractionSource()
    val secondSource = MutableInteractionSource()
    val state = HazeState()
    setContent {
      Box(Modifier.size(360.dp, 240.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(state).background(Color.White))
        Box(Modifier.size(360.dp, 240.dp).hazeGlassContainer(spacing.dp)) {
          Box(
            Modifier.offset(40.dp, 60.dp).size(100.dp, 80.dp).then(firstModifier).hazeGlass(
              HazeEffectFactory { first }, HazeInput.Sources(state), containerTestStyle(Color.Red).then(pressed),
              HazePerformanceMode.Quality, true, firstSource, GlassTransformTarget.MaterialOnly, pivot, policy,
            ),
          )
          Box(
            Modifier.offset((140 + gap).dp, 60.dp).size(100.dp, 80.dp).hazeGlass(
              HazeEffectFactory { GlassRuntimeEffect() }, HazeInput.Sources(state), containerTestStyle(Color.Blue).then(pressed),
              HazePerformanceMode.Quality, true, secondSource, GlassTransformTarget.MaterialOnly, pivot, policy,
            ),
          )
        }
      }
    }
    waitForIdle()
    return ClusterFixture(this, first, firstSource, secondSource)
  }
}

private fun Assert<Rect>.isCloseTo(expected: Rect) = all {
  prop(Rect::left).isCloseTo(expected.left, 0.01f)
  prop(Rect::top).isCloseTo(expected.top, 0.01f)
  prop(Rect::right).isCloseTo(expected.right, 0.01f)
  prop(Rect::bottom).isCloseTo(expected.bottom, 0.01f)
}
