// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.test.ContextTest
import java.awt.Frame
import kotlin.test.Test
import kotlinx.coroutines.Job

/**
 * Measures the coroutines that a [HazeSourceNode] snapshot-apply observer launches, and the
 * snapshot-driven effect refreshes they produce, for a source feeding an effect in another window.
 *
 * Launches are the source node's queued child jobs, read straight after a burst: applies notify
 * observers synchronously while the launched coroutines wait on the test's main dispatcher.
 * Refreshes are the effect's [HazeEffectNode.inputCaptureGeneration] delta.
 */
@OptIn(ExperimentalTestApi::class, InternalHazeApi::class, ExperimentalComposeUiApi::class)
class HazeSourceSnapshotApplyChurnTest : ContextTest() {

  @Test
  fun unrelatedApplyBurst_launchesOncePerFrameAndRefreshesOnce() = churnTest {
    for (n in listOf(1, 10, 100)) {
      val (launches, refreshes) = measure { repeat(n) { applyUnrelated() } }
      assertThat(launches, "launches for $n applies").isEqualTo(1)
      assertThat(refreshes, "refreshes for $n applies").isEqualTo(1)
    }
  }

  @Test
  fun unrelatedGlobalWriteBurst_launchesOncePerFrameAndRefreshesOnce() = churnTest {
    val (launches, refreshes) = measure {
      repeat(100) {
        unrelated.intValue++
        Snapshot.sendApplyNotifications()
      }
    }
    assertThat(launches).isEqualTo(1)
    assertThat(refreshes).isEqualTo(1)
  }

  @Test
  fun unrelatedAppliesAcrossFrames_refreshOncePerFrame() = churnTest {
    val (_, refreshes) = measure {
      repeat(10) {
        applyUnrelated()
        test.mainClock.advanceTimeByFrame()
      }
    }
    assertThat(refreshes).isEqualTo(10)
  }

  @Test
  fun applyDuringQueuedLaunch_isCoveredByThatLaunch() = churnTest {
    val (_, refreshes) = measure {
      applyUnrelated()
      assertThat(source.job().children.count()).isEqualTo(1)
      applyUnrelated()
      assertThat(source.job().children.count()).isEqualTo(1)
    }
    assertThat(refreshes).isEqualTo(1)
  }

  @Test
  fun relevantDescendantLayerWrite_refreshesCrossWindowEffect() = churnTest {
    val (launches, refreshes) = measure { Snapshot.withMutableSnapshot { alpha.floatValue = 0.5f } }
    assertThat(launches).isEqualTo(1)
    assertThat(refreshes).isEqualTo(1)
  }

  @Test
  fun detachWithQueuedLaunches_cancelsSourceWork() = churnTest {
    // The queued launch may run before the removal applies, so this does not prove that detach
    // cancelled it; per-registration flag safety is structural (see enableSnapshotApplyObserver).
    val job = source.job()
    repeat(10) { applyUnrelated() }
    assertThat(job.children.count()).isEqualTo(1)

    present.value = false
    test.waitForIdle()

    assertThat(job.isActive).isFalse()
    assertThat(job.children.count()).isEqualTo(0)
  }

  @Test
  fun reattachAsNewNode_launchesAndRefreshesAgain() = churnTest {
    val old = source
    present.value = false
    test.waitForIdle()
    present.value = true
    test.waitForIdle()
    assertThat(source).isNotSameInstanceAs(old)

    val (launches, refreshes) = measure { repeat(10) { applyUnrelated() } }
    assertThat(launches).isEqualTo(1)
    assertThat(refreshes).isEqualTo(1)
  }

  @Test
  fun reusedSourceNode_launchesAndRefreshesAgain() = churnTest {
    repeat(10) { applyUnrelated() }
    val old = source
    active.value = false
    test.waitForIdle()
    active.value = true
    test.waitForIdle()
    assertThat(source).isSameInstanceAs(old)

    val (launches, refreshes) = measure { repeat(10) { applyUnrelated() } }
    assertThat(launches).isEqualTo(1)
    assertThat(refreshes).isEqualTo(1)
  }

  private fun churnTest(block: ChurnFixture.() -> Unit) = runComposeUiTest {
    val sourceWindow = createTestWindow("SourceWindow")
    val effectWindow = createTestWindow("EffectWindow")
    try {
      val fixture = ChurnFixture(this)
      setContent {
        CompositionLocalProvider(LocalAwtWindow provides sourceWindow) {
          if (fixture.present.value) {
            ReusableContentHost(fixture.active.value) {
              Box(
                Modifier
                  .size(100.dp)
                  .hazeSource(fixture.state)
                  .then(CaptureSourceNodeElement { fixture.capturedSource = it }),
              ) {
                Box(
                  Modifier
                    .size(50.dp)
                    .graphicsLayer { alpha = fixture.alpha.floatValue }
                    .background(Color.Red),
                )
              }
            }
          }
        }
        CompositionLocalProvider(LocalAwtWindow provides effectWindow) {
          Spacer(
            Modifier
              .size(100.dp)
              .testHazeEffect(fixture.state, HazeSourceSelection.All)
              .captureEffectNode { fixture.capturedEffect = it },
          )
        }
      }
      waitForIdle()
      fixture.block()
    } finally {
      (sourceWindow as? Frame)?.dispose()
      (effectWindow as? Frame)?.dispose()
    }
  }
}

@OptIn(ExperimentalTestApi::class)
private class ChurnFixture(val test: ComposeUiTest) {
  val state = HazeState()
  val unrelated = mutableIntStateOf(0)
  val alpha = mutableFloatStateOf(1f)
  val present = mutableStateOf(true)
  val active = mutableStateOf(true)
  var capturedSource: HazeSourceNode? = null
  var capturedEffect: HazeEffectNode? = null

  val source: HazeSourceNode get() = checkNotNull(capturedSource)
  val effect: HazeEffectNode get() = checkNotNull(capturedEffect)

  fun applyUnrelated() {
    Snapshot.withMutableSnapshot { unrelated.intValue++ }
  }

  /** Returns the coroutines [burst] launched on the source node, and the refreshes they caused. */
  fun measure(burst: () -> Unit): Pair<Int, Long> {
    test.waitForIdle()
    val job = source.job()
    assertThat(job.children.count(), "queued launches at idle").isEqualTo(0)
    val generation = effect.inputCaptureGeneration
    burst()
    val launches = job.children.count()
    test.waitForIdle()
    return launches to effect.inputCaptureGeneration - generation
  }
}

private fun HazeSourceNode.job(): Job = checkNotNull(coroutineScope.coroutineContext[Job])

private data class CaptureSourceNodeElement(
  val onSourceNode: (HazeSourceNode) -> Unit,
) : ModifierNodeElement<CaptureSourceNode>() {
  override fun create(): CaptureSourceNode = CaptureSourceNode(onSourceNode)

  override fun update(node: CaptureSourceNode) {
    node.onSourceNode = onSourceNode
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "captureSourceNode"
  }
}

private class CaptureSourceNode(var onSourceNode: (HazeSourceNode) -> Unit) : Modifier.Node() {
  override fun onAttach() {
    (findNearestAncestor(HazeTraversableNodeKeys.Source) as? HazeSourceNode)?.let(onSourceNode)
  }
}
