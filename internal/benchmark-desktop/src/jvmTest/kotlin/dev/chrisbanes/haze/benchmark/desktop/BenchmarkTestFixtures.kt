// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

internal fun benchmarkBlockFixture() = BenchmarkBlockResult(
  suiteId = "glass",
  scenarioId = "pointer_sweep",
  protocolVersion = 1,
  revision = "head",
  round = 0,
  order = 1,
  environment = benchmarkEnvironmentFixture(),
  workloadDurationNanos = 4_000_000_000,
  samples = listOf(FrameSample(10_000_000, null), FrameSample(11_000_000, 16_000_000)),
)

internal fun benchmarkEnvironmentFixture() = BenchmarkEnvironment(
  osName = "Mac OS X",
  osVersion = "26.0",
  architecture = "aarch64",
  cpu = "Apple M1",
  memoryBytes = 7_000_000_000,
  javaVendor = "Azul Systems, Inc.",
  javaVersion = "21",
  composeVersion = "1.11.1",
  skikoVersion = "0.144.6",
  renderApi = "METAL",
  framebufferWidth = 1280,
  framebufferHeight = 720,
  contentScale = 2f,
  refreshRateHz = 60,
  runnerImage = "macos-26",
  runnerImageVersion = "test",
)

internal fun artifactFixture(diagnostic: String) = BenchmarkArtifact(
  suiteId = "glass",
  repository = "chrisbanes/haze",
  baseSha = null,
  headSha = "b".repeat(40),
  scenarios = emptyList(),
  diagnostic = diagnostic,
)
