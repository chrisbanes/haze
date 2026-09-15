// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassStyle
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlassTiltVisualsTest {
  @Test
  fun lightAlignment_tracksTheLatestPositionInAbsoluteCoordinates() {
    val position = mutableStateOf(Offset(0.32f, 0.68f))
    val alignment = GlassTiltLightAlignment(position)
    var invalidated = false
    val observer = SnapshotStateObserver { command -> command() }
    observer.start()

    try {
      observer.observeReads(Unit, { invalidated = true }) {
        assertThat(alignment.align(IntSize.Zero, IntSize(100, 200), LayoutDirection.Ltr))
          .isEqualTo(IntOffset(32, 136))
      }

      position.value = Offset(0.68f, 0.32f)
      Snapshot.sendApplyNotifications()

      assertThat(invalidated).isTrue()
      assertThat(alignment.align(IntSize.Zero, IntSize(100, 200), LayoutDirection.Rtl))
        .isEqualTo(IntOffset(68, 64))
    } finally {
      observer.stop()
    }
  }

  @Test
  fun rememberedStyle_survivesTiltPositionUpdates() = runComposeUiTest {
    val position = mutableStateOf(Offset(0.50f, 0.50f))
    var firstStyle: GlassStyle? = null
    var latestStyle: GlassStyle? = null

    setContent {
      val style = rememberGlassTiltStyle(position)
      GlassTiltLightPositionLabel(position)
      SideEffect {
        if (firstStyle == null) firstStyle = style
        latestStyle = style
      }
    }
    waitForIdle()

    runOnIdle { position.value = Offset(0.68f, 0.32f) }
    waitForIdle()

    assertThat(latestStyle).isSameInstanceAs(firstStyle)
  }
}
