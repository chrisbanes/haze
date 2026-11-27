// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isSameInstanceAs
import assertk.assertions.single
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HazeSourceReactivationTest {

  // Guards #1920: the source recovers only because onReset's invalidatePlacement() re-runs onPlaced.
  @Test
  fun reactivation_unchangedLayout_restoresSourceGeometry() = runComposeUiTest {
    val state = HazeState()
    val active = mutableStateOf(true)

    setContent {
      Row {
        Box(Modifier.size(50.dp))
        ReusableContentHost(active.value) {
          Box(
            Modifier
              .size(50.dp)
              .hazeSource(state)
              .background(Color.Blue),
          )
        }
      }
    }
    waitForIdle()

    val area = state.areas.single()
    val localPosition = area.coordinates.localPosition
    val screenPosition = area.coordinates.screenPosition
    val size = area.size
    assertThat(localPosition.x).isGreaterThan(0f)
    assertThat(screenPosition).isNotEqualTo(Offset.Unspecified)
    assertThat(size).isNotEqualTo(Size.Unspecified)

    runOnIdle { active.value = false }
    waitForIdle()

    assertThat(state.areas).doesNotContain(area)
    assertThat(area.size).isEqualTo(Size.Unspecified)

    runOnIdle { active.value = true }
    waitForIdle()

    assertThat(state.areas).single().isSameInstanceAs(area)
    assertThat(area.coordinates.localPosition).isEqualTo(localPosition)
    assertThat(area.coordinates.screenPosition).isEqualTo(screenPosition)
    assertThat(area.size).isEqualTo(size)
  }
}
