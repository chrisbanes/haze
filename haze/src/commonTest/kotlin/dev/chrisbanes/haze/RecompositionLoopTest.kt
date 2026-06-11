// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import assertk.assertThat
import assertk.assertions.isNotNull
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalTestApi::class)
class RecompositionLoopTest : ContextTest() {

  companion object {
    private const val IDLE_TIMEOUT_MS = 1000L
  }

  private suspend fun ComposeUiTest.awaitIdleWithTimeout(description: String) {
    try {
      withTimeout(IDLE_TIMEOUT_MS) {
        waitForIdle()
      }
    } catch (e: TimeoutCancellationException) {
      throw AssertionError(
        "Infinite recomposition loop detected $description. " +
          "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
        e,
      )
    }
  }

  @Test
  fun positionStrategyMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        GradientBox(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    waitForIdle()

    hazeState.positionStrategy = HazePositionStrategy.Screen

    awaitIdleWithTimeout("after positionStrategy mutation")
  }

  @Test
  fun addingSourceNode_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(false)

    setContent {
      if (showSource.value) {
        GradientBox(Modifier.hazeSource(hazeState).size(50.dp))
      }
      GradientBox(Modifier.hazeEffect(hazeState).size(100.dp))
    }
    waitForIdle()

    showSource.value = true

    awaitIdleWithTimeout("after adding source node")
  }

  @Test
  fun removingSourceNode_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(true)

    setContent {
      if (showSource.value) {
        GradientBox(Modifier.hazeSource(hazeState).size(50.dp))
      }
      GradientBox(Modifier.hazeEffect(hazeState).size(100.dp))
    }
    waitForIdle()

    showSource.value = false

    awaitIdleWithTimeout("after removing source node")
  }

  @Test
  fun openingDialogWithSharedStateAndHostEffect_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val showDialog = mutableStateOf(false)
    val detector = LivelockDetector()
    val hostEffect = LoopDetectingVisualEffect(detector::onUpdate)
    val dialogEffect = LoopDetectingVisualEffect(detector::onUpdate)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        GradientBox(
          Modifier
            .hazeEffect(hazeState) {
              visualEffect = hostEffect
            }
            .size(100.dp),
        )
      }

      if (showDialog.value) {
        Dialog(onDismissRequest = {}) {
          GradientBox(
            Modifier
              .hazeEffect(hazeState) {
                visualEffect = dialogEffect
              }
              .size(100.dp),
          )
        }
      }
    }
    waitForIdle()

    detector.arm()
    showDialog.value = true

    awaitIdleWithTimeout("after opening dialog with shared HazeState")
  }

  @Test
  fun blurEffectBlockMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val drawBehind = mutableStateOf(false)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        GradientBox(
          Modifier
            .hazeEffect(hazeState) {
              drawContentBehind = drawBehind.value
            }
            .size(100.dp),
        )
      }
    }
    waitForIdle()

    drawBehind.value = true

    awaitIdleWithTimeout("after blur effect block mutation")
  }

  @Test
  fun rapidAlternatingMutations_doNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val flag = mutableStateOf(false)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        GradientBox(
          Modifier
            .hazeEffect(hazeState) {
              drawContentBehind = flag.value
            }
            .size(100.dp),
        )
      }
    }
    waitForIdle()

    // Alternate quickly to mimic interactive toggles and catch feedback loops.
    repeat(5) {
      flag.value = !flag.value
      awaitIdleWithTimeout("on alternating mutation #$it")
    }
  }

  @Test
  fun lazyColumnScroll_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val listState = androidx.compose.foundation.lazy.LazyListState()

    setContent {
      Box(Modifier.fillMaxSize()) {
        LazyColumn(
          state = listState,
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
        ) {
          items(50) { index ->
            GradientBox(
              Modifier
                .fillMaxWidth()
                .height(80.dp),
            )
          }
        }

        GradientBox(
          Modifier
            .hazeEffect(hazeState)
            .fillMaxWidth()
            .height(56.dp),
        )
      }
    }
    waitForIdle()

    // Programmatic scroll gives a deterministic trigger for loop detection.
    listState.scrollToItem(25)

    awaitIdleWithTimeout("after LazyColumn scroll")
  }

  @Test
  fun lazyColumnItemCountChange_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val itemCount = mutableIntStateOf(10)

    setContent {
      Box(Modifier.fillMaxSize()) {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
        ) {
          items(itemCount.intValue) { index ->
            GradientBox(
              Modifier
                .fillMaxWidth()
                .height(80.dp),
            )
          }
        }

        GradientBox(
          Modifier
            .hazeEffect(hazeState)
            .fillMaxWidth()
            .height(56.dp),
        )
      }
    }
    waitForIdle()

    // Large list growth can churn source area bookkeeping; should still settle.
    itemCount.intValue = 50

    awaitIdleWithTimeout("after LazyColumn item count change")
  }

  @Test
  fun livelockDetector_ignoresUpdatesBeforeArm() {
    val time = TestTimeSource()
    val detector = LivelockDetector(timeSource = time)

    // 50 tight updates before arm() must be ignored, no throw.
    repeat(50) {
      time += 1.milliseconds
      detector.onUpdate()
    }
  }

  @Test
  fun livelockDetector_doesNotThrow_forBurstBelowThreshold() {
    val time = TestTimeSource()
    val detector = LivelockDetector(timeSource = time)
    detector.arm()

    // 50 updates within the window is below the 100 threshold.
    repeat(50) {
      time += 5.milliseconds
      detector.onUpdate()
    }
  }

  @Test
  fun livelockDetector_throws_whenBurstExceedsThreshold() {
    val time = TestTimeSource()
    val detector = LivelockDetector(timeSource = time)
    detector.arm()

    // 100 updates within 500ms — at the threshold, no throw.
    repeat(100) {
      time += 2.milliseconds
      detector.onUpdate()
    }
    // The 101st update throws.
    val ex = runCatching {
      time += 2.milliseconds
      detector.onUpdate()
    }.exceptionOrNull()
    assertThat(ex).isNotNull()
  }

  @Test
  fun livelockDetector_doesNotThrow_whenIdleGapEvictsWindow() {
    val time = TestTimeSource()
    val detector = LivelockDetector(window = 100.milliseconds, maxUpdatesPerWindow = 10, timeSource = time)
    detector.arm()

    // 8 updates in 50ms — within threshold.
    repeat(8) {
      time += 5.milliseconds
      detector.onUpdate()
    }
    // Long idle gap evicts the window.
    time += 200.milliseconds
    detector.onUpdate()
    // 8 more updates — still within threshold because the window evicted.
    repeat(8) {
      time += 5.milliseconds
      detector.onUpdate()
    }
  }

  @Test
  fun livelockDetector_armClearsWindow() {
    val time = TestTimeSource()
    val detector = LivelockDetector(timeSource = time)
    detector.arm()
    repeat(50) {
      time += 2.milliseconds
      detector.onUpdate()
    }
    // Re-arm: window resets to empty.
    detector.arm()
    // 50 fresh updates should not throw.
    repeat(50) {
      time += 2.milliseconds
      detector.onUpdate()
    }
  }
}

