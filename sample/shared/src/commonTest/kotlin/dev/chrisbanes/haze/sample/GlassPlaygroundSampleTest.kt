// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GlassPlaygroundSampleTest {
  @Test
  fun controlsForwardPlayResetAndRecordingEvents() = runComposeUiTest {
    var playPauseCount = 0
    var resetCount = 0
    var recordingMode = false
    setContent {
      GlassPlaygroundSampleContent(
        progressProvider = { 0.25f },
        dragOffsetProvider = { Offset.Zero },
        isPlaying = true,
        recordingMode = recordingMode,
        onPlayPause = { playPauseCount++ },
        onReset = { resetCount++ },
        onRecordingModeChanged = { recordingMode = it },
        onBack = {},
        onDragStart = {},
        onDrag = { _, _ -> },
        onDragEnd = {},
      )
    }

    onNodeWithContentDescription("Pause animation").performClick()
    onNodeWithContentDescription("Reset demo").performClick()
    onNodeWithContentDescription("Enter recording mode").performClick()

    assertEquals(1, playPauseCount)
    assertEquals(1, resetCount)
    assertTrue(recordingMode)
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

    assertEquals(Offset(140f, 150f), base)
    assertEquals(Offset(-60f, 350f), dragged)
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

    assertEquals(Offset(310f, -130f), light)
  }
}
