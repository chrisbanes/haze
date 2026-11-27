// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class HazeCoordinatesTest {

  @Test
  fun positionFor_returnsLocalPosition_forLocalStrategy() {
    val coords = HazeCoordinates()
    coords.localPosition = Offset(10f, 20f)
    coords.screenPosition = Offset(100f, 200f)

    val position = coords.positionFor(HazePositionStrategy.Local)

    assertThat(position).isEqualTo(Offset(10f, 20f))
  }

  @Test
  fun positionFor_returnsScreenPosition_forScreenStrategy() {
    val coords = HazeCoordinates()
    coords.localPosition = Offset(10f, 20f)
    coords.screenPosition = Offset(100f, 200f)

    val position = coords.positionFor(HazePositionStrategy.Screen)

    assertThat(position).isEqualTo(Offset(100f, 200f))
  }

  @Test
  fun positionFor_returnsLocalPosition_forAutoStrategy() {
    val coords = HazeCoordinates()
    coords.localPosition = Offset(10f, 20f)
    coords.screenPosition = Offset(100f, 200f)

    val position = coords.positionFor(HazePositionStrategy.Auto)

    assertThat(position).isEqualTo(Offset(10f, 20f))
  }
}
