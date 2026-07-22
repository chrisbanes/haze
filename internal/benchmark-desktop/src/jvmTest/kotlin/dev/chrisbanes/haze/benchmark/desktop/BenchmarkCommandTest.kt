// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.serialization.encodeToString

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

  @Test
  fun runRejectsInvalidRevisionRoundOrderAndOutput() {
    val valid = arrayOf(
      "run",
      "--scenario", "pointer_sweep",
      "--revision", "head",
      "--round", "0",
      "--order", "1",
      "--output", "result.json",
    )
    listOf(
      valid.copyOf().also { it[4] = "../head" },
      valid.copyOf().also { it[6] = "3" },
      valid.copyOf().also { it[8] = "4" },
      valid.copyOf().also { it[10] = "result.txt" },
    ).forEach { arguments ->
      assertFailure {
        parseBenchmarkCommand(arguments, setOf("pointer_sweep"), ci = null)
      }.isInstanceOf<IllegalArgumentException>()
    }
  }

  @Test
  fun invalidArgumentsReturnExitTwo() {
    assertThat(
      runDesktopBenchmarkSuite(emptyArray(), "glass", listOf(::CommandFakeScenario)),
    ).isEqualTo(2)
  }

  @Test
  fun smokeIsRejectedWhenCiIsNonBlank() {
    assertFailure {
      parseBenchmarkCommand(
        arrayOf(
          "run",
          "--scenario", "pointer_sweep",
          "--revision", "head",
          "--round", "0",
          "--order", "1",
          "--output", "result.json",
          "--smoke",
        ),
        setOf("pointer_sweep"),
        ci = "true",
      )
    }.isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun smokeOnlySkipsPostWarmupDelay() = withCommandTempDirectory { directory ->
    assertThat(postWarmupDelayMillis(smoke = false)).isEqualTo(500L)
    assertThat(postWarmupDelayMillis(smoke = true)).isEqualTo(0L)

    val expectedEvents = CommandFakeScenario().events
    var replayedEvents: List<DesktopInputEvent>? = null
    val exitCode = executeDesktopBenchmarkSuite(
      args = arrayOf(
        "run",
        "--scenario", "pointer_sweep",
        "--revision", "head",
        "--round", "0",
        "--order", "1",
        "--output", directory.resolve("smoke.json").toString(),
        "--smoke",
      ),
      suiteId = "glass",
      scenarioFactories = listOf(::CommandFakeScenario),
      ci = null,
      probe = { benchmarkEnvironmentFixture() },
      runBlock = { _, scenarioFactory ->
        replayedEvents = scenarioFactory().events
        benchmarkBlockFixture()
      },
    )

    assertThat(exitCode).isEqualTo(0)
    assertThat(replayedEvents).isEqualTo(expectedEvents)
  }

  @Test
  fun aggregateWritesCompleteArtifactWithoutOpeningHost() = withCommandTempDirectory { directory ->
    val input = Files.createDirectories(directory.resolve("raw"))
    writeHeadOnlyBlocks(input)
    val output = directory.resolve("benchmark.json")

    val exitCode = runDesktopBenchmarkSuite(
      aggregateArgs(input, output),
      suiteId = "glass",
      scenarioFactories = listOf(::CommandFakeScenario),
    )

    assertThat(exitCode).isEqualTo(0)
    val artifact = BenchmarkJson.decodeFromString<BenchmarkArtifact>(Files.readString(output))
    assertThat(artifact.status).isEqualTo("complete")
    assertThat(artifact.scenarios.single().id).isEqualTo("pointer_sweep")
  }

  @Test
  fun invalidBlockWritesFailedArtifactAndReturnsExitTwo() = withCommandTempDirectory { directory ->
    val input = Files.createDirectories(directory.resolve("raw"))
    Files.writeString(input.resolve("invalid.json"), "{")
    val output = directory.resolve("benchmark.json")

    val exitCode = runDesktopBenchmarkSuite(
      aggregateArgs(input, output),
      suiteId = "glass",
      scenarioFactories = listOf(::CommandFakeScenario),
    )

    assertThat(exitCode).isEqualTo(2)
    val artifact = BenchmarkJson.decodeFromString<BenchmarkArtifact>(Files.readString(output))
    assertThat(artifact.status).isEqualTo("failed")
    assertThat(artifact.scenarios).isEqualTo(emptyList())
    assertThat(artifact.diagnostic).isNotNull()
  }

  @Test
  fun symlinkBlockWritesFailedArtifactAndReturnsExitTwoWhenSupported() =
    withCommandTempDirectory { directory ->
      val input = Files.createDirectories(directory.resolve("raw"))
      val target = input.resolve("target.txt")
      Files.writeString(target, BenchmarkJson.encodeToString(benchmarkBlockFixture()))
      if (runCatching { Files.createSymbolicLink(input.resolve("block.json"), target.fileName) }.isFailure) {
        return@withCommandTempDirectory
      }
      val output = directory.resolve("benchmark.json")

      val exitCode = runDesktopBenchmarkSuite(
        aggregateArgs(input, output),
        suiteId = "glass",
        scenarioFactories = listOf(::CommandFakeScenario),
      )

      assertThat(exitCode).isEqualTo(2)
      val artifact = BenchmarkJson.decodeFromString<BenchmarkArtifact>(Files.readString(output))
      assertThat(artifact.status).isEqualTo("failed")
    }

  @Test
  fun unexpectedAggregationFailureWritesBoundedFailedArtifactAndReturnsExitOne() =
    withCommandTempDirectory { directory ->
      val input = Files.createDirectories(directory.resolve("raw"))
      writeHeadOnlyBlocks(input)
      val output = directory.resolve("benchmark.json")
      val diagnostic = "unexpected-é".repeat(500)

      val exitCode = executeDesktopBenchmarkSuite(
        args = aggregateArgs(input, output),
        suiteId = "glass",
        scenarioFactories = listOf(::CommandFakeScenario),
        ci = null,
        probe = { benchmarkEnvironmentFixture() },
        runBlock = { _, _ -> error("run should not execute") },
        aggregateBlocks = { _, _, _, _, _, _ -> error(diagnostic) },
      )

      assertThat(exitCode).isEqualTo(1)
      val artifact = BenchmarkJson.decodeFromString<BenchmarkArtifact>(Files.readString(output))
      assertThat(artifact.status).isEqualTo("failed")
      assertThat(checkNotNull(artifact.diagnostic).encodeToByteArray().size <= 2048).isEqualTo(true)
    }

  @Test
  fun runFailureReturnsExitOneWithoutWritingFabricatedArtifact() =
    withCommandTempDirectory { directory ->
      val output = directory.resolve("run.json")
      val exitCode = executeDesktopBenchmarkSuite(
        args = arrayOf(
          "run",
          "--scenario", "pointer_sweep",
          "--revision", "head",
          "--round", "0",
          "--order", "1",
          "--output", output.toString(),
        ),
        suiteId = "glass",
        scenarioFactories = listOf(::CommandFakeScenario),
        ci = null,
        probe = { benchmarkEnvironmentFixture() },
        runBlock = { _, _ -> error("measurement failed") },
      )

      assertThat(exitCode).isEqualTo(1)
      assertThat(Files.exists(output)).isEqualTo(false)
    }

  @Test
  fun writeTextCompletelyReplacesExistingDestination() = withCommandTempDirectory { directory ->
    val output = directory.resolve("result.json")
    Files.writeString(output, "old trailing content")

    writeText(output, "new")

    assertThat(Files.readString(output)).isEqualTo("new")
  }

  @Test
  fun writeTextRejectsNonAtomicMoveAndPreservesExistingDestination() =
    withCommandTempDirectory { directory ->
      val output = directory.resolve("result.json")
      Files.writeString(output, "old")

      val failure = assertFailsWith<IOException> {
        writeText(output, "new") { source, destination ->
          throw AtomicMoveNotSupportedException(
            source.toString(),
            destination.toString(),
            "not supported",
          )
        }
      }

      assertThat(failure.cause is AtomicMoveNotSupportedException).isTrue()
      assertThat(Files.readString(output)).isEqualTo("old")
      assertThat(Files.list(directory).use { it.count() }).isEqualTo(1L)
    }
}

