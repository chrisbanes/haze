// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.containsExactly
import kotlin.test.Test

class HazePreDrawListenerTest {

  @Test
  fun snapshotApply_notifiesOnlyCrossWindowListener() {
    val area = HazeArea().apply { windowId = SOURCE_WINDOW }
    val sameWindowInvalidations = mutableListOf<Boolean>()
    val crossWindowInvalidations = mutableListOf<Boolean>()
    area.preDrawListeners += listener(SOURCE_WINDOW, sameWindowInvalidations)
    area.preDrawListeners += listener(EFFECT_WINDOW, crossWindowInvalidations)

    area.notifyPreDrawListeners(regularPreDraw = false, snapshotApplied = true)

    assertThat(sameWindowInvalidations).containsExactly()
    assertThat(crossWindowInvalidations).containsExactly(true)
  }

  @Test
  fun regularPreDraw_notifiesEveryListenerWithoutInvalidatingCapture() {
    val area = HazeArea().apply { windowId = SOURCE_WINDOW }
    val sameWindowInvalidations = mutableListOf<Boolean>()
    val crossWindowInvalidations = mutableListOf<Boolean>()
    area.preDrawListeners += listener(SOURCE_WINDOW, sameWindowInvalidations)
    area.preDrawListeners += listener(EFFECT_WINDOW, crossWindowInvalidations)

    area.notifyPreDrawListeners(regularPreDraw = true, snapshotApplied = false)

    assertThat(sameWindowInvalidations).containsExactly(false)
    assertThat(crossWindowInvalidations).containsExactly(false)
  }

  @Test
  fun combinedPreDraw_invalidatesOnlyCrossWindowCapture() {
    val area = HazeArea().apply { windowId = SOURCE_WINDOW }
    val sameWindowInvalidations = mutableListOf<Boolean>()
    val crossWindowInvalidations = mutableListOf<Boolean>()
    area.preDrawListeners += listener(SOURCE_WINDOW, sameWindowInvalidations)
    area.preDrawListeners += listener(EFFECT_WINDOW, crossWindowInvalidations)

    area.notifyPreDrawListeners(regularPreDraw = true, snapshotApplied = true)

    assertThat(sameWindowInvalidations).containsExactly(false)
    assertThat(crossWindowInvalidations).containsExactly(true)
  }

  private fun listener(
    windowId: Any,
    invalidations: MutableList<Boolean>,
  ): OnPreDrawListener = OnPreDrawListener(
    effectWindowId = { windowId },
    onPreDraw = invalidations::add,
  )

  private companion object {
    val SOURCE_WINDOW = Any()
    val EFFECT_WINDOW = Any()
  }
}
