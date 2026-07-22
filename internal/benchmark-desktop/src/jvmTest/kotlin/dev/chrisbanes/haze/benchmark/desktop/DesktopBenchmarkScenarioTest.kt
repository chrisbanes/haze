// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import androidx.compose.runtime.Composable
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

class DesktopBenchmarkScenarioTest {
  @Test
  fun validScenario_isAccepted() {
    assertThat(validateScenario(FakeScenario("pointer_sweep", 1, validEvents()))).isSameInstanceAs(Unit)
  }

  @Test
  fun identifiers_areRestricted() {
    assertFailure { validateScenario(FakeScenario("Pointer Sweep!", 1, validEvents())) }
      .isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun events_areOrderedAndNormalized() {
    assertFailure {
      validateScenario(
        FakeScenario(
          "pointer_sweep",
          1,
          listOf(
            DesktopInputEvent(10, DesktopInputEventType.Move, NormalizedPoint(0.5f, 0.5f)),
            DesktopInputEvent(5, DesktopInputEventType.Exit, null),
          ),
        ),
      )
    }.isInstanceOf<IllegalArgumentException>()
  }
}

private class FakeScenario(
  override val id: String,
  override val protocolVersion: Int,
  override val events: List<DesktopInputEvent>,
) : DesktopBenchmarkScenario {
  @Composable
  override fun Content() = Unit

  override suspend fun reset() = Unit
}

private fun validEvents() = listOf(
  DesktopInputEvent(0, DesktopInputEventType.Move, NormalizedPoint(0.5f, 0.5f)),
  DesktopInputEvent(1, DesktopInputEventType.Exit, null),
)
