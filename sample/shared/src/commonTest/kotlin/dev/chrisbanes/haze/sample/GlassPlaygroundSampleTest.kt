// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.navigation.compose.rememberNavController
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassTransformPivot
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.GlassVisualEffect
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalHazeApi::class, ExperimentalTestApi::class)
class GlassPlaygroundSampleTest : ContextTest() {
  @Test
  fun reset_restartsAutoplayFromLoopZeroWithANewGeneration() = runTest {
    val state = GlassPlaygroundState()
    val initialGeneration = state.autoplayGeneration

    state.reset()

    assertEquals(0f, state.progress())
    assertEquals(0, state.completedLoopCount)
    assertEquals(initialGeneration + 1, state.autoplayGeneration)
    assertTrue(state.isPlaying)
  }

  @Test
  fun resetAction_restartsAutoplayThroughACompleteLoop() = runComposeUiTest {
    mainClock.autoAdvance = false
    val autoplayGate = Channel<Unit>(capacity = Channel.UNLIMITED)
    val state = GlassPlaygroundState(
      loopDurationMillis = TEST_PLAYGROUND_LOOP_DURATION_MILLIS,
    ).apply {
      togglePlayback()
    }
    setContent {
      GlassPlaygroundSample(
        navController = rememberNavController(),
        state = state,
        runAutoplay = {
          autoplayGate.receive()
          runAutoplayLoop(loopLimit = 1)
        },
      )
    }

    state.togglePlayback()
    mainClock.advanceTimeByFrame()
    autoplayGate.trySend(Unit)
    mainClock.advanceTimeBy(TEST_PLAYGROUND_LOOP_DURATION_MILLIS.toLong() + 1_000)
    onNodeWithTag("glass_playground_loop_1").assertIsDisplayed()

    onNodeWithContentDescription("Reset demo").performClick()
    mainClock.advanceTimeByFrame()
    onNodeWithTag("glass_playground_loop_0").assertIsDisplayed()

    autoplayGate.trySend(Unit)
    mainClock.advanceTimeBy(TEST_PLAYGROUND_LOOP_DURATION_MILLIS.toLong() + 1_000)
    onNodeWithTag("glass_playground_loop_1").assertIsDisplayed()

    state.togglePlayback()
    autoplayGate.close()
    mainClock.advanceTimeByFrame()
  }

  @Test
  fun content_exposesCompletedTimelineLoop() = runComposeUiTest {
    setContent {
      GlassPlaygroundSampleContent(
        progressProvider = { 0f },
        dragOffsetProvider = { Offset.Zero },
        isPlaying = true,
        recordingMode = true,
        completedLoopCount = 2,
        onPlayPause = {},
        onReset = {},
        onRecordingModeChanged = {},
        onBack = {},
        onDragStart = {},
        onDrag = { _, _ -> },
        onDragEnd = {},
      )
    }

    onNodeWithTag("glass_playground_loop_2").assertIsDisplayed()
  }

  @Test
  fun playgroundInteraction_keepsHoverAfterPressAndUsesPointerContentTransform() {
    val source = MutableInteractionSource()
    val effect = GlassVisualEffect()

    effect.configurePlaygroundInteraction(source)

    assertThat(effect.interactionSource).isEqualTo(source)
    assertThat(effect.interactionTransformTarget).isEqualTo(GlassTransformTarget.MaterialAndContent)
    assertThat(effect.interactionTransformPivot).isEqualTo(GlassTransformPivot.Pointer)
    assertThat(effect.observesPointerEvents).isTrue()

    effect.clearPressed()
    assertThat(effect.observesPointerEvents).isTrue()
    effect.clearHovered()
    assertThat(effect.observesPointerEvents).isFalse()
  }

  @Test
  fun resolvedCenterContainsBaseSurfaceBeforeApplyingUnboundedDrag() {
    val base = resolvedPlaygroundSurfaceCenter(
      normalizedCenter = Offset(0.1f, 0.9f),
      sceneSize = IntSize(320, 240),
      surfaceSize = IntSize(280, 180),
      dragOffset = Offset.Zero,
    )
    val dragged = resolvedPlaygroundSurfaceCenter(
      normalizedCenter = Offset(0.1f, 0.9f),
      sceneSize = IntSize(320, 240),
      surfaceSize = IntSize(280, 180),
      dragOffset = Offset(-200f, 200f),
    )

    assertThat(base).isEqualTo(Offset(140f, 150f))
    assertThat(dragged).isEqualTo(Offset(-60f, 350f))
  }

  @Test
  fun localLightSubtractsResolvedSurfaceOriginIncludingDrag() {
    val light = resolvePlaygroundSurfaceLightPosition(
      normalizedLight = Offset(0.75f, 0.25f),
      normalizedCenter = Offset(0.5f, 0.5f),
      sceneSize = IntSize(1_000, 800),
      surfaceSize = IntSize(200, 100),
      dragOffset = Offset(40f, -20f),
    )

    assertThat(light).isEqualTo(Offset(310f, -130f))
  }
}

private const val TEST_PLAYGROUND_LOOP_DURATION_MILLIS = 100
