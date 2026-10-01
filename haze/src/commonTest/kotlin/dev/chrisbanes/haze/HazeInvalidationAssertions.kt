// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo

internal class HazeInvalidationAssertionScope internal constructor(
  private val tag: String,
) {
  fun drawInvalidationsExactly(count: Int) {
    val matchingCount = hazeInvalidationEvents().count { it.tag == tag }
    assertThat(
      matchingCount,
      "Haze Draw invalidations for tag '$tag'. All events: ${hazeInvalidationEvents()}",
    ).isEqualTo(count)
  }

  fun drawInvalidationsAtMost(count: Int) {
    val allEvents = hazeInvalidationEvents()
    val matchingCount = allEvents.count { it.tag == tag }
    assertThat(
      matchingCount,
      "Haze Draw invalidations for tag '$tag' expected at most $count. " +
        "Actual=$matchingCount. All events: $allEvents",
    ).isLessThanOrEqualTo(count)
  }
}

internal fun assertHazeInvalidations(
  tag: String,
  block: HazeInvalidationAssertionScope.() -> Unit,
) {
  HazeInvalidationAssertionScope(tag).block()
}
