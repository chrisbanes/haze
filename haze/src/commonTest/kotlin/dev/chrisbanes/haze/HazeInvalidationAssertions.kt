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

  fun layoutInvalidationsExactly(count: Int) {
    assertInvalidationsExactly(HazeInvalidationType.Layout, count)
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
}

internal fun assertHazeInvalidations(
  tag: String,
  block: HazeInvalidationAssertionScope.() -> Unit,
) {
  HazeInvalidationAssertionScope(tag).block()
}
