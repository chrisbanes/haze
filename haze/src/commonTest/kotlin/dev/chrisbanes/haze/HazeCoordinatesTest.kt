// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

@OptIn(ExperimentalHazeApi::class)
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

  @Test
  fun boundsFor_returnsRect_whenPositionAndSizeSpecified() {
    val coords = HazeCoordinates()
    coords.localPosition = Offset(10f, 20f)
    coords.screenPosition = Offset(100f, 200f)

    val bounds = coords.boundsFor(HazePositionStrategy.Local, Size(50f, 60f))

    assertThat(bounds).isEqualTo(Rect(Offset(10f, 20f), Size(50f, 60f)))
  }

  @Test
  fun boundsFor_returnsRect_usingScreenPosition_whenScreenStrategy() {
    val coords = HazeCoordinates()
    coords.localPosition = Offset(10f, 20f)
    coords.screenPosition = Offset(100f, 200f)

    val bounds = coords.boundsFor(HazePositionStrategy.Screen, Size(50f, 60f))

    assertThat(bounds).isEqualTo(Rect(Offset(100f, 200f), Size(50f, 60f)))
  }

  @Test
  fun boundsFor_returnsNull_whenPositionUnspecified() {
    val coords = HazeCoordinates()
    // localPosition and screenPosition default to Offset.Unspecified

    val bounds = coords.boundsFor(HazePositionStrategy.Local, Size(50f, 60f))

    assertThat(bounds).isNull()
  }

  @Test
  fun boundsFor_returnsNull_whenSizeUnspecified() {
    val coords = HazeCoordinates()
    coords.localPosition = Offset(10f, 20f)

    val bounds = coords.boundsFor(HazePositionStrategy.Local, Size.Unspecified)

    assertThat(bounds).isNull()
  }
}
