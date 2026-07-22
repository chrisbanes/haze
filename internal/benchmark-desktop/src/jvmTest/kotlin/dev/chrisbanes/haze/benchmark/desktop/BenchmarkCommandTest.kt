// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import java.nio.file.Path
import kotlin.test.Test

class BenchmarkCommandTest {
  @Test
  fun runRequiresKnownScenarioAndOutput() {
    assertFailure {
      parseBenchmarkCommand(arrayOf("run", "--scenario", "missing"), setOf("pointer_sweep"))
    }.isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun probeDoesNotRequireScenarios() {
    assertThat(parseBenchmarkCommand(arrayOf("probe"), emptySet()))
      .isEqualTo(BenchmarkCommand.Probe)
  }

  @Test
  fun runParsesAllOptions() {
    assertThat(
      parseBenchmarkCommand(
        arrayOf(
          "run",
          "--scenario",
          "pointer_sweep",
          "--revision",
          "local",
          "--round",
          "2",
          "--order",
          "3",
          "--output",
          "result.json",
          "--smoke",
        ),
        setOf("pointer_sweep"),
      ),
    ).isEqualTo(
      BenchmarkCommand.Run(
        scenarioId = "pointer_sweep",
        revision = "local",
        round = 2,
        order = 3,
        output = Path.of("result.json"),
        smoke = true,
      ),
    )
  }

  @Test
  fun runRejectsUnknownOptions() {
    assertFailure {
      parseBenchmarkCommand(
        arrayOf(
          "run",
          "--scenario",
          "pointer_sweep",
          "--revision",
          "local",
          "--round",
          "0",
          "--order",
          "0",
          "--output",
          "result.json",
          "--unexpected",
          "value",
        ),
        setOf("pointer_sweep"),
      )
    }.isInstanceOf<IllegalArgumentException>()
  }
}
