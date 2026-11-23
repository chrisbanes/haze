// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test

/** Background snapshot applies that overlap an effect's detach (#1837). */
@OptIn(ExperimentalTestApi::class)
class HazeSourceSelectionDetachRaceTest {

  @Test
  fun sourceSelection_backgroundApplyAfterRemoval_isIgnored() = runComposeUiTest {
    val selectedKey = mutableStateOf("first")
    val present = mutableStateOf(true)
    var predicateCalls = 0
    val selection = HazeSourceSelection.All.where { info ->
      predicateCalls++
      info.key == selectedKey.value
    }
    val state = HazeState()

    BackgroundApplyGate().use { gate ->
      setContent {
        Box(Modifier.size(100.dp)) {
          Box(Modifier.fillMaxSize().hazeSource(state, key = "first").background(Color.Red))
          if (present.value) {
            Box(Modifier.fillMaxSize().testHazeEffect(state, selection))
          }
        }
      }
      waitForIdle()
      assertThat(predicateCalls).isGreaterThan(0)

      val failure = gate.applyAcross(selectedKey) {
        runOnIdle { present.value = false }
        waitForIdle()
      }
      val callsAfterRemoval = predicateCalls
      waitForIdle()

      assertThat(failure).isNull()
      assertThat(predicateCalls).isEqualTo(callsAfterRemoval)
    }
  }

  @Test
  fun sourceSelection_backgroundApplyAcrossReactivation_leavesNewAttachmentAlone() = runComposeUiTest {
    val selectedKey = mutableStateOf("first")
    val active = mutableStateOf(true)
    var predicateCalls = 0
    val selection = HazeSourceSelection.All.where { info ->
      predicateCalls++
      info.key == selectedKey.value
    }
    val state = HazeState()

    BackgroundApplyGate().use { gate ->
      setContent {
        Box(Modifier.size(100.dp)) {
          Box(Modifier.fillMaxSize().hazeSource(state, key = "first").background(Color.Red))
          ReusableContentHost(active.value) {
            Box(Modifier.fillMaxSize().testHazeEffect(state, selection))
          }
        }
      }
      waitForIdle()

      var callsAfterReactivation = 0
      val failure = gate.applyAcross(selectedKey) {
        runOnIdle { active.value = false }
        waitForIdle()
        runOnIdle { active.value = true }
        waitForIdle()
        callsAfterReactivation = predicateCalls
      }
      waitForIdle()

      assertThat(failure).isNull()
      // The stale observer's notification must not rerun selection on the new attachment.
      assertThat(predicateCalls).isEqualTo(callsAfterReactivation)

      // The new attachment still observes selection reads.
      runOnIdle { selectedKey.value = "first" }
      waitForIdle()
      assertThat(predicateCalls).isGreaterThan(callsAfterReactivation)
    }
  }

  @Test
  fun sourceSelection_backgroundApplyWhileAttached_rerunsSelection() = runComposeUiTest {
    val selectedKey = mutableStateOf("first")
    var predicateCalls = 0
    val selection = HazeSourceSelection.All.where { info ->
      predicateCalls++
      info.key == selectedKey.value
    }
    val state = HazeState()

    BackgroundApplyGate().use { gate ->
      setContent {
        Box(Modifier.size(100.dp)) {
          Box(Modifier.fillMaxSize().hazeSource(state, key = "first").background(Color.Red))
          Box(Modifier.fillMaxSize().testHazeEffect(state, selection))
        }
      }
      waitForIdle()
      val callsBefore = predicateCalls

      val failure = gate.applyAcross(selectedKey) {}
      waitForIdle()

      assertThat(failure).isNull()
      assertThat(predicateCalls).isGreaterThan(callsBefore)
    }
  }
}

/**
 * Holds a background snapshot apply after its changes are committed but before the effect's
 * selection observer is notified. Apply observers run in registration order, so the gate must be
 * created before the effect starts observing.
 */
private class BackgroundApplyGate : AutoCloseable {
  private val applierName = "haze-background-apply"

  private val applying = CountDownLatch(1)
  private val release = CountDownLatch(1)

  private val handle = Snapshot.registerApplyObserver { _, _ ->
    if (Thread.currentThread().name == applierName) {
      applying.countDown()
      check(release.await(10, TimeUnit.SECONDS)) { "Timed out waiting for release" }
    }
  }

  /** Toggles [key] on a background thread, runs [whileHeld] during its apply, and returns any failure. Call once. */
  fun applyAcross(key: MutableState<String>, whileHeld: () -> Unit): Throwable? {
    var failure: Throwable? = null
    val applier = thread(name = applierName) {
      try {
        Snapshot.withMutableSnapshot { key.value = if (key.value == "first") "second" else "first" }
      } catch (t: Throwable) {
        failure = t
      }
    }
    try {
      check(applying.await(10, TimeUnit.SECONDS)) { "Background apply did not start" }
      whileHeld()
    } finally {
      release.countDown()
      applier.join(10_000)
    }
    check(!applier.isAlive) { "Background apply did not finish" }
    return failure
  }

  override fun close() = handle.dispose()
}
