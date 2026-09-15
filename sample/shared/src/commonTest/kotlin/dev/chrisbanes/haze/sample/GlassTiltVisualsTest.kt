// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import kotlin.test.Test

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

}
