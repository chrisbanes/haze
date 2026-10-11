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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isCloseTo
import assertk.assertions.prop
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
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
  fun graphicsLayerScale_stillNeverMerges() = runComposeUiTest {
    val fixture = cluster(firstModifier = Modifier.graphicsLayer { scaleX = 0.9f })
    assertThat(fixture.owner().model.surfaces).hasSize(2)
  }

  private class ClusterFixture(
    val test: ComposeUiTest,
    val first: GlassRuntimeEffect,
    val firstSource: MutableInteractionSource,
    val secondSource: MutableInteractionSource,
  ) {
    fun owner() = checkNotNull(first.memberBindingForTest?.node?.container)
    fun field() = checkNotNull(owner().renderer.runtimeSurfaces.single().mergedField)

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
    pressed: GlassStyle = GlassStyle { pressed { scale(0.9f) } },
    policy: GlassReducedMotionPolicy = GlassReducedMotionPolicy.Full,
    firstModifier: Modifier = Modifier,
  ): ClusterFixture {
    val first = GlassRuntimeEffect()
    val firstSource = MutableInteractionSource()
    val secondSource = MutableInteractionSource()
    val state = HazeState()
    setContent {
      Box(Modifier.size(360.dp, 240.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(state).background(Color.White))
        Box(Modifier.size(360.dp, 240.dp).hazeGlassContainer(40.dp)) {
          Box(
            Modifier.offset(40.dp, 60.dp).size(100.dp, 80.dp).then(firstModifier).hazeGlass(
              HazeEffectFactory { first }, HazeInput.Sources(state), containerTestStyle(Color.Red).then(pressed),
              HazePerformanceMode.Quality, true, firstSource, GlassTransformTarget.MaterialOnly, GlassTransformPivot.Center, policy,
            ),
          )
          Box(
            Modifier.offset((140 + gap).dp, 60.dp).size(100.dp, 80.dp).hazeGlass(
              HazeEffectFactory { GlassRuntimeEffect() }, HazeInput.Sources(state), containerTestStyle(Color.Blue).then(pressed),
              HazePerformanceMode.Quality, true, secondSource, GlassTransformTarget.MaterialOnly, GlassTransformPivot.Center, policy,
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
