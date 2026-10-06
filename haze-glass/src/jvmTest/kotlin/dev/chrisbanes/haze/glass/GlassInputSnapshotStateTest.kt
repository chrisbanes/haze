// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GlassInputSnapshotStateTest {
  @Test fun compatibleFreshness_pendingAndReadyCoalesceUntilSuccessfulDraw() = runTest {
    val released = mutableListOf<String>()
    val state = GlassInputSnapshotState<Pair<Int, Int>, String>(
      compatible = { old, new -> old.second == new.second },
      release = released::add,
    )
    val completion = CompletableDeferred<String>()
    var captures = 0
    state.update(1 to 391)
    state.capture(this, {
      captures++
      completion.await()
    }, {})
    state.update(2 to 391)
    state.update(3 to 391)
    assertThat(state.isCapturing).isTrue()
    completion.complete("first")
    runCurrent()
    val ready = checkNotNull(state.ready)
    assertThat(ready.key).isEqualTo(1 to 391)
    state.update(4 to 391)
    assertThat(state.ready).isSameInstanceAs(ready)
    assertThat(state.needsCapture()).isFalse()
    state.capture(this, {
      captures++
      "must not overwrite"
    }, {})
    assertThat(captures).isEqualTo(1)
    state.didDraw(ready)
    assertThat(state.needsCapture()).isTrue()
    state.capture(this, {
      captures++
      "latest"
    }, {})
    assertThat(state.ready?.key).isEqualTo(4 to 391)
    assertThat(state.displayed).isSameInstanceAs(ready)
    state.didDraw(checkNotNull(state.ready))
    assertThat(state.displayed?.key).isEqualTo(4 to 391)
    assertThat(released).isEqualTo(listOf("first"))
    assertThat(captures).isEqualTo(2)
    assertThat(state.needsCapture()).isFalse()
  }

  @Test fun incompatibleGeometry_afterCoalescingRejectsLateCompletion() = runTest {
    val released = mutableListOf<String>()
    val state = GlassInputSnapshotState<Pair<Int, Int>, String>(
      compatible = { old, new -> old.second == new.second },
      release = released::add,
    )
    val completion = CompletableDeferred<String>()
    state.update(1 to 391)
    state.capture(this, { withContext(NonCancellable) { completion.await() } }, {})
    state.update(2 to 391)
    state.update(3 to 688)
    completion.complete("stale crop")
    runCurrent()
    assertThat(state.ready).isNull()
    assertThat(released).isEqualTo(listOf("stale crop"))
    assertThat(state.needsCapture()).isTrue()
  }

  @Test fun incompatibleReady_discardedWithoutReplacingDisplayedFrame() = runTest {
    val released = mutableListOf<String>()
    val state = GlassInputSnapshotState<Pair<Int, Int>, String>(
      compatible = { old, new -> old.second == new.second },
      release = released::add,
    )
    state.update(1 to 391)
    state.capture(this, { "displayed" }, {})
    val displayed = checkNotNull(state.ready)
    state.didDraw(displayed)
    state.update(2 to 391)
    state.capture(this, { "undrawn" }, {})
    state.update(3 to 688)
    assertThat(state.ready).isNull()
    assertThat(state.displayed).isSameInstanceAs(displayed)
    assertThat(released).isEqualTo(listOf("undrawn"))
    state.update(null)
    assertThat(state.displayed).isSameInstanceAs(displayed)
  }

  @Test fun suspendedCompletion_closesCandidateAndSchedulesPresentationOnOwnerThread() = runTest {
    Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
      val gate = CompletableDeferred<String>()
      val done = CompletableDeferred<Unit>()
      val state = GlassInputSnapshotState<Int, String> { }
      val ownerScope = CoroutineScope(coroutineContext + dispatcher)
      var owner: Thread? = null
      var finished: Thread? = null
      var settled: Thread? = null
      withContext(dispatcher) {
        owner = Thread.currentThread()
        state.update(1)
        state.capture(ownerScope, { gate.await() }, {
          settled = Thread.currentThread()
          done.complete(Unit)
        }, { finished = Thread.currentThread() })
      }
      gate.complete("ready")
      done.await()
      assertThat(finished).isSameInstanceAs(owner)
      assertThat(settled).isSameInstanceAs(owner)
      withContext(dispatcher) { state.clear() }
    }
  }

  @Test fun cancelledOwner_releasesCandidateWithoutRunningCapture() = runTest {
    val state = GlassInputSnapshotState<Int, String> { }
    val owner = Job().apply { cancel() }
    var finished = 0
    var captures = 0
    state.update(1)
    state.capture(CoroutineScope(coroutineContext + owner), {
      captures++
      "unexpected"
    }, {}, { finished++ })
    runCurrent()
    assertThat(captures).isEqualTo(0)
    assertThat(finished).isEqualTo(1)
    assertThat(state.isCapturing).isFalse()
    assertThat(state.frame).isNull()
  }

  @Test fun inlineCapture_isReadyUntilSuccessfulDraw() = runTest {
    val released = mutableListOf<String>()
    val state = GlassInputSnapshotState<Int, String>(release = released::add)
    var redraws = 0
    state.update(1)
    state.capture(this, { "red" }, { redraws++ })
    assertThat(state.isCapturing).isFalse()
    assertThat(state.displayed).isNull()
    assertThat(state.frame?.value).isEqualTo("red")
    assertThat(redraws).isEqualTo(1)
    state.didDraw(checkNotNull(state.frame))
    assertThat(state.displayed?.value).isEqualTo("red")
    assertThat(state.needsCapture()).isFalse()
    assertThat(released.size).isEqualTo(0)
  }

  @Test fun delayedFirstFrame_hasNoLiveFallback() = runTest {
    val state = GlassInputSnapshotState<Int, String> { }
    val completion = CompletableDeferred<String>()
    state.update(1)
    state.capture(this, { completion.await() }, { })
    assertThat(state.frame).isNull()
    assertThat(state.isCapturing).isTrue()
    completion.complete("red")
    runCurrent()
    assertThat(state.frame?.value).isEqualTo("red")
    assertThat(state.displayed).isNull()
  }

  @Test fun readyButUndrawn_sourceLossDiscardsIt() = runTest {
    val released = mutableListOf<String>()
    val state = GlassInputSnapshotState<Int, String>(release = released::add)
    state.update(1)
    state.capture(this, { "undrawn" }, { })
    state.update(null)
    assertThat(state.frame).isNull()
    assertThat(released).isEqualTo(listOf("undrawn"))
  }

  @Test fun pendingReplacement_keepsDisplayedGeometryAndPublishesOnlyAfterDraw() = runTest {
    data class Key(val revision: Int, val crop: Int, val scale: Float, val background: Int)
    val released = mutableListOf<String>()
    val state = GlassInputSnapshotState<Key, String>(release = released::add)
    val oldKey = Key(1, 391, 1f, 0)
    state.update(oldKey)
    state.capture(this, { "red" }, { })
    val old = checkNotNull(state.frame)
    state.didDraw(old)
    state.update(Key(2, 688, .5f, 1))
    val completion = CompletableDeferred<String>()
    state.capture(this, { completion.await() }, { })
    assertThat(state.frame).isSameInstanceAs(old)
    assertThat(state.frame?.key).isEqualTo(oldKey)
    completion.complete("green")
    runCurrent()
    assertThat(state.displayed).isSameInstanceAs(old)
    state.didDraw(checkNotNull(state.ready))
    assertThat(state.displayed?.value).isEqualTo("green")
    assertThat(released).isEqualTo(listOf("red"))
  }

  @Test fun staleNonCancellableCompletion_cannotPublishOrOverlapNextCapture() = runTest {
    val released = mutableListOf<String>()
    val state = GlassInputSnapshotState<Int, String>(release = released::add)
    val completion = CompletableDeferred<String>()
    state.update(1)
    state.capture(this, { withContext(NonCancellable) { completion.await() } }, { })
    state.update(2)
    assertThat(state.needsCapture()).isFalse()
    completion.complete("stale")
    runCurrent()
    assertThat(state.frame).isNull()
    assertThat(released).isEqualTo(listOf("stale"))
    assertThat(state.needsCapture()).isTrue()
    state.capture(this, { "latest" }, { })
    assertThat(state.frame?.value).isEqualTo("latest")
  }

  @Test fun failure_doesNotPublishOrRetryLoopAndNewRevisionMakesProgress() = runTest {
    val state = GlassInputSnapshotState<Int, String> { }
    var captures = 0
    var redraws = 0
    state.update(1)
    state.capture(this, {
      captures++
      error("capture failed")
    }, { redraws++ })
    repeat(10) {
      state.update(1)
      state.capture(this, {
        captures++
        "unexpected"
      }, { redraws++ })
    }
    assertThat(captures).isEqualTo(1)
    assertThat(redraws).isEqualTo(1)
    assertThat(state.frame).isNull()
    state.update(2)
    state.capture(this, { "latest" }, { })
    assertThat(state.frame?.value).isEqualTo("latest")
  }

  @Test fun clearTrimDetach_releasesDisplayedAndReadyOnceAndRejectsLateCompletion() = runTest {
    val released = mutableListOf<String>()
    val state = GlassInputSnapshotState<Int, String>(release = released::add)
    state.update(1)
    state.capture(this, { "displayed" }, { })
    state.didDraw(checkNotNull(state.frame))
    state.update(2)
    state.capture(this, { "ready" }, { })
    state.clear()
    state.clear()
    assertThat(released).isEqualTo(listOf("ready", "displayed"))
    state.update(3)
    val completion = CompletableDeferred<String>()
    state.capture(this, { withContext(NonCancellable) { completion.await() } }, { })
    state.clear()
    completion.complete("late")
    runCurrent()
    assertThat(state.frame).isNull()
    assertThat(released).isEqualTo(listOf("ready", "displayed", "late"))
  }

  @Test fun rapidChanges_areCoalescedToLatestAndIdleDoesNotCapture() = runTest {
    val state = GlassInputSnapshotState<Int, String> { }
    val completion = CompletableDeferred<String>()
    var captures = 0
    state.update(1)
    state.capture(this, {
      captures++
      withContext(NonCancellable) { completion.await() }
    }, { })
    repeat(100) { state.update(it + 2) }
    assertThat(state.isCapturing).isTrue()
    completion.complete("stale")
    runCurrent()
    state.capture(this, {
      captures++
      "latest"
    }, { })
    state.didDraw(checkNotNull(state.frame))
    repeat(100) {
      state.update(101)
      state.capture(this, {
        captures++
        "loop"
      }, { })
    }
    assertThat(captures).isEqualTo(2)
    assertThat(state.displayed?.key).isEqualTo(101)
  }
}
