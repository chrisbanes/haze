// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class HazeAreaSelectionVersionTest {

  @Test
  fun selectionVersion_countsOnlyKeyAndZIndexChanges() {
    val area = HazeArea()
    val initial = area.selectionVersion

    area.size = Size(10f, 10f)
    area.windowId = Any()
    area.coordinates.localPosition = Offset(1f, 2f)
    area.coordinates.screenPosition = Offset(3f, 4f)
    assertThat(area.selectionVersion).isEqualTo(initial)

    area.key = "key"
    area.zIndex = 1f
    assertThat(area.selectionVersion).isEqualTo(initial + 2)

    area.key = "key"
    area.zIndex = 1f
    assertThat(area.selectionVersion).isEqualTo(initial + 2)
  }
}
