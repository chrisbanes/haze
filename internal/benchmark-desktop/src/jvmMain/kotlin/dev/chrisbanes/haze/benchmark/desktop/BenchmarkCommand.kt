// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
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
  ci: String? = System.getenv("CI"),
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
      val command = BenchmarkCommand.Run(
        scenarioId = scenarioId,
        revision = options.required("--revision"),
        round = options.requiredInt("--round"),
        order = options.requiredInt("--order"),
        output = Path.of(options.required("--output")),
        smoke = options.flag("--smoke"),
      )
      require(command.revision.matches(Regex("[a-z][a-z0-9_]{0,63}"))) {
        "Invalid revision: ${command.revision}"
      }
      require(command.round in 0..2) { "Invalid round: ${command.round}" }
      require(command.order in 0..3) { "Invalid order: ${command.order}" }
      require(command.output.fileName?.toString()?.endsWith(".json") == true) {
        "Run output must be a JSON file"
      }
      require(!command.smoke || ci.isNullOrBlank()) { "--smoke is not allowed in CI" }
      command
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
): Int {
  var host: ComposeDesktopBenchmarkHost? = null
  fun host(): ComposeDesktopBenchmarkHost =
    host ?: ComposeDesktopBenchmarkHost(suiteId).also { host = it }
  return executeDesktopBenchmarkSuite(
    args = args,
    suiteId = suiteId,
    scenarioFactories = scenarioFactories,
    ci = System.getenv("CI"),
    probe = { host().probe() },
    runBlock = { command, scenarioFactory -> host().runBlock(command, scenarioFactory) },
  )
}

internal fun executeDesktopBenchmarkSuite(
  args: Array<String>,
  suiteId: String,
  scenarioFactories: List<() -> DesktopBenchmarkScenario>,
  ci: String?,
  probe: suspend () -> BenchmarkEnvironment,
  runBlock: suspend (
    BenchmarkCommand.Run,
    () -> DesktopBenchmarkScenario,
  ) -> BenchmarkBlockResult,
  aggregateBlocks: (
    suiteId: String,
    allowedScenarioIds: Set<String>,
    repository: String,
    baseSha: String?,
    headSha: String,
    blocks: List<BenchmarkBlockResult>,
  ) -> BenchmarkArtifact = ::aggregateBenchmarkBlocks,
): Int {
  val scenarios: List<DesktopBenchmarkScenario>
  val command: BenchmarkCommand
  try {
    require(suiteId.matches(Regex("[a-z][a-z0-9_]{0,63}"))) { "Invalid suite id: $suiteId" }
    scenarios = scenarioFactories.map { factory -> factory().also(::validateScenario) }
    require(scenarios.map { it.id }.distinct().size == scenarios.size) {
      "Scenario ids must be unique"
    }
    command = parseBenchmarkCommand(
      args,
      scenarios.mapTo(mutableSetOf()) { it.id },
      ci,
    )
  } catch (failure: IllegalArgumentException) {
    reportFailure(failure)
    return INVALID_INPUT_EXIT_CODE
  } catch (failure: Exception) {
    reportFailure(failure)
    return RUNTIME_FAILURE_EXIT_CODE
  }

  return when (command) {
    BenchmarkCommand.Probe -> executeRuntimeCommand {
      println(BenchmarkJson.encodeToString(runBlocking { probe() }))
    }

    is BenchmarkCommand.Run -> executeRuntimeCommand {
      val scenario = scenarios.single { it.id == command.scenarioId }
      val result = runBlocking { runBlock(command) { scenario } }
      writeText(command.output, BenchmarkJson.encodeToString(result))
    }

    is BenchmarkCommand.Aggregate -> executeAggregateCommand(
      command = command,
      suiteId = suiteId,
      allowedScenarioIds = scenarios.mapTo(mutableSetOf()) { it.id },
      aggregateBlocks = aggregateBlocks,
    )
  }
}

private fun executeRuntimeCommand(block: () -> Unit): Int = try {
  block()
  SUCCESS_EXIT_CODE
} catch (failure: Exception) {
  reportFailure(failure)
  RUNTIME_FAILURE_EXIT_CODE
}

private fun executeAggregateCommand(
  command: BenchmarkCommand.Aggregate,
  suiteId: String,
  allowedScenarioIds: Set<String>,
  aggregateBlocks: (
    suiteId: String,
    allowedScenarioIds: Set<String>,
    repository: String,
    baseSha: String?,
    headSha: String,
    blocks: List<BenchmarkBlockResult>,
  ) -> BenchmarkArtifact,
): Int {
  try {
    validateAggregateIdentity(
      suiteId = suiteId,
      allowedScenarioIds = allowedScenarioIds,
      repository = command.repository,
      baseSha = command.baseSha,
      headSha = command.headSha,
    )
    require(command.output.fileName?.toString()?.endsWith(".json") == true) {
      "Aggregate output must be a JSON file"
    }
  } catch (failure: IllegalArgumentException) {
    reportFailure(failure)
    return INVALID_INPUT_EXIT_CODE
  }

  return try {
    val artifact = aggregateBlocks(
      suiteId,
      allowedScenarioIds,
      command.repository,
      command.baseSha,
      command.headSha,
      readBenchmarkBlocks(command.input),
    )
    writeText(command.output, encodeArtifact(artifact))
    SUCCESS_EXIT_CODE
  } catch (failure: IllegalArgumentException) {
    writeFailureArtifact(command, suiteId, failure, INVALID_INPUT_EXIT_CODE)
  } catch (failure: Exception) {
    writeFailureArtifact(command, suiteId, failure, RUNTIME_FAILURE_EXIT_CODE)
  }
}

private fun writeFailureArtifact(
  command: BenchmarkCommand.Aggregate,
  suiteId: String,
  failure: Exception,
  exitCode: Int,
): Int {
  reportFailure(failure)
  return try {
    writeText(
      command.output,
      encodeArtifact(
        BenchmarkArtifact(
          suiteId = suiteId,
          repository = command.repository,
          baseSha = command.baseSha,
          headSha = command.headSha,
          scenarios = emptyList(),
          status = "failed",
          diagnostic = boundedDiagnostic(failure.message ?: failure::class.java.name),
        ),
      ),
    )
    exitCode
  } catch (writeFailure: Exception) {
    reportFailure(writeFailure)
    RUNTIME_FAILURE_EXIT_CODE
  }
}

internal fun writeText(
  path: Path,
  value: String,
  atomicMove: (Path, Path) -> Unit = ::atomicMove,
) {
  val destination = path.toAbsolutePath()
  val parent = requireNotNull(destination.parent) { "Output path has no parent: $path" }
  Files.createDirectories(parent)
  val temporary = Files.createTempFile(parent, ".${destination.fileName}.", ".tmp")
  try {
    Files.writeString(temporary, value)
    try {
      atomicMove(temporary, destination)
    } catch (failure: AtomicMoveNotSupportedException) {
      throw IOException("Atomic replacement is required for benchmark output: $destination", failure)
    }
  } finally {
    Files.deleteIfExists(temporary)
  }
}

private fun atomicMove(source: Path, destination: Path) {
  Files.move(source, destination, ATOMIC_MOVE, REPLACE_EXISTING)
}

private fun reportFailure(failure: Exception) {
  System.err.println(failure.message ?: failure::class.java.name)
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

private const val SUCCESS_EXIT_CODE = 0
private const val RUNTIME_FAILURE_EXIT_CODE = 1
private const val INVALID_INPUT_EXIT_CODE = 2
