// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class)
class VisualEffectReplacementDrawTest : ContextTest() {

  @Test
  fun replacementDrawsNewEffectOnNextFrame() = runComposeUiTest {
    val effect1 = RetainedOutputRecordingVisualEffect()
    val effect2 = ReplacementBoundsVisualEffect()
    val effect = mutableStateOf<VisualEffect>(effect1)

    setContent {
      Spacer(
        Modifier
          .size(100.dp)
          .hazeEffect {
            visualEffect = effect.value
          },
      )
    }

    waitForIdle()
    val effect1DrawCalls = effect1.drawCalls
    val effect1ClearCalls = effect1.clearCalls

    effect.value = effect2
    waitForIdle()

    assertThat(effect1.drawCalls).isEqualTo(effect1DrawCalls)
    assertThat(effect1.clearCalls).isGreaterThan(effect1ClearCalls)
    assertThat(effect2.calculateLayerBoundsCalls).isGreaterThan(0)
    assertThat(effect2.drawCalls).isGreaterThan(0)
  }

  @Test
  fun replacementWithEmptyRemovesOldEffectOnNextFrame() = runComposeUiTest {
    val recordingEffect = RetainedOutputRecordingVisualEffect()
    val effect = mutableStateOf<VisualEffect>(recordingEffect)
    var contentDrawCalls = 0

    setContent {
      Spacer(
        Modifier
          .size(100.dp)
          .drawWithContent {
            contentDrawCalls++
            drawContent()
          }
          .hazeEffect {
            visualEffect = effect.value
          },
      )
    }

    waitForIdle()
    val recordingEffectDrawCalls = recordingEffect.drawCalls
    val initialClearCalls = recordingEffect.clearCalls
    val initialContentDrawCalls = contentDrawCalls

    effect.value = VisualEffect.Empty
    waitForIdle()

    assertThat(recordingEffect.drawCalls).isEqualTo(recordingEffectDrawCalls)
    assertThat(recordingEffect.clearCalls).isGreaterThan(initialClearCalls)
    assertThat(contentDrawCalls).isGreaterThan(initialContentDrawCalls)
  }
}

private class ReplacementBoundsVisualEffect : VisualEffect {
  var calculateLayerBoundsCalls = 0
  var drawCalls = 0

  override fun calculateLayerBounds(rect: Rect, density: Density): Rect {
    calculateLayerBoundsCalls++
    return rect.inflate(8f)
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    drawCalls++
  }
}