@Composable
private fun GradientBox(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.background(
      Brush.linearGradient(
        colors = listOf(
          Color(0xFF6B73FF),
          Color(0xFF9B59B6),
          Color(0xFFE74C3C),
          Color(0xFFF39C12),
        ),
      ),
    ),
  )
}

private class LoopDetectingVisualEffect(
  private val onUpdate: () -> Unit,
) : VisualEffect {
  override fun update(context: VisualEffectContext) {
    onUpdate()
  }

  override fun DrawScope.draw(context: VisualEffectContext) = Unit
}

/**
 * Detects a recomposition livelock: a sustained burst of `update()` calls with no idle gap.
 *
 * The detector tracks a sliding window of recent update timestamps. A livelock is flagged when
 * [maxUpdatesPerWindow] or more updates arrive within [window] of each other.
 *
 * This is meaningfully stronger than a simple update-count cap: a burst of 50 updates during
 * dialog placement is normal (gaps within the window), but a continuous stream of 200 updates
 * within 500ms is not.
 *
 * The [timeSource] is injected so the detector can be unit-tested without sleeping. Production
 * callers should pass [TimeSource.Monotonic], which is the only choice suitable for measuring
 * elapsed time (wall clocks can jump backwards).
 */
internal class LivelockDetector(
  private val window: Duration = 500.milliseconds,
  private val maxUpdatesPerWindow: Int = 100,
  private val timeSource: TimeSource = TimeSource.Monotonic,
) {
  private var armed = false
  private val recentUpdates = ArrayDeque<TimeMark>()

  fun arm() {
    armed = true
    recentUpdates.clear()
  }

  fun onUpdate() {
    if (!armed) return
    val now = timeSource.markNow()
    recentUpdates.addLast(now)
    // Drop entries older than the window from the head.
    while (recentUpdates.isNotEmpty() && recentUpdates.first().elapsedNow() > window) {
      recentUpdates.removeFirst()
    }
    if (recentUpdates.size > maxUpdatesPerWindow) {
      throw AssertionError(
        "Livelock detected: VisualEffect.update() fired ${recentUpdates.size} times " +
          "within $window (threshold = $maxUpdatesPerWindow).",
      )
    }
  }
}
