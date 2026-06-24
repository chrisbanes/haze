// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isEqualTo

internal class HazeInvalidationAssertionScope internal constructor(
  private val tag: String,
) {
  fun drawInvalidationsExactly(count: Int) {
    assertThat(eventsOfType(HazeInvalidationType.Draw).size).isEqualTo(count)
  }

  fun layoutInvalidationsExactly(count: Int) {
    assertThat(eventsOfType(HazeInvalidationType.Layout).size).isEqualTo(count)
  }

  private fun eventsOfType(type: HazeInvalidationType): List<HazeInvalidationEvent> {
    return hazeInvalidationEvents().filter { event ->
      event.tag == tag && event.invalidationType == type
    }
  }
}

internal fun assertHazeInvalidations(
  tag: String,
  block: HazeInvalidationAssertionScope.() -> Unit,
) {
  HazeInvalidationAssertionScope(tag).block()
}
