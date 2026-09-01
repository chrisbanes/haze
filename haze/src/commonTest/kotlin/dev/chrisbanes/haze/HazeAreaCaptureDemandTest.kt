// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class HazeAreaCaptureDemandTest {

  @Test
  fun captureDemand_tracksConsumersByIdentity() {
    val area = HazeArea()
    val firstConsumer = Any()
    val secondConsumer = Any()

    assertThat(area.hasCaptureDemand).isFalse()
    area.addCaptureConsumer(firstConsumer)
    area.addCaptureConsumer(firstConsumer)
    assertThat(area.captureConsumerCount).isEqualTo(1)

    area.addCaptureConsumer(secondConsumer)
    assertThat(area.captureConsumerCount).isEqualTo(2)
    assertThat(area.hasCaptureDemand).isTrue()

    area.removeCaptureConsumer(firstConsumer)
    assertThat(area.hasCaptureDemand).isTrue()
    area.removeCaptureConsumer(secondConsumer)
    assertThat(area.hasCaptureDemand).isFalse()
  }
}
