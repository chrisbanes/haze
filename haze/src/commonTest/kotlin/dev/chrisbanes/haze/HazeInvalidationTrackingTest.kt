// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEmpty
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, InternalHazeApi::class)
class HazeInvalidationTrackingTest : ContextTest() {

  @Test
  fun rendererRequestedInvalidateDraw_recordsTaggedEffectDrawInvalidation() = runComposeUiTest {
    val hazeState = HazeState()
    val factory = InvalidatingRendererFactory()
    val shouldInvalidate = mutableStateOf(false)

    withHazeInvalidationTracking {
      setContent {
        Box(Modifier.hazeSource(hazeState).size(100.dp)) {
          Spacer(
            Modifier
              .hazeInvalidationTag("effect")
              .hazeEffect(
                factory = factory,
                input = HazeInput.Sources(hazeState),
                style = shouldInvalidate.value,
              )
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
      }
    }
  }

  @Test
  fun noActiveRecorder_doesNotStoreInvalidationEvents() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(false)

    setContent {
      if (showSource.value) {
        Spacer(Modifier.hazeSource(hazeState).size(50.dp))
      }
      Spacer(
        Modifier
          .hazeInvalidationTag("effect")
          .testHazeEffect(hazeState)
          .size(100.dp),
      )
    }
    waitForIdle()

    showSource.value = true
    waitForIdle()

    assertThat(hazeInvalidationEvents()).isEmpty()
  }

  @Test
  fun addingAndRemovingSourceNode_recordsBoundedTaggedEffectInvalidations() = runComposeUiTest {
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
            .testHazeEffect(hazeState)
            .size(100.dp),
        )
      }
      waitForIdle()

      clearHazeInvalidations()
      showSource.value = true
      waitForIdle()
      assertHazeInvalidations("effect") {
        drawInvalidationsAtMost(1)
      }

      clearHazeInvalidations()
      showSource.value = false
      waitForIdle()
      assertHazeInvalidations("effect") {
        drawInvalidationsAtMost(1)
      }
    }
  }

  @Test
  fun multipleSimultaneousSourceChanges_recordsBoundedTaggedEffectInvalidations() =
    runComposeUiTest {
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
              .testHazeEffect(hazeState)
              .size(100.dp),
          )
        }
        waitForIdle()

        clearHazeInvalidations()
        showSources.value = true
        waitForIdle()

        assertHazeInvalidations("effect") {
          drawInvalidationsAtMost(1)
        }
      }
    }
}

private class InvalidatingRendererFactory : HazeEffectFactory<Boolean> {
  override fun createRenderer(): HazeEffectRenderer<Boolean> = InvalidatingRenderer()
}

@OptIn(InternalHazeApi::class)
private class InvalidatingRenderer :
  HazeEffectRenderer<Boolean>,
  HazeEffectRendererLifecycle<Boolean> {
  override fun update(
    scope: HazeEffectLifecycleScope,
    style: Boolean,
    sampling: HazeSampling,
  ) {
    if (style) {
      scope.invalidateDraw()
    }
  }

  override fun HazeEffectDrawScope.draw(style: Boolean) = Unit
}
