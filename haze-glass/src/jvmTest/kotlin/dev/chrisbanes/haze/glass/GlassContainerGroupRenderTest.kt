// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.prop
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class GlassContainerGroupRenderTest : ContextTest() {
  @Test
  fun groupedPillsFarApart_drawOneFieldMemberOverBounds() = runComposeUiTest {
    val fixture = group()
    assertThat(fixture.owner().renderer.runtimeSurfaces).hasSize(1)
    // Field origin is the members' union inflated by the 40px merge distance.
    assertThat(fixture.field().members).hasSize(1)
    val member = fixture.field().members.single()
    assertThat(member.rect).isCloseTo(Rect(40f, 40f, 440f, 80f))
    assertThat(member.cornerRadii).isEqualTo(CornerRadii(20f, 20f, 20f, 20f))
  }

  @Test
  fun groupUsesFirstMembersShapeAndTint() = runComposeUiTest {
    val fixture = group(firstShape = RoundedCornerShape(12.dp), firstTint = Color.Red, secondTint = Color.Blue)
    val member = fixture.field().members.single()
    assertThat(member.cornerRadii).isEqualTo(CornerRadii(12f, 12f, 12f, 12f))
    assertThat(member.tint).isEqualTo(Color.Red)
  }

  @Test
  fun pressingOneGroupMember_scalesWholeGroupAndLightsIt() = runComposeUiTest {
    val fixture = group(pivot = GlassTransformPivot.Center)
    assertThat(fixture.field().members.single().lightIntensity).isEqualTo(0f)
    val press = fixture.press(fixture.secondSource)
    val member = fixture.field().members.single()
    // The 400x40 group scales by 0.9 about its centre, owner (240, 80).
    assertThat(member.rect).isCloseTo(Rect(60f, 42f, 420f, 78f))
    assertThat(member.lightIntensity).isGreaterThan(0f)
    fixture.release(fixture.secondSource, press)
    assertThat(fixture.field().members.single().rect).isCloseTo(Rect(40f, 40f, 440f, 80f))
    assertThat(fixture.field().members.single().lightIntensity).isEqualTo(0f)
  }

  @Test
  fun pressingGroupMemberWithPointerPivot_keepsPressedPointFixedInGroup() = runComposeUiTest {
    val fixture = group(pivot = GlassTransformPivot.Pointer)
    // Member-local (50, 20) of the second member is owner (390, 80) and field (390, 60).
    fixture.press(fixture.secondSource, Offset(50f, 20f))
    val member = fixture.field().members.single()
    assertThat(member.rect).isCloseTo(Rect(75f, 42f, 435f, 78f))
    assertThat(member.lightPosition.x).isCloseTo(390f, 0.01f)
    assertThat(member.lightPosition.y).isCloseTo(60f, 0.01f)
  }

  @Test
  fun groupBoundsSetContainerOpticalSize() = runComposeUiTest {
    val small = opticalGroup(grouped = true)
    // Two 100x40 members 60px apart stack into a 100x140 group, so its bounds set the optical size.
    assertThat(small.opticalSizeOverride).isEqualTo(Size(100f, 100f))
  }

  @Test
  fun ungroupedMembers_setContainerOpticalSizeFromEachMember() = runComposeUiTest {
    val small = opticalGroup(grouped = false)
    assertThat(small.opticalSizeOverride).isEqualTo(Size(40f, 40f))
  }

  private class GroupFixture(
    val test: ComposeUiTest,
    val first: GlassRuntimeEffect,
    val secondSource: MutableInteractionSource,
  ) {
    fun owner() = checkNotNull(first.memberBindingForTest?.node?.container)
    fun field() = checkNotNull(owner().renderer.runtimeSurfaces.single().mergedField)

    fun press(source: MutableInteractionSource, position: Offset = Offset(50f, 20f)): PressInteraction.Press {
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

  /** Two stacked 100x40 members and a separate 30x30 member; returns the small member's runtime. */
  private fun ComposeUiTest.opticalGroup(grouped: Boolean): GlassRuntimeEffect {
    val effects = List(3) { GlassRuntimeEffect() }
    val state = HazeState()
    setContent {
      Box(Modifier.size(520.dp, 300.dp).background(Color.White)) {
        Box(Modifier.size(520.dp, 300.dp).hazeSource(state).background(Color.White))
        Box(Modifier.size(520.dp, 300.dp).hazeGlassContainer(10.dp)) {
          listOf(DpOffset(40.dp, 20.dp) to DpSize(100.dp, 40.dp), DpOffset(40.dp, 120.dp) to DpSize(100.dp, 40.dp), DpOffset(300.dp, 20.dp) to DpSize(30.dp, 30.dp))
            .forEachIndexed { index, (offset, size) ->
              Box(
                Modifier.offset(offset.x, offset.y).size(size).hazeGlass(
                  HazeEffectFactory { effects[index] },
                  HazeInput.Sources(state),
                  containerTestStyle(Color.Red),
                  HazePerformanceMode.Quality,
                  true,
                  null,
                  groupKey = "group".takeIf { grouped && index < 2 },
                ),
              )
            }
        }
      }
    }
    waitForIdle()
    return effects[2]
  }

  /** Two 100x40 members 200px apart, far beyond the 40px merge distance, sharing a group key. */
  private fun ComposeUiTest.group(
    firstShape: RoundedCornerShape = RoundedCornerShape(percent = 50),
    firstTint: Color = Color.Red,
    secondTint: Color = Color.Blue,
    pivot: GlassTransformPivot = GlassTransformPivot.Center,
  ): GroupFixture {
    val first = GlassRuntimeEffect()
    val second = GlassRuntimeEffect()
    val firstSource = MutableInteractionSource()
    val secondSource = MutableInteractionSource()
    val state = HazeState()
    val pressed = GlassStyle {
      pressed {
        scale(0.9f)
        lightingIntensity(1f)
      }
    }
    setContent {
      Box(Modifier.size(520.dp, 200.dp).background(Color.White)) {
        Box(Modifier.size(520.dp, 200.dp).hazeSource(state).background(Color.White))
        Box(Modifier.size(520.dp, 200.dp).hazeGlassContainer(40.dp)) {
          Box(
            Modifier.offset(40.dp, 60.dp).size(100.dp, 40.dp).hazeGlass(
              HazeEffectFactory { first }, HazeInput.Sources(state),
              containerTestStyle(firstTint).then(GlassStyle { shape(firstShape) }).then(pressed),
              HazePerformanceMode.Quality, true, firstSource, GlassTransformTarget.MaterialOnly, pivot,
              GlassReducedMotionPolicy.Full, groupKey = "group",
            ),
          )
          Box(
            Modifier.offset(340.dp, 60.dp).size(100.dp, 40.dp).hazeGlass(
              HazeEffectFactory { second }, HazeInput.Sources(state),
              containerTestStyle(secondTint).then(GlassStyle { shape(RoundedCornerShape(percent = 50)) }).then(pressed),
              HazePerformanceMode.Quality, true, secondSource, GlassTransformTarget.MaterialOnly, pivot,
              GlassReducedMotionPolicy.Full, groupKey = "group",
            ),
          )
        }
      }
    }
    waitForIdle()
    return GroupFixture(this, first, secondSource)
  }
}

private fun Assert<Rect>.isCloseTo(expected: Rect) = all {
  prop(Rect::left).isCloseTo(expected.left, 0.01f)
  prop(Rect::top).isCloseTo(expected.top, 0.01f)
  prop(Rect::right).isCloseTo(expected.right, 0.01f)
  prop(Rect::bottom).isCloseTo(expected.bottom, 0.01f)
}
