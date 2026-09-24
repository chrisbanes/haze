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
import assertk.assertions.isGreaterThan
import assertk.assertions.isNull
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceMetadata
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import java.lang.ref.WeakReference
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HazeBlurSelectionLeakTest : ContextTest() {
  @Test
  fun refinedSelection_releasesDetachedBlur() {
    var probeRef: WeakReference<SelectionProbe>? = null
    runComposeUiTest {
      val state = HazeState()
      val showScreen = mutableStateOf(true)
      val visibility = mutableStateOf(1f)

      setContent {
        if (showScreen.value) {
          val probe = remember { SelectionProbe().also { probeRef = WeakReference(it) } }
          val selection = remember(probe) {
            HazeSourceSelection.Behind.where(probe::matches)
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
      waitForIdle()
      assertThat(checkNotNull(checkNotNull(probeRef).get()).matchesCount).isGreaterThan(0)

      visibility.value = 0.5f
      waitForIdle()
      showScreen.value = false
      waitForIdle()
    }

    val ref = checkNotNull(probeRef)
    for (attempt in 1..10) {
      System.gc()
      if (ref.get() == null) break
    }
    assertThat(ref.get()).isNull()
  }
}

private class SelectionProbe {
  var matchesCount = 0
    private set

  fun matches(metadata: HazeSourceMetadata): Boolean {
    matchesCount++
    return metadata.zIndex < 0.5f
  }
}
