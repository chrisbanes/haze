// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import dev.chrisbanes.haze.test.ContextTest
import java.awt.Frame
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, InternalHazeApi::class, ExperimentalComposeUiApi::class)
class HazePreDrawListenerChurnTest : ContextTest() {

  @Test
  fun positionStrategyEarlyReturn_unchangedAreas_keepsPreDrawListener() = runComposeUiTest {
    val hazeState = HazeState().apply {
      positionStrategy = HazePositionStrategy.Local
    }
    val sourceWindow = createTestWindow("SourceWindow")
    val effectWindow = createTestWindow("EffectWindow")
    var sourceKey by mutableStateOf("a")

    try {
      setContent {
        CompositionLocalProvider(LocalAwtWindow provides sourceWindow) {
          Box(Modifier.size(100.dp).hazeSource(hazeState, key = sourceKey))
        }
        CompositionLocalProvider(LocalAwtWindow provides effectWindow) {
          // Attach the effect node without placing it so lastKnownCoordinates stays null.
          Layout(
            content = {
              Spacer(
                Modifier
                  .size(100.dp)
                  .hazeEffect(
                    factory = TestEffectFactory,
                    input = HazeInput.Sources(hazeState),
                    style = Unit,
                  ),
              )
            },
          ) { _, constraints ->
            layout(constraints.minWidth, constraints.minHeight) {}
          }
        }
      }
      waitForIdle()

      val area = hazeState.areas.single()
      assertThat(area.preDrawListeners).isNotEmpty()

      // Dirty Areas without changing the filtered area set while switching position strategy so
      // updateGeometry hits the unplaced-coordinates early return.
      Snapshot.withMutableSnapshot {
        sourceKey = "b"
        hazeState.positionStrategy = HazePositionStrategy.Screen
      }
      waitForIdle()

      assertThat(area.preDrawListeners).isNotEmpty()
    } finally {
      (sourceWindow as? Frame)?.dispose()
      (effectWindow as? Frame)?.dispose()
    }
  }

  @Test
  fun redrawWithUnchangedAreas_doesNotWritePreDrawListeners() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceWindow = createTestWindow("SourceWindow")
    val effectWindow = createTestWindow("EffectWindow")
    var sourceKey by mutableStateOf("a")

    try {
      setContent {
        CompositionLocalProvider(LocalAwtWindow provides sourceWindow) {
          Box(Modifier.size(100.dp).hazeSource(hazeState, key = sourceKey))
        }
        CompositionLocalProvider(LocalAwtWindow provides effectWindow) {
          Spacer(
            Modifier
              .size(100.dp)
              .hazeEffect(
                factory = TestEffectFactory,
                input = HazeInput.Sources(hazeState),
                style = Unit,
              ),
          )
        }
      }
      waitForIdle()

      val area = hazeState.areas.single()
      assertThat(area.preDrawListeners).isNotEmpty()

      var preDrawListenerWrites = 0
      val handle = Snapshot.registerApplyObserver { applied, _ ->
        if (area.preDrawListeners in applied) {
          preDrawListenerWrites++
        }
      }
      try {
        sourceKey = "b"
        waitForIdle()
      } finally {
        handle.dispose()
      }

      assertThat(preDrawListenerWrites).isEqualTo(0)
      assertThat(area.preDrawListeners).isNotEmpty()
    } finally {
      (sourceWindow as? Frame)?.dispose()
      (effectWindow as? Frame)?.dispose()
    }
  }

  @Test
  fun detach_removesPreDrawListenerFromAreas() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceWindow = createTestWindow("SourceWindow")
    val effectWindow = createTestWindow("EffectWindow")
    var showEffect by mutableStateOf(true)

    try {
      setContent {
        CompositionLocalProvider(LocalAwtWindow provides sourceWindow) {
          Box(Modifier.size(100.dp).hazeSource(hazeState))
        }
        if (showEffect) {
          CompositionLocalProvider(LocalAwtWindow provides effectWindow) {
            Spacer(
              Modifier
                .size(100.dp)
                .hazeEffect(
                  factory = TestEffectFactory,
                  input = HazeInput.Sources(hazeState),
                  style = Unit,
                ),
            )
          }
        }
      }
      waitForIdle()

      val area = hazeState.areas.single()
      assertThat(area.preDrawListeners).isNotEmpty()

      showEffect = false
      waitForIdle()

      assertThat(area.preDrawListeners).isEmpty()
    } finally {
      (sourceWindow as? Frame)?.dispose()
      (effectWindow as? Frame)?.dispose()
    }
  }
}
