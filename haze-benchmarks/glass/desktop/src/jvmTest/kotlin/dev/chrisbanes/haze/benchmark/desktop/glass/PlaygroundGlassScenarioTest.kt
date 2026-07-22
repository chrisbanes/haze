// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop.glass

import androidx.compose.ui.geometry.Offset
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.benchmark.desktop.DesktopInputEvent
import dev.chrisbanes.haze.benchmark.desktop.DesktopInputEventType
import dev.chrisbanes.haze.benchmark.desktop.NormalizedPoint
import dev.chrisbanes.haze.benchmark.desktop.validateScenario
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class PlaygroundGlassScenarioTest {

  @Test
  fun path_isExactSixSecondPressDragReleaseProtocol() {
    val events = playgroundEvents()
    val start = NormalizedPoint(0.5f, 0.52f)

    assertThat(events.size).isEqualTo(304)
    assertThat(events.first()).isEqualTo(
      DesktopInputEvent(0, DesktopInputEventType.Move, start),
    )
    assertThat(events[1]).isEqualTo(
      DesktopInputEvent(500_000_000L, DesktopInputEventType.Press, start),
    )

    val drags = events.drop(2).take(300)
    drags.forEachIndexed { index, event ->
      val progress = index / 299f
      assertThat(event).isEqualTo(
        DesktopInputEvent(
          offsetNanos = 500_000_000L + index * 5_000_000_000L / 299L,
          type = DesktopInputEventType.Drag,
          position = NormalizedPoint(
            x = 0.5f + 0.20f * progress,
            y = 0.52f + 0.10f * sin((progress * PI.toFloat()).toDouble()).toFloat(),
          ),
        ),
      )
    }

    assertThat(events.take(2).map { it.type }).isEqualTo(
      listOf(DesktopInputEventType.Move, DesktopInputEventType.Press),
    )
    assertThat(drags.map { it.type }.distinct()).isEqualTo(listOf(DesktopInputEventType.Drag))
    assertThat(events.takeLast(2)).isEqualTo(
      listOf(
        DesktopInputEvent(
          5_500_000_000L,
          DesktopInputEventType.Release,
          NormalizedPoint(0.7f, 0.52f),
        ),
        DesktopInputEvent(6_000_000_000L, DesktopInputEventType.Exit, null),
      ),
    )
  }

  @Test
  fun scenario_isValidAndResetClearsOnlyScenarioDragState() = runBlocking {
    val scenario = PlaygroundGlassScenario()

    assertThat(scenario.id).isEqualTo("playground_drag")
    assertThat(scenario.protocolVersion).isEqualTo(1)
    assertThat(validateScenario(scenario)).isSameInstanceAs(Unit)
    scenario.applyDragForTest(Offset(80f, 40f))
    assertThat(scenario.dragOffsetForTest()).isEqualTo(Offset(80f, 40f))

    scenario.reset()

    assertThat(scenario.dragOffsetForTest()).isEqualTo(Offset.Zero)
  }
}
