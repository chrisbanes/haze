// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop.glass

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.benchmark.desktop.DesktopInputEvent
import dev.chrisbanes.haze.benchmark.desktop.DesktopInputEventType
import kotlin.test.Test

class IsolatedGlassScenarioTest {

  @Test
  fun pointerPath_isFourSecondsAt120Hz() {
    val events = isolatedPointerEvents()

    assertThat(events.count { it.type == DesktopInputEventType.Move }).isEqualTo(480)
    assertThat(events.last()).isEqualTo(
      DesktopInputEvent(4_000_000_000L, DesktopInputEventType.Exit, null),
    )
  }

  @Test
  fun pointerPath_staysInsideGlassSurface() {
    isolatedPointerEvents().mapNotNull { it.position }.forEach { point ->
      assertThat(point.x).isBetween(0.25f, 0.75f)
      assertThat(point.y).isBetween(0.30f, 0.70f)
    }
  }
}