private class CommandFakeScenario : DesktopBenchmarkScenario {
  override val id = "pointer_sweep"
  override val protocolVersion = 1
  override val events = listOf(
    DesktopInputEvent(0, DesktopInputEventType.Move, NormalizedPoint(0.5f, 0.5f)),
  )

  @androidx.compose.runtime.Composable
  override fun Content() = Unit

  override suspend fun reset() = Unit
}

private fun aggregateArgs(input: Path, output: Path) = arrayOf(
  "aggregate",
  "--input", input.toString(),
  "--repository", "chrisbanes/haze",
  "--base-sha", "a".repeat(40),
  "--head-sha", "b".repeat(40),
  "--output", output.toString(),
)

private fun writeHeadOnlyBlocks(input: Path) {
  repeat(3) { round ->
    listOf(1, 2).forEach { order ->
      val block = benchmarkBlockFixture().copy(round = round, order = order)
      Files.writeString(
        input.resolve("$round-$order.json"),
        BenchmarkJson.encodeToString(block),
      )
    }
  }
}

private inline fun withCommandTempDirectory(block: (Path) -> Unit) {
  val directory = Files.createTempDirectory("haze-benchmark-command")
  try {
    block(directory)
  } finally {
    directory.toFile().deleteRecursively()
  }
}
