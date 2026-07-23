// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.serialization.encodeToString

class BenchmarkReportTest {
  @Test
  fun optionsUseOneCommitAndOneOutput() {
    assertThat(
      parseBenchmarkOptions(
        arrayOf(
          "--commit-sha",
          "a".repeat(40),
          "--output",
          "build/benchmark.json",
        ),
      ),
    ).isEqualTo(
      BenchmarkOptions(
        commitSha = "a".repeat(40),
        output = "build/benchmark.json",
      ),
    )
  }

  @Test
  fun metricSummaryContainsOnlyUsefulValues() {
    assertThat(summarizeMetric(listOf(5L, 1L, 3L, 2L, 4L))).isEqualTo(
      MetricSummary(
        sampleCount = 5,
        p50Nanos = 3,
        p95Nanos = 5,
        maxNanos = 5,
      ),
    )
  }

  @Test
  fun reportRoundTrips() {
    val report = BenchmarkReport(
      suiteId = "glass",
      commitSha = "a".repeat(40),
      scenarios = listOf(
        BenchmarkScenarioResult(
          id = "pointer_sweep",
          environment = environment(),
          renderDuration = MetricSummary(3, 2, 3, 3),
          callbackInterval = MetricSummary(2, 4, 5, 5),
        ),
      ),
    )

    assertThat(
      BenchmarkJson.decodeFromString<BenchmarkReport>(BenchmarkJson.encodeToString(report)),
    ).isEqualTo(report)
  }
}

private fun environment() = BenchmarkEnvironment(
  osName = "Mac OS X",
  osVersion = "26",
  architecture = "aarch64",
  cpu = "Apple",
  memoryBytes = 1,
  javaVendor = "Azul",
  javaVersion = "21",
  composeVersion = "1",
  skikoVersion = "1",
  renderApi = "METAL",
  framebufferWidth = 1280,
  framebufferHeight = 720,
  contentScale = 2f,
  refreshRateHz = 120,
  runnerImage = null,
  runnerImageVersion = null,
)
