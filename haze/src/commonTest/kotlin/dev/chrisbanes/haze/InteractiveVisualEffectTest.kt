// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
class InteractiveVisualEffectTest : ContextTest() {

  @Test
  fun pointerObservation_isInstalledOnlyWhileRequested() = runComposeUiTest {
    val effect = RecordingInteractiveVisualEffect()
    setContent {
      Box(
        Modifier
          .size(100.dp)
          .testTag("glass")
          .hazeEffect { visualEffect = effect },
      )
    }

    onNodeWithTag("glass").performTouchInput { click(center) }
    assertThat(effect.pointerEvents).isEmpty()

    effect.observes = true
    waitForIdle()
    onNodeWithTag("glass").performTouchInput { click(center) }
    assertThat(effect.pointerEvents).isNotEmpty()

    effect.observes = false
    waitForIdle()
    val count = effect.pointerEvents.size
    onNodeWithTag("glass").performTouchInput { click(center) }
    assertThat(effect.pointerEvents.size).isEqualTo(count)
    assertThat(effect.sawConsumedChange).isFalse()
  }

  @Test
  fun pointerObservation_cancelledWhenNodeLeavesComposition() = runComposeUiTest {
    val effect = RecordingInteractiveVisualEffect().apply { observes = true }
    var shown by mutableStateOf(true)
    setContent {
      if (shown) {
        Box(
          Modifier
            .size(100.dp)
            .testTag("glass")
            .hazeEffect { visualEffect = effect },
        )
      }
    }

    onNodeWithTag("glass").performTouchInput { down(center) }
    shown = false
    waitForIdle()

    assertThat(effect.cancelCalls).isEqualTo(1)
  }

  @Test
  fun pointerObservation_cancelledWhenEffectIsReplaced() = runComposeUiTest {
    val effect = RecordingInteractiveVisualEffect().apply { observes = true }
    var currentEffect: VisualEffect by mutableStateOf<VisualEffect>(effect)
    setContent {
      Box(
        Modifier
          .size(100.dp)
          .testTag("glass")
          .hazeEffect { visualEffect = currentEffect },
      )
    }

    onNodeWithTag("glass").performTouchInput { down(center) }
    currentEffect = VisualEffect.Empty
    waitForIdle()
    val eventCount = effect.pointerEvents.size
    onNodeWithTag("glass").performTouchInput { up() }

    assertThat(effect.cancelCalls).isEqualTo(1)
    assertThat(effect.pointerEvents.size).isEqualTo(eventCount)
  }

  @Test
  fun materialAndContentTransform_scalesFinalGroupWithoutChangingBounds() = runComposeUiTest {
    val effect = RecordingInteractiveVisualEffect().apply {
      transform = VisualEffectTransform(0.5f, 0.5f, Offset(50f, 50f))
    }
    setContent {
      Box(Modifier.size(100.dp).background(Color.Black)) {
        Box(
          Modifier
            .fillMaxSize()
            .testTag("glass")
            .hazeEffect {
              drawContentBehind = true
              visualEffect = effect
            }
            .background(Color.Red),
        )
      }
    }

    val pixels = onNodeWithTag("glass").captureToImage().toPixelMap()
    assertThat(pixels[50, 50]).isEqualTo(Color.Red)
    assertThat(pixels[5, 5]).isEqualTo(Color.Black)
    onNodeWithTag("glass").assertWidthIsEqualTo(100.dp).assertHeightIsEqualTo(100.dp)
  }
}

private class RecordingInteractiveVisualEffect : InteractiveVisualEffect {
  var observes by mutableStateOf(false)
  var transform: VisualEffectTransform = VisualEffectTransform.Identity
  val pointerEvents = mutableListOf<PointerEvent>()
  var cancelCalls = 0
  var sawConsumedChange = false

  override val observesPointerEvents: Boolean get() = observes

  override fun onPointerEvent(event: PointerEvent, context: VisualEffectContext) {
    pointerEvents += event
    sawConsumedChange = sawConsumedChange || event.changes.any { it.isConsumed }
  }

  override fun onCancelPointerInput(context: VisualEffectContext) {
    cancelCalls++
  }

  override fun currentContentTransform(context: VisualEffectContext): VisualEffectTransform {
    return transform
  }

  override fun shouldDrawContentBehind(context: VisualEffectContext): Boolean = true

  override fun DrawScope.draw(context: VisualEffectContext) = Unit
}
