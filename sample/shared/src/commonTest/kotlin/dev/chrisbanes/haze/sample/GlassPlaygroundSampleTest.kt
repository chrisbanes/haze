// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalHazeApi::class, ExperimentalTestApi::class)
class GlassPlaygroundSampleTest : ContextTest() {
  @Test
  fun reset_restartsAutoplayFromLoopZeroWithANewGeneration() = runTest {
    val state = GlassPlaygroundState()
    val initialGeneration = state.autoplayGeneration

    state.reset()

    assertThat(state.progress()).isEqualTo(0f)
    assertThat(state.completedLoopCount).isEqualTo(0)
    assertThat(state.autoplayGeneration).isEqualTo(initialGeneration + 1)
    assertThat(state.isPlaying).isTrue()
  }

  @Test
  fun resetAction_invokesResetCallback() = runComposeUiTest {
    var resetCount = 0
    setContent {
      GlassPlaygroundSampleContent(
        progressProvider = { 0f },
        dragOffsetProvider = { Offset.Zero },
        isPlaying = false,
        recordingMode = false,
        onPlayPause = {},
        onReset = { resetCount++ },
        onRecordingModeChanged = {},
        onBack = {},
        onDragStart = {},
        onDrag = { _, _ -> },
        onDragEnd = {},
      )
    }

    onNodeWithContentDescription("Reset demo").performClick()

    runOnIdle { assertThat(resetCount).isEqualTo(1) }
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
  fun playgroundInteraction_isDeclaredInTheTypedStyle() {
    assertThat(playgroundInteractionStyle()).isNotEqualTo(GlassStyle)
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
