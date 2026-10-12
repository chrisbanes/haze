// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class HazePreDrawListenerAndroidTest {
  @Test
  @Config(sdk = [31])
  fun snapshotApply_invalidatesSameWindowCapture_onApi31() {
    val (same, cross) = notifySnapshotApply()
    assertThat(same).containsExactly(true)
    assertThat(cross).containsExactly(true)
  }

  @Test
  @Config(sdk = [30, 32, 36])
  fun snapshotApply_skipsSameWindowListener_offApi31() {
    val (same, cross) = notifySnapshotApply()
    assertThat(same).isEmpty()
    assertThat(cross).containsExactly(true)
  }

  private fun notifySnapshotApply(): Pair<List<Boolean>, List<Boolean>> {
    val sourceWindow = Any()
    val area = HazeArea().apply { windowId = sourceWindow }
    val same = mutableListOf<Boolean>()
    val cross = mutableListOf<Boolean>()
    area.preDrawListeners += OnPreDrawListener(effectWindowId = { sourceWindow }, onPreDraw = same::add)
    area.preDrawListeners += OnPreDrawListener(effectWindowId = { Any() }, onPreDraw = cross::add)
    area.notifyPreDrawListeners(regularPreDraw = false, snapshotApplied = true)
    return same to cross
  }
}
