// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class HazeAreaChangeCountTest {

  @Test
  fun changeCount_incrementsForEachChangedPlacementValue() {
    val area = HazeArea()
    val initial = area.changeCount

    area.size = Size(10f, 10f)
    area.zIndex = 1f
    area.key = "key"
    area.windowId = Any()
    area.coordinates.localPosition = Offset(1f, 2f)
    area.coordinates.screenPosition = Offset(3f, 4f)

    assertThat(area.changeCount).isEqualTo(initial + 6)
  }

  @Test
  fun changeCount_ignoresWritesOfTheSameValue() {
    val area = HazeArea()
    val window = Any()
    area.size = Size(10f, 10f)
    area.zIndex = 1f
    area.key = "key"
    area.windowId = window
    area.coordinates.localPosition = Offset(1f, 2f)
    area.coordinates.screenPosition = Offset(3f, 4f)
    val settled = area.changeCount

    area.size = Size(10f, 10f)
    area.zIndex = 1f
    area.key = "key"
    area.windowId = window
    area.coordinates.localPosition = Offset(1f, 2f)
    area.coordinates.screenPosition = Offset(3f, 4f)

    assertThat(area.changeCount).isEqualTo(settled)
  }
}
