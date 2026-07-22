// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop.glass

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.benchmark.desktop.DesktopInputEvent
import dev.chrisbanes.haze.benchmark.desktop.DesktopInputEventType
import dev.chrisbanes.haze.benchmark.desktop.validateScenario
import kotlin.test.Test

class IsolatedGlassScenarioTest {

  @Test
  fun pointerPath_isFourSecondsAt120Hz() {
    val events = isolatedPointerEvents()

    assertThat(events.size).isEqualTo(481)
    assertThat(events.count { it.type == DesktopInputEventType.Move }).isEqualTo(480)
    assertThat(events.last()).isEqualTo(
      DesktopInputEvent(4_000_000_000L, DesktopInputEventType.Exit, null),
    )
    events.dropLast(1).forEachIndexed { index, event ->
      assertThat(event.offsetNanos).isEqualTo(index * 1_000_000_000L / 120L)
    }
    assertThat(events[events.lastIndex - 1].offsetNanos).isEqualTo(479_000_000_000L / 120L)
  }

  @Test
  fun pointerPath_staysInsideGlassSurface() {
    val minX = (1f - isolatedGlassSurfaceWidthFraction) / 2f
    val maxX = 1f - minX
    val minY = (1f - isolatedGlassSurfaceHeightFraction) / 2f
    val maxY = 1f - minY

    isolatedPointerEvents().mapNotNull { it.position }.forEach { point ->
      assertThat(point.x).isBetween(minX, maxX)
      assertThat(point.y).isBetween(minY, maxY)
    }
  }

  @Test
  fun scenario_isSortedAndValid() {
    assertThat(validateScenario(IsolatedGlassScenario())).isSameInstanceAs(Unit)
  }
}
