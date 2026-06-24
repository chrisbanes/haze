// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isEqualTo

internal class HazeInvalidationAssertionScope internal constructor(
  private val tag: String,
) {
  fun drawInvalidationsExactly(count: Int) {
    assertInvalidationsExactly(HazeInvalidationType.Draw, count)
  }

  fun drawInvalidationsAtMost(count: Int) {
    assertInvalidationsAtMost(HazeInvalidationType.Draw, count)
  }

  private fun assertInvalidationsExactly(
    type: HazeInvalidationType,
    count: Int,
  ) {
    val matchingEvents = hazeInvalidationEvents().filter {
      it.tag == tag && it.invalidationType == type
    }
    assertThat(
      matchingEvents.size,
      "Haze $type invalidations for tag '$tag'. All events: ${hazeInvalidationEvents()}",
    ).isEqualTo(count)
  }

  private fun assertInvalidationsAtMost(
    type: HazeInvalidationType,
    count: Int,
  ) {
    val allEvents = hazeInvalidationEvents()
    val matchingEvents = allEvents.filter {
      it.tag == tag && it.invalidationType == type
    }
    assertThat(
      matchingEvents.size <= count,
      "Haze $type invalidations for tag '$tag' expected at most $count. " +
        "Actual=${matchingEvents.size}. All events: $allEvents",
    ).isEqualTo(true)
  }
}

internal fun assertHazeInvalidations(
  tag: String,
  block: HazeInvalidationAssertionScope.() -> Unit,
) {
  HazeInvalidationAssertionScope(tag).block()
}
