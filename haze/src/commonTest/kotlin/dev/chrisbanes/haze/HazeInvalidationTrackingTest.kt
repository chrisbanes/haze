// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEmpty
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HazeInvalidationTrackingTest : ContextTest() {

  @Test
  fun positionStrategyChange_recordsOneTaggedEffectDrawInvalidation() = runComposeUiTest {
    val hazeState = HazeState()

    withHazeInvalidationTracking {
      setContent {
        Box(Modifier.hazeSource(hazeState).size(100.dp)) {
          Spacer(
            Modifier
              .hazeInvalidationTag("effect")
              .hazeEffect(hazeState)
              .size(100.dp),
          )
        }
      }
      waitForIdle()

      clearHazeInvalidations()

      hazeState.positionStrategy = HazePositionStrategy.Screen
      waitForIdle()

      assertHazeInvalidations("effect") {
        drawInvalidationsAtMost(1)
        layoutInvalidationsExactly(0)
      }
    }
  }

  @Test
  fun effectRequestedInvalidateDraw_recordsTaggedEffectDrawInvalidation() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = InvalidatingVisualEffect()
    val shouldInvalidate = mutableStateOf(false)

    withHazeInvalidationTracking {
      setContent {
        Box(Modifier.hazeSource(hazeState).size(100.dp)) {
          Spacer(
            Modifier
              .hazeInvalidationTag("effect")
              .hazeEffect(hazeState) {
                effect.shouldInvalidate = shouldInvalidate.value
                visualEffect = effect
              }
              .size(100.dp),
          )
        }
      }
      waitForIdle()

      clearHazeInvalidations()

      shouldInvalidate.value = true
      waitForIdle()

      assertHazeInvalidations("effect") {
        drawInvalidationsExactly(1)
        layoutInvalidationsExactly(0)
      }
    }
  }

  @Test
  fun noActiveRecorder_doesNotStoreInvalidationEvents() = runComposeUiTest {
    val hazeState = HazeState()

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        Spacer(
          Modifier
            .hazeInvalidationTag("effect")
            .hazeEffect(hazeState)
            .size(100.dp),
        )
      }
    }
    waitForIdle()

    hazeState.positionStrategy = HazePositionStrategy.Screen
    waitForIdle()

    assertThat(hazeInvalidationEvents()).isEmpty()
  }

  @Test
  fun addingSourceNode_recordsBoundedTaggedEffectInvalidations() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(false)

    withHazeInvalidationTracking {
      setContent {
        if (showSource.value) {
          Spacer(Modifier.hazeSource(hazeState).size(50.dp))
        }
        Spacer(
          Modifier
            .hazeInvalidationTag("effect")
            .hazeEffect(hazeState)
            .size(100.dp),
        )
      }
      waitForIdle()

      clearHazeInvalidations()

      showSource.value = true
      waitForIdle()

      assertHazeInvalidations("effect") {
        drawInvalidationsAtMost(1)
        layoutInvalidationsExactly(0)
      }
    }
  }

  @Test
  fun removingSourceNode_recordsBoundedTaggedEffectInvalidations() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(true)

    withHazeInvalidationTracking {
      setContent {
        if (showSource.value) {
          Spacer(Modifier.hazeSource(hazeState).size(50.dp))
        }
        Spacer(
          Modifier
            .hazeInvalidationTag("effect")
            .hazeEffect(hazeState)
            .size(100.dp),
        )
      }
      waitForIdle()

      clearHazeInvalidations()

      showSource.value = false
      waitForIdle()

      assertHazeInvalidations("effect") {
        drawInvalidationsAtMost(1)
        layoutInvalidationsExactly(0)
      }
    }
  }

  @Test
  fun multipleSimultaneousSourceChanges_recordsBoundedTaggedEffectInvalidations() = runComposeUiTest {
    val hazeState = HazeState()
    val showSources = mutableStateOf(false)

    withHazeInvalidationTracking {
      setContent {
        if (showSources.value) {
          repeat(5) {
            Spacer(Modifier.hazeSource(hazeState).size(20.dp))
          }
        }
        Spacer(
          Modifier
            .hazeInvalidationTag("effect")
            .hazeEffect(hazeState)
            .size(100.dp),
        )
      }
      waitForIdle()

      clearHazeInvalidations()

      showSources.value = true
      waitForIdle()

      assertHazeInvalidations("effect") {
        drawInvalidationsAtMost(1)
        layoutInvalidationsExactly(0)
      }
    }
  }

  @Test
  fun effectBlockMutation_recordsBoundedTaggedEffectInvalidations() = runComposeUiTest {
    val hazeState = HazeState()
    val drawBehind = mutableStateOf(false)

    withHazeInvalidationTracking {
      setContent {
        Box(Modifier.hazeSource(hazeState).size(100.dp)) {
          Spacer(
            Modifier
              .hazeInvalidationTag("effect")
              .hazeEffect(hazeState) {
                drawContentBehind = drawBehind.value
              }
              .size(100.dp),
          )
        }
      }
      waitForIdle()

      clearHazeInvalidations()

      drawBehind.value = true
      waitForIdle()

      assertHazeInvalidations("effect") {
        drawInvalidationsAtMost(1)
        layoutInvalidationsExactly(0)
      }
    }
  }
}

private class InvalidatingVisualEffect : VisualEffect {
  var shouldInvalidate = false

  override fun update(context: VisualEffectContext) {
    if (shouldInvalidate) {
      shouldInvalidate = false
      context.invalidateDraw()
    }
  }

  override fun DrawScope.draw(context: VisualEffectContext) = Unit
}
