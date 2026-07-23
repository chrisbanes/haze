// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString

internal data class BenchmarkOptions(
  val commitSha: String,
  val output: String,
)

internal fun parseBenchmarkOptions(args: Array<String>): BenchmarkOptions {
  require(args.size == 4) {
    "Usage: --commit-sha SHA --output FILE"
  }
  require(args[0] == "--commit-sha" && args[2] == "--output") {
    "Usage: --commit-sha SHA --output FILE"
  }
  return BenchmarkOptions(args[1], args[3]).also {
    require(it.commitSha.matches(Regex("[0-9a-f]{40}"))) { "Invalid commit SHA" }
    require(it.output.endsWith(".json")) { "Output must be a JSON file" }
  }
}

public fun runDesktopBenchmarkSuite(
  args: Array<String>,
  suiteId: String,
  scenarioFactories: List<() -> DesktopBenchmarkScenario>,
): Int = try {
  val options = parseBenchmarkOptions(args)
  val host = ComposeDesktopBenchmarkHost()
  val scenarios = runBlocking {
    scenarioFactories.map { host.runScenario(it) }
  }
  require(scenarios.map { it.id }.distinct().size == scenarios.size) {
    "Scenario ids must be unique"
  }
  val output = Path.of(options.output).toAbsolutePath()
  output.parent?.let(Files::createDirectories)
  Files.writeString(
    output,
    BenchmarkJson.encodeToString(
      BenchmarkReport(
        suiteId = suiteId,
        commitSha = options.commitSha,
        scenarios = scenarios,
      ),
    ),
  )
  0
} catch (failure: Exception) {
  System.err.println(failure.message ?: failure::class.java.name)
  1
}
