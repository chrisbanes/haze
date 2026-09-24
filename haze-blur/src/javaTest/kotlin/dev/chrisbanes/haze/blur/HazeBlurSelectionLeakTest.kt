// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HazeBlurSelectionLeakTest : ContextTest() {
  @Test
  fun refinedSelection_unregistersObserversAfterDetach() = runComposeUiTest {
    val state = HazeState()
    val showScreen = mutableStateOf(false)
    val visibility = mutableStateOf(1f)
    var matchesCount = 0

    setContent {
      if (showScreen.value) {
        val selection = remember {
          HazeSourceSelection.Behind.where {
            matchesCount++
            it.zIndex < 0.5f
          }
        }

        Box(Modifier.size(100.dp)) {
          LazyColumn(Modifier.size(100.dp).hazeSource(state)) {
            items(20) { Box(Modifier.size(100.dp)) }
          }
          Box(
            Modifier
              .size(100.dp)
              .hazeSource(state, zIndex = 0.5f)
              .graphicsLayer { alpha = visibility.value }
              .hazeBlur(
                input = HazeInput.Sources(state, selection),
                style = HazeBlurStyle { blurRadius(8.dp) },
              ),
          )
        }
      }
    }
    val baselineObservers = runOnIdle { registeredApplyObservers() }

    repeat(3) { cycle ->
      runOnIdle {
        matchesCount = 0
        visibility.value = 1f
        showScreen.value = true
      }
      runOnIdle { assertThat(matchesCount).isGreaterThan(0) }

      runOnIdle { visibility.value = 0.5f }
      runOnIdle { showScreen.value = false }
      runOnIdle {
        val remainingObservers = registeredApplyObservers().filter { observer ->
          baselineObservers.none { it === observer }
        }
        assertThat(remainingObservers, "Observers remaining after detach in cycle $cycle").isEmpty()
      }
    }
  }
}

// The leak is a registration in Compose's global apply-observer list. Inspect it directly
// rather than relying on GC timing or callbacks, which detached nodes already suppress.
// Keep this Compose-internal dependency isolated here so runtime upgrades fail explicitly.
private fun registeredApplyObservers(): List<*> {
  val snapshotClass = Class.forName("androidx.compose.runtime.snapshots.SnapshotKt")
  val lock = snapshotClass.getDeclaredField("lock").apply { isAccessible = true }.get(null)
  val observers = snapshotClass.getDeclaredField("applyObservers").apply { isAccessible = true }
  return synchronized(checkNotNull(lock)) { (observers.get(null) as List<*>).toList() }
}
