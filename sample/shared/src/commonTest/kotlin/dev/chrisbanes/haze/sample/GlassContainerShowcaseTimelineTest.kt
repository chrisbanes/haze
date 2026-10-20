// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.BroadcastFrameClock
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class GlassContainerShowcaseTimelineTest {
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun settleColor_retimesAnActiveReturnWhenSpeedChanges() = runTest {
    val clock = BroadcastFrameClock()
    val normal = GlassContainerShowcaseState().apply { dragColorBy(1f) }
    val slow = GlassContainerShowcaseState().apply {
      dragColorBy(1f)
      toggleSlowMotion()
    }
    val slowing = GlassContainerShowcaseState().apply { dragColorBy(1f) }
    val speeding = GlassContainerShowcaseState().apply {
      dragColorBy(1f)
      toggleSlowMotion()
    }
    val jobs = listOf(normal, slow, slowing, speeding).map { state -> launch(clock) { state.settleColor() } }
    fun frame(millis: Long) {
      clock.sendFrame(millis * 1_000_000)
      runCurrent()
    }
    runCurrent()
    frame(0)
    frame(32)
    val slowingOffset = slowing.colorOffset
    val speedingOffset = speeding.colorOffset
    slowing.toggleSlowMotion()
    speeding.toggleSlowMotion()
    assertThat(slowing.colorOffset).isEqualTo(slowingOffset)
    assertThat(speeding.colorOffset).isEqualTo(speedingOffset)
    for (millis in 48L..128L step 16) frame(millis)
    assertThat(slowing.colorOffset).isGreaterThan(normal.colorOffset + 0.01f)
    assertThat(speeding.colorOffset).isLessThan(slow.colorOffset - 0.01f)
    for (millis in 144L..2_000L step 16) frame(millis)
    jobs.forEach { it.join() }
    listOf(normal, slow, slowing, speeding).forEach { assertThat(it.colorOffset).isEqualTo(0f) }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun slowMotion_runsTheSameTimelineAtHalfSpeed() = runTest {
    val clock = BroadcastFrameClock()
    val normal = GlassContainerShowcaseState()
    val slow = GlassContainerShowcaseState().apply { toggleSlowMotion() }
    val normalJob = launch(clock) { normal.runAutoplayLoop() }
    val slowJob = launch(clock) { slow.runAutoplayLoop() }
    runCurrent()
    clock.sendFrame(0L)
    runCurrent()
    clock.sendFrame(3_000_000_000L)
    runCurrent()
    assertThat(normal.expanded).isTrue()
    assertThat(slow.expanded).isFalse()
    clock.sendFrame(6_000_000_000L)
    runCurrent()
    assertThat(normal.colorOffset).isEqualTo(1f)
    assertThat(slow.expanded).isTrue()
    assertThat(slow.colorOffset).isEqualTo(0f)
    normalJob.cancelAndJoin()
    slowJob.cancelAndJoin()
  }

  @Test
  fun glassContainerShowcaseFrame_holdsBetweenExpansionSplitAndCollapse() {
    assertThat(glassContainerShowcaseFrame(1f / 14).expanded).isFalse()
    assertThat(glassContainerShowcaseFrame(4f / 14).expanded).isTrue()
    assertThat(glassContainerShowcaseFrame(4f / 14).colorDetachFraction).isEqualTo(0f)
    assertThat(glassContainerShowcaseFrame(7f / 14).colorDetachFraction).isEqualTo(1f)
    assertThat(glassContainerShowcaseFrame(10f / 14).colorDetachFraction).isEqualTo(0f)
    assertThat(glassContainerShowcaseFrame(12f / 14).expanded).isFalse()
    assertThat(glassContainerShowcaseFrame(1f)).isEqualTo(glassContainerShowcaseFrame(0f))
  }

  @Test
  fun dragColorBy_rejoinsAfterInterruptingTheScript() {
    val state = GlassContainerShowcaseState()
    state.seek(0.5f)
    state.dragColorBy(-1f)
    assertThat(state.colorOffset).isEqualTo(0f)
    assertThat(state.isPlaying).isFalse()
    state.dragColorBy(5f)
    assertThat(state.colorOffset).isEqualTo(1f)
  }

  @Test
  fun toggleExpanded_pausesTheScriptAndRestoresTheAttachedPalette() {
    val state = GlassContainerShowcaseState()
    state.seek(0.5f)
    state.toggleExpanded()
    assertThat(state.isPlaying).isFalse()
    assertThat(state.expanded).isFalse()
    assertThat(state.colorOffset).isEqualTo(0f)
  }

  @Test
  fun toggleSlowMotion_preservesTheCurrentFrameAndPlaybackState() {
    val state = GlassContainerShowcaseState()
    state.seek(5.5f / 14)
    val offset = state.colorOffset
    state.toggleSlowMotion()
    assertThat(state.isSlowMotion).isTrue()
    assertThat(state.animationDurationScale).isEqualTo(2f)
    assertThat(state.expanded).isTrue()
    assertThat(state.colorOffset).isEqualTo(offset)
    assertThat(state.isPlaying).isTrue()
    state.pause()
    state.toggleSlowMotion()
    assertThat(state.isSlowMotion).isFalse()
    assertThat(state.animationDurationScale).isEqualTo(1f)
    assertThat(state.isPlaying).isFalse()
    assertThat(state.colorOffset).isEqualTo(offset)
  }

  @Test
  fun reset_restoresTheLoopAfterManualToolChanges() {
    val state = GlassContainerShowcaseState()
    state.selectColor(2)
    state.cycleStroke()
    state.toggleSlowMotion()
    assertThat(state.colorIndex).isEqualTo(2)
    assertThat(state.isPlaying).isFalse()
    state.reset()
    assertThat(state.colorIndex).isEqualTo(0)
    assertThat(state.strokeIndex).isEqualTo(1)
    assertThat(state.expanded).isFalse()
    assertThat(state.isPlaying).isTrue()
    assertThat(state.isSlowMotion).isFalse()
  }

  @Test
  fun glassContainerShowcaseFrame_rejectsInvalidProgressAndColor() {
    assertFailure { glassContainerShowcaseFrame(1.5f) }.isInstanceOf<IllegalArgumentException>()
    assertFailure { glassContainerShowcaseFrame(Float.NaN) }.isInstanceOf<IllegalArgumentException>()
    assertFailure { GlassContainerShowcaseState().selectColor(-1) }.isInstanceOf<IllegalArgumentException>()
  }
}
