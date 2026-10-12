// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import kotlin.test.Test

class RetainedGraphicsLayerTest {

  @Test
  fun acquire_returnsTheSameLayerUntilReleased() {
    val context = RecordingGraphicsContext()
    val holder = RetainedGraphicsLayer()

    val first = holder.acquire(context)
    val second = holder.acquire(context)

    assertThat(second).isSameInstanceAs(first)
    assertThat(context.createdLayers).isEqualTo(1)
    assertThat(first.isReleased).isFalse()
  }

  @Test
  fun release_releasesOnceAndForgetsTheLayer() {
    val context = RecordingGraphicsContext()
    val holder = RetainedGraphicsLayer()
    val first = holder.acquire(context)

    holder.release()
    holder.release()

    assertThat(first.isReleased).isTrue()
    assertThat(context.releasedLayers).isEqualTo(1)
    assertThat(holder.layer).isNull()
    assertThat(holder.acquire(context)).isNotSameInstanceAs(first)
  }

  @Test
  fun acquire_afterTheLayerWasReleasedElsewhere_createsAnotherWithoutReleasingTwice() {
    val context = RecordingGraphicsContext()
    val holder = RetainedGraphicsLayer()
    val first = holder.acquire(context)
    context.releaseLatestLayer()

    val second = holder.acquire(context)

    assertThat(second).isNotSameInstanceAs(first)
    assertThat(context.releasedLayers).isEqualTo(1)
    assertThat(holder.layer).isNotNull().isSameInstanceAs(second)
  }

  @Test
  fun acquire_withAChangedKey_releasesTheOldLayerAndCreatesANewOne() {
    val context = RecordingGraphicsContext()
    val holder = RetainedGraphicsLayer()
    val first = holder.acquire(context, key = 1L)

    val second = holder.acquire(context, key = 2L)

    assertThat(second).isNotSameInstanceAs(first)
    assertThat(first.isReleased).isTrue()
    assertThat(second.isReleased).isFalse()
    assertThat(context.createdLayers).isEqualTo(2)
    assertThat(context.releasedLayers).isEqualTo(1)
  }

  @Test
  fun acquire_withTheSameKey_returnsTheSameLayer() {
    val context = RecordingGraphicsContext()
    val holder = RetainedGraphicsLayer()
    val first = holder.acquire(context, key = 7L)

    assertThat(holder.acquire(context, key = 7L)).isSameInstanceAs(first)
    assertThat(context.createdLayers).isEqualTo(1)
  }
}
