// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class)
class VisualEffectRetainedOutputTest : ContextTest() {

  @Test
  fun visualEffect_retainedOutputDrawnWhenBackgroundAreasDisappear() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = RetainedOutputRecordingVisualEffect()
    val showSource = mutableStateOf(true)

    setContent {
      Box(Modifier.size(100.dp)) {
        if (showSource.value) {
          Spacer(Modifier.size(100.dp).hazeSource(hazeState))
        }
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect(hazeState) {
              visualEffect = effect
            },
        )
      }
    }

    waitForIdle()
    assertThat(effect.drawCalls).isGreaterThan(0)
    assertThat(effect.lastDrawAreaCount).isGreaterThan(0)

    val beforeRemovalDraws = effect.drawCalls
    showSource.value = false
    waitForIdle()

    assertThat(effect.drawCalls).isGreaterThan(beforeRemovalDraws)
    assertThat(effect.lastDrawAreaCount).isEqualTo(0)
  }

  @Test
  fun visualEffect_retainedOutputNotDrawnWhenNeverAvailable() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = RetainedOutputRecordingVisualEffect()

    setContent {
      Spacer(
        Modifier
          .size(100.dp)
          .hazeEffect(hazeState) {
            visualEffect = effect
          },
      )
    }

    waitForIdle()

    assertThat(effect.drawCalls).isEqualTo(0)
  }

  @Test
  fun visualEffect_pendingRetainedOutputDrawnWhenBackgroundAreasDisappear() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = RetainedOutputRecordingVisualEffect()
    val showSource = mutableStateOf(true)

    setContent {
      Box(Modifier.size(100.dp)) {
        if (showSource.value) {
          Spacer(Modifier.size(100.dp).hazeSource(hazeState))
        }
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect(hazeState) {
              visualEffect = effect
            },
        )
      }
    }

    waitForIdle()
    assertThat(effect.drawCalls).isGreaterThan(0)
    assertThat(effect.lastDrawAreaCount).isGreaterThan(0)

    val beforeRemovalDraws = effect.drawCalls
    effect.retainedOutputAvailable = false
    effect.pendingRetainedOutput = true
    showSource.value = false
    waitForIdle()

    assertThat(effect.drawCalls).isGreaterThan(beforeRemovalDraws)
    assertThat(effect.lastDrawAreaCount).isEqualTo(0)
  }
}
