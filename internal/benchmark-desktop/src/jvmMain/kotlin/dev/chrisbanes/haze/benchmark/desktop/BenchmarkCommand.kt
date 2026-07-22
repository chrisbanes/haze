// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString

internal sealed interface BenchmarkCommand {
  data object Probe : BenchmarkCommand

  data class Run(
    val scenarioId: String,
    val revision: String,
    val round: Int,
    val order: Int,
    val output: Path,
    val smoke: Boolean,
  ) : BenchmarkCommand

  data class Aggregate(
    val input: Path,
    val repository: String,
    val baseSha: String?,
    val headSha: String,
    val output: Path,
  ) : BenchmarkCommand
}

internal fun parseBenchmarkCommand(
  args: Array<String>,
  scenarioIds: Set<String>,
): BenchmarkCommand {
  require(args.isNotEmpty()) { "Expected a command: probe, run, or aggregate" }
  return when (args.first()) {
    "probe" -> {
      require(args.size == 1) { "probe does not accept options" }
      BenchmarkCommand.Probe
    }

    "run" -> {
      val options = parseOptions(
        args.drop(1),
        valueOptions = setOf("--scenario", "--revision", "--round", "--order", "--output"),
        booleanOptions = setOf("--smoke"),
      )
      val scenarioId = options.required("--scenario")
      require(scenarioId in scenarioIds) { "Unknown scenario: $scenarioId" }
      BenchmarkCommand.Run(
        scenarioId = scenarioId,
        revision = options.required("--revision"),
        round = options.requiredInt("--round"),
        order = options.requiredInt("--order"),
        output = Path.of(options.required("--output")),
        smoke = options.flag("--smoke"),
      )
    }

    "aggregate" -> {
      val options = parseOptions(
        args.drop(1),
        valueOptions = setOf("--input", "--repository", "--base-sha", "--head-sha", "--output"),
      )
      BenchmarkCommand.Aggregate(
        input = Path.of(options.required("--input")),
        repository = options.required("--repository"),
        baseSha = options.optional("--base-sha"),
        headSha = options.required("--head-sha"),
        output = Path.of(options.required("--output")),
      )
    }

    else -> throw IllegalArgumentException("Unknown command: ${args.first()}")
  }
}

public fun runDesktopBenchmarkSuite(
  args: Array<String>,
  suiteId: String,
  scenarioFactories: List<() -> DesktopBenchmarkScenario>,
): Int = try {
  require(suiteId.matches(Regex("[a-z][a-z0-9_]{0,63}"))) { "Invalid suite id: $suiteId" }
  val scenarios = scenarioFactories.map { factory -> factory().also(::validateScenario) }
  require(scenarios.map { it.id }.distinct().size == scenarios.size) {
    "Scenario ids must be unique"
  }
  val command = parseBenchmarkCommand(args, scenarios.mapTo(mutableSetOf()) { it.id })
  val host = ComposeDesktopBenchmarkHost(suiteId)
  runBlocking {
    when (command) {
      BenchmarkCommand.Probe -> println(BenchmarkJson.encodeToString(host.probe()))
      is BenchmarkCommand.Run -> {
        val scenario = scenarios.single { it.id == command.scenarioId }
        val result = host.runBlock(command) { scenario }
        command.output.toAbsolutePath().parent?.let(Files::createDirectories)
        Files.writeString(command.output, BenchmarkJson.encodeToString(result))
      }
      is BenchmarkCommand.Aggregate -> error("aggregate is not available yet")
    }
  }
  0
} catch (failure: Exception) {
  System.err.println(failure.message ?: failure::class.java.name)
  1
}

private class ParsedOptions(
  private val values: Map<String, String?>,
) {
  fun required(name: String): String =
    requireNotNull(values[name]) { "Missing required option: $name" }

  fun optional(name: String): String? = values[name]

  fun flag(name: String): Boolean = name in values

  fun requiredInt(name: String): Int =
    required(name).toIntOrNull() ?: throw IllegalArgumentException("$name must be an integer")
}

private fun parseOptions(
  args: List<String>,
  valueOptions: Set<String>,
  booleanOptions: Set<String> = emptySet(),
): ParsedOptions {
  val values = mutableMapOf<String, String?>()
  var index = 0
  while (index < args.size) {
    val name = args[index]
    require(name in valueOptions || name in booleanOptions) { "Unknown option: $name" }
    require(name !in values) { "Duplicate option: $name" }
    if (name in booleanOptions) {
      values[name] = null
      index++
    } else {
      require(index + 1 < args.size && !args[index + 1].startsWith("--")) {
        "Missing value for option: $name"
      }
      values[name] = args[index + 1]
      index += 2
    }
  }
  return ParsedOptions(values)
}
